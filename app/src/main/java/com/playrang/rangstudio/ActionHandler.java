package com.playrang.rangstudio;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.provider.CalendarContract;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs phone commands (parsed by {@link CommandParser} or requested by the AI with an action tag)
 * using standard Android intents. Contacts are read on the device only to find the number the user asked for.
 */
final class ActionHandler {

    interface Host {
        void botSay(String text);
        /** Shows tappable options under a question; a tap calls {@link ActionHandler#choose(int, int)} with this generation. */
        void showChoices(String question, List<String> labels, int generation);
    }

    static final int REQ_CONTACTS = 11, REQ_CALL = 12;

    private final Activity act;
    private final Host host;
    private final ExecutorService exec;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    ActionHandler(Activity act, Host host, ExecutorService exec) {
        this.act = act;
        this.host = host;
        this.exec = exec;
        this.prefs = act.getSharedPreferences("mitra", Context.MODE_PRIVATE);
        final Context app = act.getApplicationContext();
        bg(() -> launcherApps(app)); // warm the app list so "X खोलो" is instant
    }

    /** Runs work off the main thread; silently ignored once the screen is closing. */
    private boolean bg(Runnable r) {
        try { exec.execute(r); return true; } catch (RejectedExecutionException e) { return false; }
    }

    private boolean disposed;

    /** Called from the host's onDestroy: stops delayed work; queued steps stay for the next Mitra screen. */
    void dispose() {
        disposed = true;
        main.removeCallbacksAndMessages(null);
        permYes = null; permNo = null;
    }

    // ---------------------------------------------------------------- small conversation memory (this session only)
    private ContactsHelper.Contact lastContact;   // last person we called / messaged / looked up
    private String lastNumber;                    // last number dialled directly

    private static final class Option {
        final String label; final ContactsHelper.Contact contact; final Runnable action;
        Option(String label, ContactsHelper.Contact contact, Runnable action) { this.label = label; this.contact = contact; this.action = action; }
    }
    private List<Option> options;        // pending "किसको कॉल करूँ?" choices
    private String optKind, optText;     // what to do with the chosen contact
    private int optGen;
    private String awaitName, awaitText; // "किसको कॉल करूँ? नाम बताइए" → next message is the name

    void reset() { lastContact = null; lastNumber = null; options = null; awaitName = null; }

    static boolean has(String t, String... w) { return CommandParser.has(t, w); }
    static String norm(String s) { return CommandParser.norm(s); }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    // ---------------------------------------------------------------- entry points
    /** @return true when the command was understood and handled on the device */
    boolean tryLocal(String raw) {
        List<CommandParser.Cmd> cs = CommandParser.parseAll(raw);
        if (cs.isEmpty()) return false;
        if (cs.size() == 1) { CommandParser.Cmd c = cs.get(0); execute(c.type, c.a, c.b); return true; }
        runSteps(cs);
        return true;
    }

    // ---------------------------------------------------------------- multi-step ("X ko call karo aur phir YouTube kholo")
    private static final List<CommandParser.Cmd> QUEUE = new ArrayList<>();
    private static long queueAt;
    private static boolean afterCall;
    private boolean searching, launching;
    private long stepStarted;

    static boolean hasQueue() { synchronized (QUEUE) { return !QUEUE.isEmpty() && System.currentTimeMillis() - queueAt < 15 * 60000L; } }

    void runSteps(List<CommandParser.Cmd> cs) {
        synchronized (QUEUE) { QUEUE.clear(); QUEUE.addAll(cs); queueAt = System.currentTimeMillis(); afterCall = false; }
        boolean callFirst = false;
        for (int i = 0; i < cs.size() - 1; i++) if (isCall(cs.get(i).type)) callFirst = true;
        host.botSay("ठीक है, " + cs.size() + " काम एक-एक करके करता हूँ।" + (callFirst ? " कॉल के बाद मित्र पर लौटते ही बाकी काम कर दूँगा।" : ""));
        main.postDelayed(nextRun, 350);
    }

    /** The user is being asked something (a contact choice, a permission, a name): the queue waits for it. */
    private boolean waitingForUser() {
        return options != null || awaitName != null || permYes != null || permNo != null || searching || launching;
    }

    private static boolean isCall(String type) { return type.startsWith("call") || type.equals("dial") || type.equals("phone"); }

    private static boolean leavesApp(String type) {
        switch (type) {
            case "calc": case "time": case "date": case "battery": case "torch": case "flashlight": case "volume": case "ringer":
            case "copy": case "ask_name": case "contact_number": return false;
            default: return true;
        }
    }

    private void nextStep() {
        if (disposed) return;
        CommandParser.Cmd c;
        synchronized (QUEUE) {
            if (QUEUE.isEmpty()) return;
            if (System.currentTimeMillis() - queueAt > 15 * 60000L) { QUEUE.clear(); return; }
            c = QUEUE.remove(0);
            queueAt = System.currentTimeMillis();
        }
        stepStarted = System.currentTimeMillis();
        lastLaunch = "";
        execute(c.type, c.a, c.b);
        final boolean call = isCall(c.type) || c.type.startsWith("whatsapp_call");
        final boolean leaves = leavesApp(c.type);
        main.postDelayed(() -> afterStep(call, leaves), 400);
    }

    private void afterStep(boolean call, boolean leaves) {
        if (disposed || !hasQueue()) return;
        if (waitingForUser()) {
            if (System.currentTimeMillis() - stepStarted < 120000) { main.postDelayed(() -> afterStep(call, leaves), 400); return; }
            synchronized (QUEUE) { QUEUE.clear(); }
            return;
        }
        if (call && isCall(lastLaunch)) {
            // after a call: continue when the user comes back to Mitra (or the call ends while Mitra may open screens)
            synchronized (QUEUE) { afterCall = true; }
            main.postDelayed(this::pollCallEnd, 3000);
            return;
        }
        main.postDelayed(nextRun, leaves ? 2200 : 600);
    }

    private void pollCallEnd() {
        if (disposed || !hasQueue()) return;
        synchronized (QUEUE) { if (!afterCall) return; }
        AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
        int mode = am == null ? AudioManager.MODE_NORMAL : am.getMode();
        boolean inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE;
        boolean canDraw = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(act);
        if (!inCall && canDraw && System.currentTimeMillis() - stepStarted > 6000) { synchronized (QUEUE) { afterCall = false; } nextStep(); return; }
        main.postDelayed(this::pollCallEnd, 1500);
    }

    /** Host onResume: continue steps that waited for a call (or for the user to come back). */
    void resumeQueue() {
        if (disposed || !hasQueue()) return;
        boolean wasCall;
        synchronized (QUEUE) {
            wasCall = afterCall;
            // a leftover list from a closed screen is only continued soon after; a call may take longer
            if (!wasCall && System.currentTimeMillis() - queueAt > 3 * 60000L) { QUEUE.clear(); return; }
            afterCall = false;
        }
        if (waitingForUser()) return;
        if (wasCall || System.currentTimeMillis() - stepStarted > 2500) {
            main.removeCallbacks(nextRun);
            main.postDelayed(nextRun, 700);
        }
    }

    static void clearQueue() { synchronized (QUEUE) { QUEUE.clear(); afterCall = false; } }

    private String lastLaunch = "";
    private final Runnable nextRun = this::nextStep;

    private static final String[] YES = {"हाँ", "हां", "हा", "haan", "han", "ha", "haa", "yes", "ok", "okay", "ओके", "जी", "ji", "ji haan",
            "जी हाँ", "हाँ जी", "kholo", "खोलो", "theek hai", "ठीक है", "sahi", "सही", "करो", "karo", "lagao", "लगाओ", "बिल्कुल", "bilkul"};

    /** Handles answers to a pending question ("पहला", a name from the list, "रहने दो", or the name we asked for). */
    boolean handlePending(String raw) {
        if (options != null) {
            List<Option> ops = options;
            if (CommandParser.isCancel(raw) && CommandParser.ordinal(raw) < 0) {
                options = null;
                clearQueue();
                host.botSay("ठीक है, रहने देते हैं।");
                return true;
            }
            int o = CommandParser.ordinal(raw);
            if (o == -2) o = ops.size() - 1;
            if (o < 0 && ops.size() == 1 && has(norm(raw), YES) && norm(raw).split(" ").length <= 3) o = 0;
            if (o < 0) {
                // a name from the list?
                double best = 0, second = 0;
                int bi = -1;
                for (int i = 0; i < ops.size(); i++) {
                    if (ops.get(i).contact == null) continue;
                    double s = NameMatcher.bestScore(raw, ops.get(i).contact.name);
                    if (s > best) { second = best; best = s; bi = i; } else if (s > second) second = s;
                }
                if (bi >= 0 && best >= 0.8 && best - second >= 0.05 && CommandParser.parse(raw) == null) o = bi;
            }
            if (o >= 0 && o < ops.size()) { choose(optGen, o); return true; }
            options = null; // something else was said: drop the question and handle it normally
            return false;
        }
        if (awaitName != null) {
            String kind = awaitName, text = awaitText;
            awaitName = null;
            if (CommandParser.isCancel(raw)) { host.botSay("ठीक है।"); return true; }
            if (CommandParser.parse(raw) != null) return false;
            String n = norm(raw);
            if (n.split(" ").length > 4) return false;
            String name = CommandParser.strip(n, CommandParser.NAME_FILLER, CommandParser.CALL, CommandParser.CALL_VERB, CommandParser.KO);
            if (name.isEmpty()) return false;
            contactAction(kind, CommandParser.restore(raw, name), text);
            return true;
        }
        return false;
    }

    /** Called when an option chip is tapped (or chosen by voice). */
    boolean choose(int gen, int index) {
        if (options == null || gen != optGen || index < 0 || index >= options.size()) return false;
        Option op = options.get(index);
        options = null;
        if (op.contact != null) withContact(optKind, op.contact, optText);
        else if (op.action != null) op.action.run();
        return true;
    }

    private void ask(String question, List<Option> ops, String kind, String text) {
        options = ops;
        optKind = kind;
        optText = text;
        optGen++;
        List<String> labels = new ArrayList<>();
        for (Option o : ops) labels.add(o.label);
        host.showChoices(question, labels, optGen);
    }

    // ---------------------------------------------------------------- AI action tags
    private static final Pattern TAG = Pattern.compile("\\[\\[\\s*ACTION\\s*:\\s*([a-zA-Z_]+)\\s*(?:\\|([^\\]|]*))?(?:\\|([^\\]]*))?\\]\\]");
    private static final Pattern JSON_ACT = Pattern.compile("\\{[^{}]*\"action\"\\s*:\\s*\"[a-zA-Z_]+\"[^{}]*\\}");

    /** Executes the action tags (several run one after another) and returns the text with all tags removed. */
    String runTags(String aiText) {
        Matcher m = TAG.matcher(aiText);
        final List<CommandParser.Cmd> cmds = new ArrayList<>();
        while (m.find() && cmds.size() < 5) {
            String type = m.group(1).toLowerCase(Locale.ROOT);
            String a = m.group(2) == null ? "" : m.group(2).trim();
            String b = m.group(3) == null ? "" : m.group(3).trim();
            cmds.add(new CommandParser.Cmd(type, a, b));
        }
        if (cmds.isEmpty()) {
            Matcher j = JSON_ACT.matcher(aiText);
            if (j.find()) {
                try {
                    JSONObject o = new JSONObject(j.group());
                    String type = o.optString("action", "").toLowerCase(Locale.ROOT);
                    String a = first(o, "name", "query", "q", "url", "number", "place", "time", "value", "app", "state", "seconds", "text");
                    String b = o.has("name") || o.has("number") ? o.optString("text", o.optString("message", "")) : o.optString("message", "");
                    if (a.equals(b)) b = "";
                    if (!type.isEmpty()) cmds.add(new CommandParser.Cmd(type, a, b));
                } catch (Exception ignored) { }
                aiText = aiText.replace(j.group(), "");
            }
        }
        if (cmds.size() == 1) {
            final CommandParser.Cmd c = cmds.get(0);
            main.postDelayed(() -> { if (!disposed) execute(c.type, c.a, c.b); }, 300);
        } else if (cmds.size() > 1) {
            main.postDelayed(() -> { if (!disposed) runSteps(cmds); }, 300);
        }
        return aiText.replaceAll("\\[\\[[^\\]]*\\]\\]", "").replaceAll("(?s)```(json)?\\s*```", "").trim();
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) if (o.has(k)) return o.optString(k, "").trim();
        return "";
    }

    // ---------------------------------------------------------------- execution
    void execute(String type, String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        try {
            switch (type) {
                case "date": host.botSay(LocalInfo.dateReply()); break;
                case "time": host.botSay(LocalInfo.timeReply()); break;
                case "youtube": case "play": playYoutube(a.isEmpty() ? "भजन" : a); break;
                case "yt_search": ytSearch(a); break;
                case "open_url": case "website": case "url": {
                    if (a.isEmpty()) break;
                    String u = a.startsWith("http") ? a : "https://" + a.replace(" ", "");
                    host.botSay("खोल रहा हूँ: " + Uri.parse(u).getHost());
                    view(u);
                    break;
                }
                case "search": case "google":
                    host.botSay("Google पर खोज रहा हूँ: " + a);
                    view("https://www.google.com/search?q=" + enc(a));
                    break;
                case "open_app": case "app": openThing(a); break;
                case "dial_open": dial(""); host.botSay("डायलर खोल दिया है — नंबर मिलाइए, या बोलिए \"सोनू को कॉल करो\"।"); break;
                case "dial": case "call_number": case "call": case "phone":
                    if (CommandParser.digits(a).length() >= 3) callNumber(a.replaceAll("[^0-9+*#]", ""));
                    else contactAction("call", a, "");
                    break;
                case "call_contact": contactAction("call", a, ""); break;
                case "ask_name":
                    awaitName = a.isEmpty() ? "call" : a; awaitText = b;
                    host.botSay("किसको कॉल करूँ? नाम बताइए।");
                    break;
                case "whatsapp":
                    if (CommandParser.digits(a).length() >= 10) whatsappNumber(a, b, null);
                    else if (!a.isEmpty()) contactAction("whatsapp", a, b);
                    else if (!b.isEmpty()) shareTo(b, "com.whatsapp");
                    else openThing("whatsapp");
                    break;
                case "whatsapp_contact": contactAction("whatsapp", a, b); break;
                case "whatsapp_call": contactAction("wacall", a, ""); break;
                case "whatsapp_share": shareTo(a, "com.whatsapp"); break;
                case "sms":
                    if (CommandParser.digits(a).length() >= 10) smsNumber(a, b, null);
                    else contactAction("sms", a, b);
                    break;
                case "sms_contact": contactAction("sms", a, b); break;
                case "message_contact": contactAction("message", a, b); break;
                case "message_number":
                    if (waInstalled()) whatsappNumber(a, b, null); else smsNumber(a, b, null);
                    break;
                case "contact_number": contactAction("number", a, ""); break;
                case "alarm": setAlarmFromText(a); break;
                case "timer": {
                    String d = CommandParser.digits(a);
                    int secs = Integer.parseInt(d.isEmpty() ? "60" : d);
                    String n = norm(a);
                    if (has(n, "min", "minute", "minutes", "मिनट")) secs *= 60;
                    else if (has(n, "hour", "hours", "घंटा", "घंटे", "ghanta")) secs *= 3600;
                    Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, "मित्र टाइमर")
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    host.botSay(secs >= 60 ? (secs / 60) + " मिनट का टाइमर लगा रहा हूँ।" : secs + " सेकंड का टाइमर लगा रहा हूँ।");
                    start(i, "टाइमर वाला ऐप नहीं मिला।");
                    break;
                }
                case "maps": case "map":
                    if (a.isEmpty()) {
                        host.botSay("नक्शा खोल रहा हूँ।");
                        if (!start(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")), null)) view("https://maps.google.com");
                    } else {
                        host.botSay("नक्शे पर दिखा रहा हूँ: " + a);
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(a)));
                        if (!start(i, null)) view("https://www.google.com/maps/search/?api=1&query=" + enc(a));
                    }
                    break;
                case "camera": {
                    boolean front = has(norm(a), "front", "selfie", "सेल्फी", "true", "1");
                    host.botSay(front ? "सेल्फ़ी कैमरा खोल रहा हूँ 🤳" : "कैमरा खोल रहा हूँ 📷");
                    Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                    if (front) {
                        i.putExtra("android.intent.extras.CAMERA_FACING", 1).putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                                .putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                    }
                    if (!start(i, null)) start(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), "कैमरा ऐप नहीं मिला।");
                    break;
                }
                case "torch": case "flashlight": torch(!has(norm(a), "off", "band", "बंद", "ऑफ", "false", "0")); break;
                case "calc": host.botSay(a.isEmpty() ? "हिसाब समझ नहीं आया, फिर से बोलिए।" : a); break;
                case "battery": host.botSay(batteryText()); break;
                case "volume": volume(a); break;
                case "ringer": ringer(a); break;
                case "settings": case "setting": settingsPage(a, b); break;
                case "copy": case "note": copy(a); break;
                case "reminder": reminder(a, b); break;
                case "upi": upi(); break;
                case "navigate": case "navigation": navigate(a); break;
                case "selfie": execute("camera", "front", ""); break;
                case "share": {
                    host.botSay("शेयर करने के लिए ऐप चुनिए।");
                    Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a);
                    start(Intent.createChooser(s, "शेयर करें"), null);
                    break;
                }
                default: break;
            }
        } catch (Exception e) {
            host.botSay("माफ़ कीजिए, यह काम नहीं हो पाया।");
        }
    }

    /** kept for older callers */
    void run(String type, String a, String b) { execute(type, a, b); }

    // ---------------------------------------------------------------- contacts
    private static String verbQuestion(String kind) {
        switch (kind) {
            case "whatsapp": case "message": return "किसे WhatsApp संदेश भेजूँ?";
            case "wacall": return "किसकी WhatsApp चैट खोलूँ?";
            case "sms": return "किसे SMS भेजूँ?";
            case "number": return "किसका नंबर चाहिए?";
            default: return "किसको कॉल करूँ?";
        }
    }

    /** kind: call | whatsapp | wacall | sms | message | number */
    void contactAction(final String kind, String nameRaw, final String text) {
        String name = nameRaw == null ? "" : nameRaw.trim();
        if (CommandParser.LAST.equals(name) || CommandParser.LAST.equals(norm(name))) {
            if (lastContact != null) { withContact(kind, lastContact, text); return; }
            if (lastNumber != null) { withNumber(kind, lastNumber, lastNumber, text); return; }
            awaitName = kind; awaitText = text;
            host.botSay(verbQuestion(kind) + " नाम बताइए।");
            return;
        }
        if (CommandParser.digits(name).length() >= 7) { withNumber(kind, CommandParser.digits(name), name, text); return; }
        if (name.isEmpty()) { awaitName = kind; awaitText = text; host.botSay(verbQuestion(kind) + " नाम बताइए।"); return; }
        final String q = name;
        withPermission(Manifest.permission.READ_CONTACTS, REQ_CONTACTS,
                "\"" + q + "\" का नंबर आपके फ़ोन के कॉन्टैक्ट्स में ढूँढने के लिए मुझे कॉन्टैक्ट्स देखने की इजाज़त चाहिए। "
                        + "कॉन्टैक्ट्स सिर्फ़ आपके फ़ोन पर ही पढ़े जाते हैं — न कहीं भेजे जाते हैं, न सेव होते हैं।",
                () -> searchContacts(kind, q, text),
                () -> {
                    String tip = permanentlyDenied ? " (सेटिंग्स › ऐप्स › मित्र › अनुमतियाँ में Contacts चालू कर सकते हैं।)" : "";
                    switch (kind) {
                        case "call": host.botSay("कॉन्टैक्ट्स की इजाज़त नहीं मिली, इसलिए डायलर खोल रहा हूँ — वहाँ " + q + " का नाम खोजकर कॉल कीजिए।" + tip); dial(""); break;
                        case "whatsapp": case "message": case "wacall":
                            host.botSay("कॉन्टैक्ट्स की इजाज़त नहीं मिली — WhatsApp में " + q + " को खुद चुन लीजिए।" + tip);
                            if (text.isEmpty()) openThing("whatsapp"); else shareTo(text, "com.whatsapp");
                            break;
                        case "sms": host.botSay("कॉन्टैक्ट्स की इजाज़त नहीं मिली — मैसेज ऐप में " + q + " को चुन लीजिए।" + tip); smsNumber("", text, null); break;
                        default: host.botSay("कॉन्टैक्ट्स देखने की इजाज़त नहीं मिली।" + tip);
                    }
                });
    }

    private void searchContacts(final String kind, final String q, final String text) {
        searching = true;
        bg(() -> {
            List<ContactsHelper.Contact> found;
            try { found = ContactsHelper.find(act, q); } catch (Exception e) { found = new ArrayList<>(); }
            final List<ContactsHelper.Contact> res = found;
            main.post(() -> { searching = false; if (!disposed) onFound(kind, q, text, res); });
        });
    }

    private void onFound(String kind, String q, String text, List<ContactsHelper.Contact> res) {
        if (res.isEmpty()) {
            List<Option> ops = new ArrayList<>();
            if ("call".equals(kind) || "number".equals(kind)) {
                ops.add(new Option("📞 डायलर खोलें", null, () -> { dial(""); host.botSay("डायलर खोल दिया है।"); }));
                ask("आपके कॉन्टैक्ट्स में \"" + q + "\" नाम नहीं मिला। डायलर खोल दूँ?", ops, kind, text);
            } else if ("sms".equals(kind)) {
                ops.add(new Option("✉️ मैसेज ऐप खोलें", null, () -> smsNumber("", text, null)));
                ask("आपके कॉन्टैक्ट्स में \"" + q + "\" नाम नहीं मिला। मैसेज ऐप खोलकर खुद चुन लें?", ops, kind, text);
            } else {
                ops.add(new Option("💬 WhatsApp खोलें", null, () -> { if (text.isEmpty()) openThing("whatsapp"); else shareTo(text, "com.whatsapp"); }));
                ask("आपके कॉन्टैक्ट्स में \"" + q + "\" नाम नहीं मिला। WhatsApp खोलकर खुद चुन लें?", ops, kind, text);
            }
            return;
        }
        ContactsHelper.Contact top = res.get(0);
        double second = res.size() > 1 ? res.get(1).score : 0;
        boolean exactUnique = top.score >= 0.999 && second < 0.999;
        boolean clear = top.score >= 0.86 && top.score - second >= 0.12;
        if (exactUnique || clear) { withContact(kind, top, text); return; }
        List<Option> ops = new ArrayList<>();
        double floor = Math.max(NameMatcher.TOKEN_OK, top.score - 0.2);
        for (ContactsHelper.Contact k : res) {
            if (k.score < floor || ops.size() >= 6) break;
            String tl = k.typeLabel();
            ops.add(new Option(k.name + "  •  " + k.number + (tl.isEmpty() ? "" : " (" + tl + ")"), k, null));
        }
        if (ops.size() == 1) ask("क्या आपका मतलब \"" + top.name + "\" से है?", ops, kind, text);
        else ask(verbQuestion(kind) + " नाम या \"पहला / दूसरा\" बोलिए, या नीचे से चुनिए।", ops, kind, text);
    }

    private void withContact(String kind, ContactsHelper.Contact k, String text) {
        lastContact = k;
        withNumber(kind, k.number, k.name, text);
    }

    private void withNumber(String kind, String number, String who, String text) {
        switch (kind) {
            case "call": callNumber(number, who); break;
            case "whatsapp": whatsappNumber(number, text, who); break;
            case "message":
                if (waInstalled()) whatsappNumber(number, text, who); else smsNumber(number, text, who);
                break;
            case "wacall": {
                host.botSay(who + " की WhatsApp चैट खोल रहा हूँ — ऊपर 📞 (या 🎥) बटन दबाकर कॉल कीजिए। WhatsApp कॉल मैं सीधे नहीं लगा सकता।");
                openWa(number, "");
                break;
            }
            case "sms": smsNumber(number, text, who); break;
            case "number": {
                host.botSay(who + " का नंबर: " + number);
                final String n = number, w = who;
                List<Option> ops = new ArrayList<>();
                ops.add(new Option("📞 कॉल करें", null, () -> callNumber(n, w)));
                ops.add(new Option("💬 WhatsApp", null, () -> whatsappNumber(n, "", w)));
                ask("कॉल करूँ या WhatsApp खोलूँ?", ops, kind, "");
                break;
            }
            default: callNumber(number, who);
        }
    }

    // ---------------------------------------------------------------- calling / messaging
    void callNumber(String number) { callNumber(number, null); }

    void callNumber(final String number, final String who) {
        if (who == null || CommandParser.digits(who).length() >= 7) lastNumber = number;
        final String label = who == null || CommandParser.digits(who).length() >= 7 ? number : who;
        withPermission(Manifest.permission.CALL_PHONE, REQ_CALL,
                "सीधे कॉल लगाने के लिए मुझे फ़ोन कॉल करने की इजाज़त चाहिए। मना करेंगे तो भी ठीक है — मैं नंबर भरकर डायलर खोल दूँगा।",
                () -> {
                    host.botSay(label + " को कॉल लगा रहा हूँ 📞");
                    launching = true;
                    main.postDelayed(() -> {
                        launching = false;
                        if (disposed) return;
                        lastLaunch = "call";
                        Intent i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)));
                        if (!start(i, null)) dial(number);
                    }, 700);
                },
                () -> {
                    host.botSay(label + " का नंबर डायलर में भर दिया है — हरा बटन दबाकर कॉल कीजिए।");
                    lastLaunch = "call";
                    dial(number);
                });
    }

    private boolean waInstalled() {
        PackageManager pm = act.getPackageManager();
        return pm.getLaunchIntentForPackage("com.whatsapp") != null || pm.getLaunchIntentForPackage("com.whatsapp.w4b") != null;
    }

    void whatsappNumber(String number, String text, String who) {
        String n = ContactsHelper.intl(number);
        String label = who == null ? number : who;
        if (n.isEmpty()) { if (text.isEmpty()) openThing("whatsapp"); else shareTo(text, "com.whatsapp"); return; }
        host.botSay(text.isEmpty() ? label + " की WhatsApp चैट खोल रहा हूँ।"
                : "WhatsApp पर " + label + " के लिए संदेश तैयार है — भेजने के लिए Send ➤ दबाइए।");
        openWa(n, text);
    }

    private void openWa(String number, String text) {
        String n = ContactsHelper.intl(number);
        String url = "https://wa.me/" + n + (text == null || text.isEmpty() ? "" : "?text=" + enc(text).replace("+", "%20"));
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.setPackage("com.whatsapp");
        if (start(i, null)) return;
        i.setPackage("com.whatsapp.w4b");
        if (start(i, null)) return;
        i.setPackage(null);
        start(i, "WhatsApp नहीं मिला।");
    }

    void smsNumber(String number, String text, String who) {
        String n = number == null ? "" : number.replaceAll("[^0-9+]", "");
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + n));
        if (text != null && !text.isEmpty()) i.putExtra("sms_body", text);
        if (!n.isEmpty()) host.botSay((who == null ? n : who) + " के लिए SMS तैयार है — भेजने के लिए Send दबाइए।");
        start(i, "मैसेज ऐप नहीं मिला।");
    }

    // ---------------------------------------------------------------- runtime permissions
    private Runnable permYes, permNo;
    private int permCode;
    private boolean permanentlyDenied;

    private void withPermission(String perm, int code, String why, Runnable yes, Runnable no) {
        permanentlyDenied = false;
        if (Build.VERSION.SDK_INT < 23 || act.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) { yes.run(); return; }
        String key = "asked_" + perm;
        if (prefs.getBoolean(key, false) && !act.shouldShowRequestPermissionRationale(perm)) {
            // user chose "don't ask again" earlier
            permanentlyDenied = true;
            no.run();
            return;
        }
        prefs.edit().putBoolean(key, true).apply();
        permYes = yes; permNo = no; permCode = code;
        host.botSay(why);
        act.requestPermissions(new String[]{perm}, code);
    }

    /** Forwarded from Activity.onRequestPermissionsResult. */
    boolean onPermissionResult(int code, boolean granted) {
        if (code != permCode || (permYes == null && permNo == null)) return false;
        Runnable r = granted ? permYes : permNo;
        permYes = null; permNo = null;
        if (r != null) r.run();
        return true;
    }

    // ---- YouTube
    void playYoutube(final String query) {
        host.botSay("YouTube पर \"" + query + "\" चला रहा हूँ ▶️");
        launching = true;
        boolean queued = bg(() -> {
            String id = null;
            try {
                // read only until the first video id (a few hundred KB at most) instead of the whole results page
                id = AiClient.firstMatch("https://www.youtube.com/results?search_query=" + enc(query) + "&hl=hi&gl=IN",
                        YT_ID, 1, 1500000, 6000);
            } catch (Exception ignored) { }
            final String vid = id;
            main.post(() -> {
                launching = false;
                if (disposed) return;
                if (vid != null) {
                    Intent app = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + vid));
                    if (!start(app, null)) view("https://www.youtube.com/watch?v=" + vid);
                } else {
                    ytSearchIntent(query);
                }
            });
        });
        if (!queued) { launching = false; ytSearchIntent(query); }
    }

    private static final Pattern YT_ID = Pattern.compile("\"videoId\"\\s*:\\s*\"([A-Za-z0-9_-]{11})\"");

    void ytSearch(String q) {
        host.botSay("YouTube पर खोज रहा हूँ: " + q);
        ytSearchIntent(q);
    }

    private void ytSearchIntent(String q) {
        Intent s = new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q);
        if (!start(s, null)) view("https://www.youtube.com/results?search_query=" + enc(q));
    }

    // ---- open app or site
    private static final Map<String, String> ALIAS = new HashMap<>();
    private static final Map<String, String[]> PKG = new HashMap<>();
    private static final Map<String, String> CATEGORY = new HashMap<>();
    private static final Map<String, String> SITES = new HashMap<>();
    private static final Map<String, String> SAY = new HashMap<>();
    static {
        String[][] a = {
                {"व्हाट्सएप", "whatsapp"}, {"व्हाट्सऐप", "whatsapp"}, {"वाट्सएप", "whatsapp"}, {"watsapp", "whatsapp"}, {"whats app", "whatsapp"},
                {"यूट्यूब", "youtube"}, {"you tube", "youtube"}, {"yt", "youtube"},
                {"फेसबुक", "facebook"}, {"fb", "facebook"}, {"facebook lite", "facebook"},
                {"इंस्टाग्राम", "instagram"}, {"इंस्टा", "instagram"}, {"insta", "instagram"},
                {"फ्लिपकार्ट", "flipkart"}, {"अमेजन", "amazon"}, {"अमेजॉन", "amazon"}, {"amazon shopping", "amazon"},
                {"गूगल", "google"}, {"क्रोम", "chrome"}, {"गूगल क्रोम", "chrome"}, {"google chrome", "chrome"}, {"browser", "browser"}, {"ब्राउजर", "browser"},
                {"गैलरी", "gallery"}, {"galary", "gallery"}, {"gallary", "gallery"}, {"galery", "gallery"}, {"गेलरी", "gallery"},
                {"फोटो", "photos"}, {"फोटोज", "photos"}, {"photo", "photos"}, {"google photos", "photos"},
                {"कैलकुलेटर", "calculator"}, {"केलकुलेटर", "calculator"}, {"calculater", "calculator"}, {"calc", "calculator"}, {"हिसाब", "calculator"},
                {"सेटिंग", "settings"}, {"सेटिंग्स", "settings"}, {"setting", "settings"}, {"सेटिंग्ज", "settings"},
                {"घडी", "clock"}, {"क्लॉक", "clock"}, {"clock", "clock"}, {"घड़ी", "clock"},
                {"कैलेंडर", "calendar"}, {"कैलेंडर", "calendar"}, {"calender", "calendar"},
                {"मैसेज", "messages"}, {"मैसेजेस", "messages"}, {"संदेश", "messages"}, {"message", "messages"}, {"sms", "messages"}, {"एसएमएस", "messages"},
                {"फोन", "phone"}, {"phone", "phone"}, {"कॉल", "phone"}, {"call", "phone"}, {"डायलर", "phone"}, {"dialer", "phone"}, {"dialler", "phone"},
                {"calling", "phone"}, {"कॉलिंग", "phone"}, {"फोन डायलर", "phone"},
                {"कॉन्टैक्ट", "contacts"}, {"कॉन्टैक्ट्स", "contacts"}, {"कांटेक्ट", "contacts"}, {"संपर्क", "contacts"}, {"contact", "contacts"},
                {"फोनपे", "phonepe"}, {"फोन पे", "phonepe"}, {"phone pe", "phonepe"}, {"phonepay", "phonepe"}, {"phone pay", "phonepe"},
                {"पेटीएम", "paytm"}, {"paytm", "paytm"},
                {"जीपे", "gpay"}, {"जी पे", "gpay"}, {"गूगल पे", "gpay"}, {"google pay", "gpay"}, {"g pay", "gpay"}, {"gpay", "gpay"},
                {"प्ले स्टोर", "play store"}, {"प्लेस्टोर", "play store"}, {"playstore", "play store"}, {"google play", "play store"},
                {"जीमेल", "gmail"}, {"ईमेल", "gmail"}, {"email", "gmail"}, {"टेलीग्राम", "telegram"}, {"स्नैपचैट", "snapchat"},
                {"मैप्स", "maps"}, {"मैप", "maps"}, {"google maps", "maps"}, {"नक्शा", "maps"}, {"स्पॉटिफाई", "spotify"}, {"स्पोटिफाई", "spotify"},
                {"ट्विटर", "twitter"}, {"शेयरचैट", "sharechat"}, {"मीशो", "meesho"}, {"स्विगी", "swiggy"}, {"जोमैटो", "zomato"}, {"ओला", "ola"},
                {"उबर", "uber"}, {"हॉटस्टार", "hotstar"}, {"नेटफ्लिक्स", "netflix"}, {"रेडियो", "radio"}, {"म्यूजिक", "music"},
                {"फाइल्स", "files"}, {"फाइल", "files"}, {"file manager", "files"}, {"नोट्स", "notes"}, {"मिंत्रा", "myntra"}, {"चैटजीपीटी", "chatgpt"},
                {"जियो", "jio"}, {"एयरटेल", "airtel"}, {"आईआरसीटीसी", "irctc"}, {"यूपीआई", "upi"}, {"भीम", "bhim"}, {"स्कैनर", "scanner"},
                {"रिकॉर्डर", "recorder"}, {"गेम", "game"}, {"लूडो", "ludo"}, {"मोज", "moj"}, {"जोश", "josh"}, {"कैमरा", "camera"}, {"camera", "camera"},
                {"alarm", "clock"}, {"अलार्म", "clock"}
        };
        for (String[] p : a) ALIAS.put(CommandParser.fold(p[0]), p[1]);
        String[][] k = {
                {"whatsapp", "com.whatsapp", "com.whatsapp.w4b"}, {"youtube", "com.google.android.youtube"},
                {"facebook", "com.facebook.katana", "com.facebook.lite"}, {"instagram", "com.instagram.android"},
                {"phonepe", "com.phonepe.app"}, {"paytm", "net.one97.paytm"}, {"gpay", "com.google.android.apps.nbu.paisa.user"},
                {"flipkart", "com.flipkart.android"}, {"amazon", "in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"},
                {"chrome", "com.android.chrome"}, {"play store", "com.android.vending"}, {"gmail", "com.google.android.gm"},
                {"telegram", "org.telegram.messenger"}, {"maps", "com.google.android.apps.maps"}, {"snapchat", "com.snapchat.android"},
                {"spotify", "com.spotify.music"}, {"meesho", "com.meesho.supply"}, {"photos", "com.google.android.apps.photos"},
                {"calculator", "com.google.android.calculator", "com.miui.calculator", "com.sec.android.app.popupcalculator",
                        "com.coloros.calculator", "com.oneplus.calculator", "com.android.bbkcalculator", "com.android.calculator2"},
                {"gallery", "com.miui.gallery", "com.sec.android.gallery3d", "com.coloros.gallery3d", "com.vivo.gallery",
                        "com.oneplus.gallery", "com.android.gallery3d", "com.google.android.apps.photos"},
                {"files", "com.google.android.apps.nbu.files", "com.mi.android.globalFileexplorer", "com.sec.android.app.myfiles"},
                {"google", "com.google.android.googlequicksearchbox"}, {"twitter", "com.twitter.android"},
                {"sharechat", "in.mohalla.sharechat"}, {"swiggy", "in.swiggy.android"}, {"zomato", "com.application.zomato"},
                {"ola", "com.olacabs.customer"}, {"uber", "com.ubercab"}, {"hotstar", "in.startv.hotstar"}, {"netflix", "com.netflix.mediaclient"},
                {"myntra", "com.myntra.android"}, {"chatgpt", "com.openai.chatgpt"}, {"jio", "com.jio.myjio"}, {"airtel", "com.myairtelapp"},
                {"irctc", "cris.org.in.prs.ima"}, {"bhim", "in.org.npci.upiapp"}, {"moj", "in.mohalla.video"}
        };
        for (String[] p : k) PKG.put(p[0], java.util.Arrays.copyOfRange(p, 1, p.length));
        CATEGORY.put("calculator", Intent.CATEGORY_APP_CALCULATOR);
        CATEGORY.put("gallery", Intent.CATEGORY_APP_GALLERY);
        CATEGORY.put("photos", Intent.CATEGORY_APP_GALLERY);
        CATEGORY.put("contacts", Intent.CATEGORY_APP_CONTACTS);
        CATEGORY.put("messages", Intent.CATEGORY_APP_MESSAGING);
        CATEGORY.put("calendar", Intent.CATEGORY_APP_CALENDAR);
        CATEGORY.put("music", Intent.CATEGORY_APP_MUSIC);
        CATEGORY.put("browser", Intent.CATEGORY_APP_BROWSER);
        CATEGORY.put("chrome", Intent.CATEGORY_APP_BROWSER);
        CATEGORY.put("maps", Intent.CATEGORY_APP_MAPS);
        CATEGORY.put("gmail", Intent.CATEGORY_APP_EMAIL);
        String[][] say = {{"whatsapp", "WhatsApp"}, {"youtube", "YouTube"}, {"facebook", "Facebook"}, {"instagram", "Instagram"},
                {"phonepe", "PhonePe"}, {"paytm", "Paytm"}, {"gpay", "Google Pay"}, {"flipkart", "Flipkart"}, {"amazon", "Amazon"},
                {"chrome", "Chrome"}, {"play store", "Play Store"}, {"calculator", "कैलकुलेटर"}, {"gallery", "गैलरी"}, {"photos", "फ़ोटो"},
                {"contacts", "कॉन्टैक्ट्स"}, {"messages", "मैसेज"}, {"calendar", "कैलेंडर"}, {"maps", "नक्शा"}, {"gmail", "Gmail"}};
        for (String[] p : say) SAY.put(p[0], p[1]);
        String[][] s = {
                {"flipkart", "https://www.flipkart.com"}, {"amazon", "https://www.amazon.in"}, {"google", "https://www.google.com"},
                {"youtube", "https://www.youtube.com"}, {"facebook", "https://www.facebook.com"}, {"instagram", "https://www.instagram.com"},
                {"irctc", "https://www.irctc.co.in"}, {"meesho", "https://www.meesho.com"}, {"myntra", "https://www.myntra.com"},
                {"wikipedia", "https://hi.wikipedia.org"}, {"gmail", "https://mail.google.com"}, {"swiggy", "https://www.swiggy.com"},
                {"zomato", "https://www.zomato.com"}, {"paytm", "https://paytm.com"}, {"chatgpt", "https://chatgpt.com"},
                {"twitter", "https://x.com"}, {"netflix", "https://www.netflix.com"}, {"hotstar", "https://www.hotstar.com"},
                {"sarkari result", "https://www.sarkariresult.com"}, {"jio", "https://www.jio.com"}, {"airtel", "https://www.airtel.in"},
                {"maps", "https://maps.google.com"}, {"telegram", "https://web.telegram.org"}, {"spotify", "https://open.spotify.com"},
                {"play store", "https://play.google.com/store"}, {"phonepe", "https://www.phonepe.com"}
        };
        for (String[] p : s) SITES.put(p[0], p[1]);
    }

    /** Maps Hindi / misspelt app names to a canonical key ("फोन पे" → "phonepe"). */
    static String canonicalApp(String targetRaw) {
        String target = norm(targetRaw);
        String direct = ALIAS.get(target);
        if (direct != null) return direct;
        List<String> keys = new ArrayList<>(ALIAS.keySet());
        Collections.sort(keys, (x, y) -> y.length() - x.length());
        String p = " " + target + " ";
        for (String k : keys) if (p.contains(" " + k + " ")) p = p.replace(" " + k + " ", " " + ALIAS.get(k) + " ");
        return p.replaceAll("\\s+", " ").trim();
    }

    void openThing(String targetRaw) {
        String target = canonicalApp(targetRaw);
        if (target.isEmpty()) return;
        if (target.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+(/\\S*)?")) {
            host.botSay("खोल रहा हूँ: " + target);
            view("https://" + target);
            return;
        }
        switch (target) {
            case "settings":
                host.botSay("सेटिंग्स खोल रहा हूँ।");
                start(new Intent(Settings.ACTION_SETTINGS), null);
                return;
            case "camera": execute("camera", null, null); return;
            case "phone":
                host.botSay("फ़ोन का डायलर खोल रहा हूँ 📞 — किसी को कॉल करना हो तो बोलिए, जैसे \"सोनू को कॉल करो\"।");
                dial("");
                return;
            case "clock":
                host.botSay("घड़ी खोल रहा हूँ ⏰");
                start(new Intent(AlarmClock.ACTION_SHOW_ALARMS), "घड़ी ऐप नहीं मिला।");
                return;
            default: break;
        }
        PackageManager pm = act.getPackageManager();
        String[] pkgs = PKG.get(target);
        if (pkgs != null) {
            for (String pk : pkgs) {
                Intent li = pm.getLaunchIntentForPackage(pk);
                if (li != null) {
                    host.botSay((SAY.containsKey(target) ? SAY.get(target) : targetRaw) + " खोल रहा हूँ।");
                    start(li, null);
                    return;
                }
            }
        }
        String cat = CATEGORY.get(target);
        if (cat != null) {
            Intent ci = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (ci.resolveActivity(pm) != null || !pm.queryIntentActivities(ci, 0).isEmpty()) {
                host.botSay((SAY.containsKey(target) ? SAY.get(target) : targetRaw) + " खोल रहा हूँ।");
                if (start(ci, null)) return;
            }
        }
        if ("upi".equals(target) || "bhim upi".equals(target)) { upi(); return; }
        Intent app = findApp(target);
        if (app == null && !norm(targetRaw).equals(target)) app = findApp(targetRaw);
        if (app == null && "gallery".equals(target)) app = findApp("photos");
        if (app == null && "photos".equals(target)) app = findApp("gallery");
        if (app == null && "gpay".equals(target)) app = findApp("google pay");
        if (app == null && "messages".equals(target)) app = findApp("messaging");
        if (app != null) {
            host.botSay("\"" + app.getStringExtra("mitra_label") + "\" खोल रहा हूँ।");
            app.removeExtra("mitra_label");
            lastLaunch = "app";
            start(app, null);
            return;
        }
        String site = SITES.get(target);
        if (site != null) {
            host.botSay("यह ऐप फ़ोन में नहीं मिला, वेबसाइट खोल रहा हूँ: " + Uri.parse(site).getHost());
            view(site);
            return;
        }
        host.botSay("\"" + targetRaw + "\" नाम का ऐप नहीं मिला, Google पर खोज रहा हूँ।");
        view("https://www.google.com/search?q=" + enc(targetRaw));
    }

    // launcher apps, cached for the process (loading labels takes a few hundred ms on big phones)
    private static volatile List<AppMatcher.App> appCache;
    private static volatile long appCacheAt;
    private static final Map<String, String> CLASS_OF = new HashMap<>();

    static List<AppMatcher.App> launcherApps(Context c) {
        List<AppMatcher.App> l = appCache;
        if (l != null && System.currentTimeMillis() - appCacheAt < 10 * 60000L) return l;
        List<AppMatcher.App> out = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                if (r.activityInfo == null || c.getPackageName().equals(r.activityInfo.packageName)) continue;
                String label = String.valueOf(r.loadLabel(pm));
                out.add(new AppMatcher.App(label, r.activityInfo.packageName));
                synchronized (CLASS_OF) { CLASS_OF.put(r.activityInfo.packageName + "|" + label, r.activityInfo.name); }
            }
        } catch (Exception ignored) { }
        appCache = out;
        appCacheAt = System.currentTimeMillis();
        return out;
    }

    /** Fuzzy match on the launcher list (Hindi or English name, misspellings, package words). */
    private Intent findApp(String target) {
        if (target == null || target.trim().isEmpty()) return null;
        List<AppMatcher.App> apps = launcherApps(act);
        int bi = AppMatcher.best(target, apps);
        if (bi < 0) return null;
        AppMatcher.App a = apps.get(bi);
        String cls;
        synchronized (CLASS_OF) { cls = CLASS_OF.get(a.pkg + "|" + a.label); }
        Intent i = cls != null ? new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(a.pkg, cls)
                : act.getPackageManager().getLaunchIntentForPackage(a.pkg);
        if (i == null) return null;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("mitra_label", a.label);
        return i;
    }

    // ---- alarm
    void setAlarmFromText(String raw) {
        int rel = TimeParse.relativeMinutes(raw);
        int h, min;
        if (rel > 0) {
            java.util.Calendar k = java.util.Calendar.getInstance();
            k.add(java.util.Calendar.MINUTE, rel);
            h = k.get(java.util.Calendar.HOUR_OF_DAY);
            min = k.get(java.util.Calendar.MINUTE);
        } else {
            int[] c = TimeParse.clock(raw);
            if (c == null) {
                host.botSay("अलार्म ऐप खोल रहा हूँ — समय चुन लीजिए। अगली बार बोलिए, जैसे \"सुबह 6 बजे का अलार्म लगाओ\"।");
                start(new Intent(AlarmClock.ACTION_SHOW_ALARMS), "अलार्म ऐप नहीं मिला।");
                return;
            }
            h = c[0];
            min = c[1];
        }
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, min)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "मित्र अलार्म")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        host.botSay(clockText(h, min) + " बजे का अलार्म लगा रहा हूँ ⏰");
        start(i, "अलार्म ऐप नहीं मिला।");
    }

    static String clockText(int h, int min) {
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String part = h < 4 ? "रात" : h < 12 ? "सुबह" : h < 16 ? "दोपहर" : h < 20 ? "शाम" : "रात";
        return part + " " + h12 + ":" + (min < 10 ? "0" + min : String.valueOf(min));
    }

    // ---- reminder (calendar event with an alert; no calendar permission needed — the calendar app saves it)
    void reminder(String raw, String explicitWhen) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        long at = TimeParse.when(explicitWhen.isEmpty() ? raw : explicitWhen + " " + raw, now);
        String title = TimeParse.reminderTitle(raw);
        if (title.isEmpty()) title = "मित्र रिमाइंडर";
        boolean timed = at > 0;
        if (!timed) at = now.getTimeInMillis() + 3600000L;
        Intent i = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, at)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, at + 15 * 60000L)
                .putExtra(CalendarContract.Events.HAS_ALARM, 1)
                .putExtra(CalendarContract.Events.DESCRIPTION, "मित्र से बनाया गया रिमाइंडर");
        java.util.Calendar k = java.util.Calendar.getInstance();
        k.setTimeInMillis(at);
        int days = k.get(java.util.Calendar.DAY_OF_YEAR) - now.get(java.util.Calendar.DAY_OF_YEAR);
        String day = days == 0 ? "आज" : days == 1 ? "कल" : days == 2 ? "परसों" : k.get(java.util.Calendar.DAY_OF_MONTH) + " तारीख़ को";
        host.botSay(timed ? day + " " + clockText(k.get(java.util.Calendar.HOUR_OF_DAY), k.get(java.util.Calendar.MINUTE)) + " बजे का रिमाइंडर: \"" + title + "\" — कैलेंडर में Save दबाइए।"
                : "रिमाइंडर \"" + title + "\" कैलेंडर में खोल रहा हूँ — समय चुनकर Save दबाइए।");
        if (start(i, null)) return;
        // no calendar app: fall back to an alarm with the title (time only)
        Intent al = new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, k.get(java.util.Calendar.HOUR_OF_DAY))
                .putExtra(AlarmClock.EXTRA_MINUTES, k.get(java.util.Calendar.MINUTE)).putExtra(AlarmClock.EXTRA_MESSAGE, title)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        start(al, "कैलेंडर या अलार्म ऐप नहीं मिला।");
    }

    // ---- notes → clipboard
    void copy(String text) {
        if (text == null || text.trim().isEmpty()) { host.botSay("क्या नोट करूँ? बोलिए, जैसे \"नोट कर लो कि कल 5 बजे मीटिंग है\"।"); return; }
        try {
            ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("मित्र नोट", text.trim()));
            host.botSay("नोट कर लिया और कॉपी कर दिया ✅ — \"" + text.trim() + "\"। कहीं भी देर तक दबाकर Paste कर सकते हैं।");
        } catch (Exception e) {
            host.botSay("कॉपी नहीं हो पाया।");
        }
    }

    // ---- battery
    String batteryText() {
        try {
            Intent b = act.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = -1;
            if (Build.VERSION.SDK_INT >= 21) {
                BatteryManager bm = (BatteryManager) act.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            }
            if ((level <= 0 || level > 100) && b != null) {
                int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (l >= 0 && sc > 0) level = Math.round(l * 100f / sc);
            }
            int st = b == null ? -1 : b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            if (level < 0) return "बैटरी की जानकारी नहीं मिल पाई।";
            return "बैटरी " + level + "% है" + (charging ? " और चार्ज हो रही है 🔌" : level <= 20 ? " — चार्जर लगा लीजिए 🔋" : " 🔋") + "।";
        } catch (Exception e) {
            return "बैटरी की जानकारी नहीं मिल पाई।";
        }
    }

    // ---- volume / ringer (no permission needed)
    void volume(String how) {
        AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) { host.botSay("आवाज़ नहीं बदल पाया।"); return; }
        int flags = AudioManager.FLAG_SHOW_UI;
        int stream = how.startsWith("ring") ? AudioManager.STREAM_RING : AudioManager.STREAM_MUSIC;
        try {
            switch (how) {
                case "up": case "ring_up":
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, flags);
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0);
                    break;
                case "down": case "ring_down":
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, flags);
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0);
                    break;
                case "max": am.setStreamVolume(stream, am.getStreamMaxVolume(stream), flags); break;
                case "mute":
                    if (Build.VERSION.SDK_INT >= 23) am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags);
                    else am.setStreamVolume(stream, 0, flags);
                    break;
                case "unmute":
                    if (Build.VERSION.SDK_INT >= 23) am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, flags);
                    if (am.getStreamVolume(stream) == 0) am.setStreamVolume(stream, Math.max(1, am.getStreamMaxVolume(stream) / 2), flags);
                    break;
                default: am.adjustStreamVolume(stream, AudioManager.ADJUST_SAME, flags);
            }
            int v = am.getStreamVolume(stream), max = Math.max(1, am.getStreamMaxVolume(stream));
            String what = stream == AudioManager.STREAM_RING ? "रिंगटोन की आवाज़" : "आवाज़";
            if ("mute".equals(how)) host.botSay(what + " बंद कर दी 🔇");
            else host.botSay(what + " अब " + Math.round(v * 100f / max) + "% है 🔊");
        } catch (SecurityException e) {
            host.botSay("फ़ोन 'Do Not Disturb' में है, इसलिए आवाज़ नहीं बदल पाया — साउंड सेटिंग खोल रहा हूँ।");
            settingsPage("sound", "");
        }
    }

    void ringer(String mode) {
        AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
        try {
            if ("vibrate".equals(mode)) { am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE); host.botSay("फ़ोन वाइब्रेट पर कर दिया 📳"); return; }
            if ("normal".equals(mode)) { am.setRingerMode(AudioManager.RINGER_MODE_NORMAL); host.botSay("फ़ोन की घंटी वापस चालू कर दी 🔔"); return; }
            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            host.botSay("फ़ोन साइलेंट कर दिया 🔕");
        } catch (Exception e) {
            // Android 7+: silent mode needs Do-Not-Disturb access; vibrate is the closest allowed choice
            try {
                if (!"normal".equals(mode)) { am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE); host.botSay("साइलेंट के लिए फ़ोन की इजाज़त चाहिए, इसलिए वाइब्रेट पर कर दिया 📳"); return; }
            } catch (Exception ignored) { }
            host.botSay("यह बदलने के लिए साउंड सेटिंग खोल रहा हूँ।");
            settingsPage("sound", "");
        }
    }

    // ---- settings screens (Android does not let apps flip wifi/bluetooth/data/brightness themselves on new phones)
    void settingsPage(String page, String dir) {
        String act1;
        String say;
        switch (page == null ? "" : page) {
            case "wifi":
                say = "Wi-Fi की सेटिंग खोल रहा हूँ — वहाँ " + onOff(dir) + " कर लीजिए।";
                if (Build.VERSION.SDK_INT >= 29 && start(new Intent(Settings.Panel.ACTION_WIFI), null)) { host.botSay(say); return; }
                act1 = Settings.ACTION_WIFI_SETTINGS; break;
            case "bluetooth": say = "ब्लूटूथ की सेटिंग खोल रहा हूँ — वहाँ " + onOff(dir) + " कर लीजिए।"; act1 = Settings.ACTION_BLUETOOTH_SETTINGS; break;
            case "display": say = "स्क्रीन की चमक (Brightness) की सेटिंग खोल रहा हूँ" + ("up".equals(dir) ? " — स्लाइडर दाईं ओर खिसकाइए।" : "down".equals(dir) ? " — स्लाइडर बाईं ओर खिसकाइए।" : "।") + " ऊपर से नीचे स्वाइप करके भी बदल सकते हैं।";
                act1 = Settings.ACTION_DISPLAY_SETTINGS; break;
            case "location": say = "लोकेशन (GPS) की सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
            case "airplane": say = "फ़्लाइट मोड की सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_AIRPLANE_MODE_SETTINGS; break;
            case "data":
                say = "मोबाइल डेटा / इंटरनेट की सेटिंग खोल रहा हूँ।";
                if (Build.VERSION.SDK_INT >= 29 && start(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), null)) { host.botSay(say); return; }
                act1 = Settings.ACTION_DATA_ROAMING_SETTINGS; break;
            case "hotspot": say = "हॉटस्पॉट की सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_WIRELESS_SETTINGS; break;
            case "battery": say = "बैटरी सेवर की सेटिंग खोल रहा हूँ।"; act1 = Build.VERSION.SDK_INT >= 22 ? Settings.ACTION_BATTERY_SAVER_SETTINGS : Settings.ACTION_SETTINGS; break;
            case "sound": say = "साउंड सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_SOUND_SETTINGS; break;
            case "notifications": say = "नोटिफ़िकेशन सेटिंग खोल रहा हूँ।"; act1 = Build.VERSION.SDK_INT >= 26 ? Settings.ACTION_APP_NOTIFICATION_SETTINGS : Settings.ACTION_SETTINGS; break;
            case "apps": say = "ऐप्स की सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_APPLICATION_SETTINGS; break;
            case "storage": say = "स्टोरेज की सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_INTERNAL_STORAGE_SETTINGS; break;
            case "language": say = "भाषा और कीबोर्ड की सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_LOCALE_SETTINGS; break;
            case "datetime": say = "तारीख़ और समय की सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_DATE_SETTINGS; break;
            case "security": say = "सुरक्षा सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_SECURITY_SETTINGS; break;
            case "accessibility": say = "सुलभता (Accessibility) सेटिंग खोल रहा हूँ।"; act1 = Settings.ACTION_ACCESSIBILITY_SETTINGS; break;
            case "mitra": host.botSay("मित्र की सेटिंग्स खोल रहा हूँ।"); start(new Intent(act, SettingsActivity.class), null); return;
            default: say = "सेटिंग्स खोल रहा हूँ।"; act1 = Settings.ACTION_SETTINGS;
        }
        host.botSay(say);
        Intent i = new Intent(act1);
        if ("notifications".equals(page) && Build.VERSION.SDK_INT >= 26) i.putExtra(Settings.EXTRA_APP_PACKAGE, act.getPackageName());
        if (!start(i, null)) start(new Intent(Settings.ACTION_SETTINGS), "सेटिंग्स नहीं खुल पाईं।");
    }

    private static String onOff(String dir) { return "off".equals(dir) ? "बंद" : "on".equals(dir) ? "चालू" : "चालू/बंद"; }

    // ---- UPI apps: open only (Mitra never makes payments)
    private static final String[][] UPI_APPS = {{"com.phonepe.app", "PhonePe"}, {"com.google.android.apps.nbu.paisa.user", "Google Pay"},
            {"net.one97.paytm", "Paytm"}, {"in.org.npci.upiapp", "BHIM"}, {"in.amazon.mShop.android.shopping", "Amazon Pay"},
            {"com.mobikwik_new", "MobiKwik"}, {"com.dreamplug.androidapp", "CRED"}, {"com.myairtelapp", "Airtel Thanks"}};

    void upi() {
        PackageManager pm = act.getPackageManager();
        final List<String[]> found = new ArrayList<>();
        for (String[] u : UPI_APPS) if (pm.getLaunchIntentForPackage(u[0]) != null) found.add(u);
        if (found.isEmpty()) {
            host.botSay("फ़ोन में कोई UPI ऐप नहीं मिला। Play Store / Indus Appstore से PhonePe, Google Pay या BHIM इंस्टॉल कर सकते हैं।");
            return;
        }
        String pref = prefs.getString("upi_app", "");
        for (String[] u : found) if (u[0].equals(pref) || found.size() == 1) { openUpi(u); return; }
        List<Option> ops = new ArrayList<>();
        for (final String[] u : found) ops.add(new Option("💳 " + u[1], null, () -> { prefs.edit().putString("upi_app", u[0]).apply(); openUpi(u); }));
        ask("कौन सा UPI ऐप खोलूँ? (पेमेंट आप खुद ऐप में करेंगे)", ops, "upi", "");
    }

    private void openUpi(String[] u) {
        Intent li = act.getPackageManager().getLaunchIntentForPackage(u[0]);
        if (li == null) { host.botSay(u[1] + " नहीं मिला।"); return; }
        host.botSay(u[1] + " खोल रहा हूँ — पेमेंट आप खुद ऐप में PIN डालकर करें। 🔒");
        start(li, null);
    }

    // ---- navigation
    void navigate(String place) {
        if (place == null || place.trim().isEmpty()) { execute("maps", "", ""); return; }
        host.botSay(place + " का रास्ता शुरू कर रहा हूँ 🧭");
        Intent n = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + enc(place)));
        if (start(n, null)) return;
        if (start(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(place))), null)) return;
        view("https://www.google.com/maps/dir/?api=1&destination=" + enc(place));
    }

    // ---- torch
    void torch(boolean on) {
        try {
            CameraManager cm = (CameraManager) act.getSystemService(Context.CAMERA_SERVICE);
            String id = null;
            for (String c : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(c).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) { id = c; break; }
            }
            if (id == null) { host.botSay("इस फ़ोन में टॉर्च नहीं मिली।"); return; }
            cm.setTorchMode(id, on);
            host.botSay(on ? "टॉर्च जला दी 🔦" : "टॉर्च बंद कर दी।");
        } catch (Exception e) {
            host.botSay("टॉर्च अभी चालू नहीं हो पाई — शायद कैमरा कोई और ऐप इस्तेमाल कर रहा है।");
        }
    }

    // ---- intents
    void dial(String number) {
        Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)));
        start(i, "डायलर नहीं मिला।");
    }

    void shareTo(String text, String pkg) {
        Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg);
        host.botSay("WhatsApp में संदेश तैयार है — किसे भेजना है, चुनिए।");
        if (!start(s, null)) {
            s.setPackage("com.whatsapp.w4b");
            if (!start(s, null)) {
                s.setPackage(null);
                start(Intent.createChooser(s, "शेयर करें"), null);
            }
        }
    }

    void view(String url) {
        start(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), "इसे खोलने वाला ऐप नहीं मिला।");
    }

    boolean start(Intent i, String failMsg) {
        try {
            act.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            if (failMsg != null) host.botSay(failMsg);
            return false;
        }
    }
}

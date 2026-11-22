package com.playrang.rangstudio;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understands simple phone commands in Hindi / Hinglish / English and performs them
 * with standard Android intents. It never controls other apps: it only opens them.
 */
final class ActionHandler {

    interface Host {
        void botSay(String text);
    }

    private final Activity act;
    private final Host host;
    private final ExecutorService exec;
    private final Handler main = new Handler(Looper.getMainLooper());

    ActionHandler(Activity act, Host host, ExecutorService exec) {
        this.act = act;
        this.host = host;
        this.exec = exec;
    }

    // ---------------------------------------------------------------- vocab
    private static final String[] YT = {"youtube", "यूट्यूब", "यूटयूब", "यू ट्यूब", "you tube", "यूट्यूब्स", "utube", "youtub"};
    private static final String[] PLAY = {"चलाओ", "चला दो", "चलादो", "चलाइए", "चलाइये", "चलाना", "चला", "लगाओ", "लगा दो", "लगादो", "बजाओ", "बजा दो",
            "सुनाओ", "सुना दो", "दिखाओ", "दिखा दो", "प्ले", "play", "chalao", "chala do", "chalado", "chalaiye", "chala", "lagao", "laga do",
            "bajao", "baja do", "sunao", "suna do", "dikhao", "dikha do"};
    private static final String[] SONG = {"गाना", "गाने", "गीत", "भजन", "song", "songs", "gana", "gaana", "gane", "gaane", "bhajan", "video", "वीडियो",
            "विडियो", "music", "म्यूजिक", "म्यूज़िक", "संगीत", "आरती", "aarti", "arti", "कथा", "katha", "चालीसा", "chalisa", "movie", "मूवी", "फिल्म", "film", "trailer", "ट्रेलर"};
    private static final String[] OPEN = {"खोलो", "खोल दो", "खोलदो", "खोलिए", "खोलिये", "खोलना", "खोल", "ओपन", "open", "kholo", "khol do", "kholdo",
            "kholiye", "khol", "चालू करो", "chalu karo", "start karo", "launch"};
    private static final String[] WA = {"whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप", "व्हाट्सअप", "वाट्सएप", "वॉट्सऐप", "वॉट्सएप", "व्हाट्सप",
            "व्हाट्सएप्प", "watsapp", "whatsaap", "whatsap", "watsap", "वाट्सऐप"};
    private static final String[] CALL = {"कॉल", "काल", "call", "फोन", "फ़ोन", "phone", "dial", "डायल"};
    private static final String[] CALL_VERB = {"लगाओ", "लगा दो", "करो", "कर दो", "मिलाओ", "मिला दो", "lagao", "laga do", "karo", "kar do", "milao",
            "mila do", "dial", "डायल", "call", "कॉल"};
    private static final String[] TORCH = {"टॉर्च", "टार्च", "torch", "flashlight", "flash light", "फ्लैशलाइट", "फ्लैश लाइट", "टोर्च"};
    private static final String[] OFF = {"बंद", "band", "off", "ऑफ", "बुझाओ", "bujhao", "बुझा"};
    private static final String[] CAMERA = {"कैमरा", "कैमरे", "camera", "कैमरा", "selfie", "सेल्फी"};
    private static final String[] ALARM = {"अलार्म", "अलार्मा", "alarm", "alaram", "अलारम"};
    private static final String[] TIMER = {"टाइमर", "timer"};
    private static final String[] MAP = {"मैप", "मैप्स", "map", "maps", "नक्शा", "नक्शे", "naksha", "रास्ता", "rasta", "raasta", "direction", "directions",
            "डायरेक्शन", "लोकेशन", "location", "navigation", "नेविगेशन"};
    private static final String[] GO = {"कैसे जाएं", "कैसे जाएँ", "कैसे जाऊं", "कैसे जाऊँ", "कैसे पहुंचे", "कैसे पहुँचें", "kaise jaye", "kaise jaaye", "kaise jau", "kaise pahuche"};
    private static final String[] SEARCH = {"search", "सर्च", "खोजो", "खोज", "ढूंढो", "ढूँढो", "ढूंढ", "dhundho", "dhoondho", "khojo", "google karo", "गूगल करो",
            "गूगल पर", "google par", "google pe", "गूगल पे"};
    private static final String[] SHARE = {"शेयर", "share"};
    private static final String[] Q = {"क्या", "kya", "कैसे", "kaise", "क्यों", "kyon", "kyu", "kyun", "what", "how", "why", "कौन", "kaun", "which",
            "कौनसा", "कौन सा", "मतलब", "matlab", "होता", "hota", "कितना", "kitna", "किसने", "kisne", "बारे", "bare", "baare", "about"};
    private static final String[] TIME_W = {"समय", "टाइम", "time", "बजे", "baje", "बजा", "baja"};
    private static final String[] TIME_Q = {"क्या", "kya", "कितने", "kitne", "बताओ", "batao", "what", "हुआ", "hua", "हुए", "hue", "अभी", "abhi", "now", "बताइए"};
    private static final String[] DATE_W = {"तारीख", "तारीख़", "तारिख", "तिथि", "दिनांक", "date", "tarikh", "tareekh", "तारीक"};
    private static final String[] DAY_W = {"दिन", "din", "वार", "day", "weekday"};

    private static final Map<String, String> HI_APP = new HashMap<>();
    private static final Map<String, String> SITES = new HashMap<>();
    static {
        String[][] a = {
                {"व्हाट्सएप", "whatsapp"}, {"यूट्यूब", "youtube"}, {"फेसबुक", "facebook"}, {"फ़ेसबुक", "facebook"}, {"इंस्टाग्राम", "instagram"},
                {"इंस्टा", "instagram"}, {"फ्लिपकार्ट", "flipkart"}, {"अमेज़न", "amazon"}, {"अमेजन", "amazon"}, {"अमेज़ॉन", "amazon"}, {"अमेजॉन", "amazon"},
                {"गूगल", "google"}, {"क्रोम", "chrome"}, {"गैलरी", "gallery"}, {"फोटो", "photos"}, {"फ़ोटो", "photos"}, {"फोटोज", "photos"},
                {"कैलकुलेटर", "calculator"}, {"सेटिंग", "settings"}, {"सेटिंग्स", "settings"}, {"घड़ी", "clock"}, {"कैलेंडर", "calendar"},
                {"मैसेज", "messages"}, {"मैसेजेस", "messages"}, {"संदेश", "messages"}, {"फोन", "phone"}, {"फ़ोन", "phone"}, {"कॉन्टैक्ट", "contacts"},
                {"कांटेक्ट", "contacts"}, {"संपर्क", "contacts"}, {"फोनपे", "phonepe"}, {"फ़ोनपे", "phonepe"}, {"फोन पे", "phonepe"}, {"पेटीएम", "paytm"},
                {"जीपे", "gpay"}, {"गूगल पे", "gpay"}, {"प्ले स्टोर", "play store"}, {"प्लेस्टोर", "play store"}, {"जीमेल", "gmail"}, {"टेलीग्राम", "telegram"},
                {"स्नैपचैट", "snapchat"}, {"मैप्स", "maps"}, {"मैप", "maps"}, {"स्पॉटिफ़ाई", "spotify"}, {"स्पोटिफाई", "spotify"}, {"ट्विटर", "twitter"},
                {"शेयरचैट", "sharechat"}, {"मीशो", "meesho"}, {"स्विगी", "swiggy"}, {"ज़ोमैटो", "zomato"}, {"जोमैटो", "zomato"}, {"ओला", "ola"},
                {"उबर", "uber"}, {"हॉटस्टार", "hotstar"}, {"नेटफ्लिक्स", "netflix"}, {"रेडियो", "radio"}, {"म्यूजिक", "music"}, {"म्यूज़िक", "music"},
                {"फाइल्स", "files"}, {"फ़ाइल्स", "files"}, {"फाइल", "files"}, {"नोट्स", "notes"}, {"मिंत्रा", "myntra"}, {"चैटजीपीटी", "chatgpt"},
                {"जियो", "jio"}, {"एयरटेल", "airtel"}, {"आईआरसीटीसी", "irctc"}, {"यूपीआई", "upi"}, {"भीम", "bhim"}, {"स्कैनर", "scanner"},
                {"रिकॉर्डर", "recorder"}, {"गेम", "game"}, {"लूडो", "ludo"}, {"मोज", "moj"}, {"जोश", "josh"}, {"इंडस", "indus"}
        };
        for (String[] p : a) HI_APP.put(p[0], p[1]);
        String[][] s = {
                {"flipkart", "https://www.flipkart.com"}, {"amazon", "https://www.amazon.in"}, {"google", "https://www.google.com"},
                {"youtube", "https://www.youtube.com"}, {"facebook", "https://www.facebook.com"}, {"instagram", "https://www.instagram.com"},
                {"irctc", "https://www.irctc.co.in"}, {"meesho", "https://www.meesho.com"}, {"myntra", "https://www.myntra.com"},
                {"wikipedia", "https://hi.wikipedia.org"}, {"gmail", "https://mail.google.com"}, {"swiggy", "https://www.swiggy.com"},
                {"zomato", "https://www.zomato.com"}, {"paytm", "https://paytm.com"}, {"chatgpt", "https://chatgpt.com"},
                {"twitter", "https://x.com"}, {"netflix", "https://www.netflix.com"}, {"hotstar", "https://www.hotstar.com"},
                {"sarkari result", "https://www.sarkariresult.com"}, {"jio", "https://www.jio.com"}, {"airtel", "https://www.airtel.in"},
                {"maps", "https://maps.google.com"}, {"telegram", "https://web.telegram.org"}, {"spotify", "https://open.spotify.com"}
        };
        for (String[] p : s) SITES.put(p[0], p[1]);
    }

    // ---------------------------------------------------------------- helpers
    static String norm(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '०' && ch <= '९') b.append((char) ('0' + (ch - '०')));
            else b.append(ch);
        }
        String t = b.toString().toLowerCase(Locale.ROOT)
                .replaceAll("[?!,।|\"“”'‘’;]+", " ")
                .replace("डॉट", ".").replace(" dot ", ".")
                .replaceAll("\\s+", " ").trim();
        return t;
    }

    /** whole-word / whole-phrase containment on space-separated text */
    static boolean has(String t, String... words) {
        String p = " " + t + " ";
        for (String w : words) if (p.contains(" " + w + " ")) return true;
        return false;
    }

    /** removes whole words / phrases (longest first) */
    static String strip(String t, String[]... lists) {
        List<String> all = new ArrayList<>();
        for (String[] l : lists) for (String w : l) all.add(w);
        java.util.Collections.sort(all, (x, y) -> y.length() - x.length());
        String p = " " + t + " ";
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String w : all) {
                String k = " " + w + " ";
                if (p.contains(k)) { p = p.replace(k, " "); changed = true; }
            }
        }
        return p.replaceAll("\\s+", " ").trim();
    }

    private static final String[] FILLER = {"पर", "पे", "में", "par", "pe", "per", "on", "me", "mein", "करो", "कर", "करिए", "करिये", "karo", "kar", "kariye",
            "दो", "please", "प्लीज", "प्लीज़", "pls", "मित्र", "mitra", "मेरे", "लिए", "liye", "mere", "जरा", "ज़रा", "zara", "थोड़ा", "thoda", "मुझे", "mujhe",
            "को", "ko", "एक", "ek", "कोई", "koi", "a", "the", "for", "me", "से", "se", "वाला", "wala", "वाली", "wali", "भाई", "bhai", "यार", "yaar", "हे", "hey", "ऐप", "app",
            "एप", "एप्प", "एप्लीकेशन", "application", "वेबसाइट", "website", "साइट", "site", "page", "पेज", "जी", "ji", "है", "hai", "दीजिए", "दीजिये", "dijiye", "do", "to"};

    private static String digits(String t) {
        StringBuilder d = new StringBuilder();
        for (char c : t.toCharArray()) if (c >= '0' && c <= '9') d.append(c);
        return d.toString();
    }

    private static String phoneDigits(String t) {
        // longest run of digits allowing spaces/dashes inside, keeps +
        Matcher m = Pattern.compile("\\+?\\d[\\d\\s-]{2,}\\d").matcher(t);
        String best = "";
        while (m.find()) { String d = digits(m.group()); if (d.length() > best.length()) best = d; }
        return best;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    // ---------------------------------------------------------------- local detection
    /** @return true when the command was understood and handled on the device */
    boolean tryLocal(String raw) {
        String t = norm(raw);
        if (t.isEmpty()) return false;
        int words = t.split(" ").length;
        boolean question = has(t, Q);

        // Alarm / timer
        if (has(t, ALARM) || (has(t, "जगाना", "जगा", "उठाना", "jagana", "jaga", "uthana") && Pattern.compile("\\d").matcher(t).find())) {
            if (question && !has(t, "लगाओ", "लगा", "सेट", "set", "lagao", "laga", "lagado", "रखो", "rakho")) return false;
            setAlarmFromText(t);
            return true;
        }
        if (has(t, TIMER)) {
            Matcher m = Pattern.compile("(\\d+)\\s*(मिनट|minute|minutes|min|सेकंड|second|seconds|sec|घंटे|घंटा|hour|hours|ghante|ghanta)").matcher(t);
            if (m.find()) {
                int v = Integer.parseInt(m.group(1));
                String u = m.group(2);
                int secs = u.startsWith("से") || u.startsWith("sec") ? v : (u.startsWith("घ") || u.startsWith("h") || u.startsWith("gh")) ? v * 3600 : v * 60;
                run("timer", String.valueOf(secs), null);
                return true;
            }
        }
        // Time / date (answered locally)
        if (words <= 8 && has(t, DATE_W)) { host.botSay(LocalInfo.dateReply()); return true; }
        if (words <= 8 && has(t, "आज", "aaj", "today") && has(t, DAY_W) && !has(t, "अच्छा", "accha", "कैसा", "kaisa")) {
            host.botSay(LocalInfo.dateReply()); return true;
        }
        if (words <= 8 && has(t, TIME_W) && has(t, TIME_Q) && !has(t, "कब", "kab", "when")) { host.botSay(LocalInfo.timeReply()); return true; }

        // Torch
        if (has(t, TORCH) || (has(t, "लाइट", "light") && has(t, "जलाओ", "jalao", "ऑन", "on", "बंद", "band", "off", "ऑफ", "बुझाओ"))) {
            if (question && !has(t, "जलाओ", "jalao", "ऑन", "on", "बंद", "band", "off", "ऑफ", "करो", "karo", "kar", "chalu", "चालू")) return false;
            run("torch", has(t, OFF) ? "off" : "on", null);
            return true;
        }
        // Camera
        if (has(t, CAMERA) && !question) { run("camera", null, null); return true; }

        // YouTube
        boolean yt = has(t, YT);
        boolean play = has(t, PLAY);
        if (yt || (play && has(t, SONG))) {
            if (question && !play && !has(t, OPEN)) return false;
            if (has(t, SEARCH) && !play) {
                String q = strip(t, YT, SEARCH, FILLER);
                if (q.isEmpty()) run("open_app", "youtube", null);
                else ytSearch(q);
                return true;
            }
            String q = strip(t, YT, PLAY, OPEN, FILLER, new String[]{"वीडियो", "विडियो", "video", "videos"});
            if (q.isEmpty() && !play) { run("open_app", "youtube", null); return true; }
            if (q.isEmpty()) q = "भजन";
            run("youtube", q, null);
            return true;
        }

        // WhatsApp
        if (has(t, WA)) {
            String num = phoneDigits(t);
            String rest = strip(t.replaceAll("\\+?\\d[\\d\\s-]{2,}\\d", " "), WA, OPEN, FILLER,
                    new String[]{"मैसेज", "message", "msg", "संदेश", "भेजो", "भेज", "bhejo", "bhej", "send", "लिखो", "likho", "कि", "ki", "नंबर", "number"});
            boolean msgIntent = has(t, "मैसेज", "message", "msg", "संदेश", "भेजो", "bhejo", "send", "लिखो", "likho");
            if (num.length() >= 10) { run("whatsapp", num, rest); return true; }
            if (msgIntent && !rest.isEmpty()) { shareTo(rest, "com.whatsapp"); return true; }
            if (question && !has(t, OPEN)) return false;
            run("open_app", "whatsapp", null);
            return true;
        }

        // Call
        if (has(t, CALL)) {
            String num = phoneDigits(t);
            if (num.length() >= 3) { run("dial", num, null); return true; }
            if (has(t, CALL_VERB) && !question && words <= 6) {
                String who = strip(t, CALL, CALL_VERB, FILLER);
                dial("");
                host.botSay(who.isEmpty() ? "डायलर खोल दिया है, नंबर मिलाइए।" : "डायलर खोल दिया है — " + who + " का नंबर चुनकर कॉल कीजिए।");
                return true;
            }
        }

        // Maps
        if (has(t, MAP) || has(t, GO)) {
            String place = strip(t, MAP, GO, OPEN, FILLER, new String[]{"दिखाओ", "dikhao", "बताओ", "batao", "show", "का", "की", "के", "ka", "ki", "ke",
                    "तक", "tak", "जाना", "jana", "है", "hai", "kaise", "कैसे", "मुझे", "find", "where", "is", "कहाँ", "कहां", "kahan", "kaha"});
            if (question && !has(t, MAP) && place.isEmpty()) return false;
            if (place.isEmpty()) run("maps", "", null);
            else run("maps", place, null);
            return true;
        }

        // Google search
        if (has(t, SEARCH)) {
            String q = strip(t, SEARCH, FILLER, new String[]{"google", "गूगल", "पर", "about"});
            if (q.isEmpty()) run("open_url", "https://www.google.com", null);
            else run("search", q, null);
            return true;
        }

        // Share
        if (has(t, SHARE) && has(t, "करो", "karo", "kar", "कर", "do", "दो")) {
            String txt = strip(t, SHARE, FILLER, new String[]{"यह", "ये", "ye", "yeh", "this", "text", "टेक्स्ट"});
            run("share", txt.isEmpty() ? "मैं मित्र ऐप इस्तेमाल कर रहा हूँ — आपका AI मित्र!" : txt, null);
            return true;
        }

        // Open app / website
        if (has(t, OPEN)) {
            String target = strip(t, OPEN, FILLER);
            if (target.isEmpty()) return false;
            if (question && words > 4) return false;
            openThing(target);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- AI action tags
    private static final Pattern TAG = Pattern.compile("\\[\\[\\s*ACTION\\s*:\\s*([a-zA-Z_]+)\\s*(?:\\|([^\\]|]*))?(?:\\|([^\\]]*))?\\]\\]");

    /** Executes the first action tag (if any) and returns the text with all tags removed. */
    String runTags(String aiText) {
        Matcher m = TAG.matcher(aiText);
        if (m.find()) {
            final String type = m.group(1).toLowerCase(Locale.ROOT);
            final String a = m.group(2) == null ? "" : m.group(2).trim();
            final String b = m.group(3) == null ? null : m.group(3).trim();
            main.postDelayed(() -> run(type, a, b), 600);
        }
        return aiText.replaceAll("\\[\\[[^\\]]*\\]\\]", "").trim();
    }

    // ---------------------------------------------------------------- execution
    void run(String type, String a, String b) {
        try {
            switch (type) {
                case "youtube": playYoutube(a); break;
                case "open_url": {
                    if (a == null || a.isEmpty()) break;
                    String u = a.startsWith("http") ? a : "https://" + a;
                    host.botSay("खोल रहा हूँ: " + Uri.parse(u).getHost());
                    view(u);
                    break;
                }
                case "search":
                    host.botSay("Google पर खोज रहा हूँ: " + a);
                    view("https://www.google.com/search?q=" + enc(a));
                    break;
                case "open_app": openThing(a); break;
                case "dial":
                    host.botSay("नंबर " + a + " डायल कर रहा हूँ — हरा बटन दबाकर कॉल कीजिए।");
                    dial(a);
                    break;
                case "whatsapp": {
                    String n = digits(a);
                    if (n.length() == 10) n = "91" + n;
                    if (n.length() == 11 && n.startsWith("0")) n = "91" + n.substring(1);
                    String msg = b == null ? "" : b;
                    host.botSay(n.isEmpty() ? "WhatsApp खोल रहा हूँ।" : "WhatsApp पर " + a + " के लिए संदेश तैयार है — भेजने के लिए Send दबाइए।");
                    if (n.isEmpty()) { if (msg.isEmpty()) openThing("whatsapp"); else shareTo(msg, "com.whatsapp"); }
                    else view("https://wa.me/" + n + (msg.isEmpty() ? "" : "?text=" + enc(msg)));
                    break;
                }
                case "alarm": setAlarmFromText(norm(a)); break;
                case "timer": {
                    int secs = Integer.parseInt(digits(a).isEmpty() ? "60" : digits(a));
                    Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, "मित्र टाइमर")
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    host.botSay(secs >= 60 ? (secs / 60) + " मिनट का टाइमर लगा रहा हूँ।" : secs + " सेकंड का टाइमर लगा रहा हूँ।");
                    start(i, "टाइमर वाला ऐप नहीं मिला।");
                    break;
                }
                case "maps":
                    if (a == null || a.isEmpty()) {
                        host.botSay("नक्शा खोल रहा हूँ।");
                        start(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")), null);
                    } else {
                        host.botSay("नक्शे पर दिखा रहा हूँ: " + a);
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(a)));
                        if (!start(i, null)) view("https://www.google.com/maps/search/?api=1&query=" + enc(a));
                    }
                    break;
                case "camera": {
                    host.botSay("कैमरा खोल रहा हूँ 📷");
                    Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                    if (!start(i, null)) start(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), "कैमरा ऐप नहीं मिला।");
                    break;
                }
                case "torch": torch(!"off".equalsIgnoreCase(a)); break;
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

    // ---- YouTube
    void playYoutube(final String query) {
        host.botSay("YouTube पर \"" + query + "\" चला रहा हूँ ▶️");
        exec.execute(() -> {
            String id = null;
            try {
                String html = AiClient.get("https://www.youtube.com/results?search_query=" + enc(query) + "&hl=hi&gl=IN");
                Matcher m = Pattern.compile("\"videoId\"\\s*:\\s*\"([A-Za-z0-9_-]{11})\"").matcher(html);
                if (m.find()) id = m.group(1);
            } catch (Exception ignored) { }
            final String vid = id;
            main.postDelayed(() -> {
                if (vid != null) {
                    Intent app = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + vid));
                    if (!start(app, null)) view("https://www.youtube.com/watch?v=" + vid);
                } else {
                    ytSearchIntent(query);
                }
            }, 800);
        });
    }

    void ytSearch(String q) {
        host.botSay("YouTube पर खोज रहा हूँ: " + q);
        ytSearchIntent(q);
    }

    private void ytSearchIntent(String q) {
        Intent s = new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q);
        if (!start(s, null)) view("https://www.youtube.com/results?search_query=" + enc(q));
    }

    // ---- open app or site
    void openThing(String targetRaw) {
        String target = norm(targetRaw);
        List<String> keys = new ArrayList<>(HI_APP.keySet());
        java.util.Collections.sort(keys, (x, y) -> y.length() - x.length());
        for (String k : keys) {
            if (has(target, k)) target = (" " + target + " ").replace(" " + k + " ", " " + HI_APP.get(k) + " ").trim();
        }
        target = target.replaceAll("\\s+", " ").trim();
        if (target.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+(/\\S*)?")) {
            host.botSay("खोल रहा हूँ: " + target);
            view("https://" + target);
            return;
        }
        if ("settings".equals(target) || "setting".equals(target)) {
            host.botSay("सेटिंग्स खोल रहा हूँ।");
            start(new Intent(Settings.ACTION_SETTINGS), null);
            return;
        }
        if ("camera".equals(target)) { run("camera", null, null); return; }
        if ("whatsapp".equals(target)) {
            Intent w = act.getPackageManager().getLaunchIntentForPackage("com.whatsapp");
            if (w == null) w = act.getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b");
            if (w != null) { host.botSay("WhatsApp खोल रहा हूँ।"); start(w, null); return; }
        }
        if ("youtube".equals(target)) {
            Intent y = act.getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
            if (y != null) { host.botSay("YouTube खोल रहा हूँ।"); start(y, null); return; }
        }
        Intent app = findApp(target);
        if (app == null && "gallery".equals(target)) app = findApp("photos");
        if (app == null && "photos".equals(target)) app = findApp("gallery");
        if (app == null && "gpay".equals(target)) app = findApp("google pay");
        if (app == null && "phone".equals(target)) { dial(""); host.botSay("डायलर खोल दिया है।"); return; }
        if (app != null) {
            host.botSay("\"" + app.getStringExtra("mitra_label") + "\" खोल रहा हूँ।");
            app.removeExtra("mitra_label");
            start(app, null);
            return;
        }
        String site = SITES.get(target);
        if (site != null) { host.botSay("खोल रहा हूँ: " + Uri.parse(site).getHost()); view(site); return; }
        host.botSay("\"" + targetRaw + "\" नाम का ऐप नहीं मिला, Google पर खोज रहा हूँ।");
        view("https://www.google.com/search?q=" + enc(targetRaw));
    }

    private Intent findApp(String target) {
        if (target.isEmpty()) return null;
        PackageManager pm = act.getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
        String tc = target.replace(" ", "");
        ResolveInfo best = null;
        int bestScore = 0;
        String bestLabel = null;
        for (ResolveInfo r : list) {
            if (r.activityInfo == null || act.getPackageName().equals(r.activityInfo.packageName)) continue;
            String label = String.valueOf(r.loadLabel(pm));
            String l = label.toLowerCase(Locale.ROOT);
            String lc = l.replace(" ", "");
            String pkg = r.activityInfo.packageName.toLowerCase(Locale.ROOT);
            int score = 0;
            if (lc.equals(tc)) score = 100;
            else if (lc.startsWith(tc)) score = 80;
            else if (tc.length() >= 3 && lc.contains(tc)) score = 60;
            else if (lc.length() >= 3 && tc.contains(lc)) score = 50;
            else if (tc.length() >= 4 && pkg.contains(tc)) score = 40;
            if (score > bestScore) { bestScore = score; best = r; bestLabel = label; }
        }
        if (best == null) return null;
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(best.activityInfo.packageName, best.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("mitra_label", bestLabel);
        return i;
    }

    // ---- alarm
    private static final Map<String, Integer> NUM = new HashMap<>();
    static {
        String[][] n = {{"एक", "1"}, {"दो", "2"}, {"तीन", "3"}, {"चार", "4"}, {"पांच", "5"}, {"पाँच", "5"}, {"छह", "6"}, {"छः", "6"}, {"छे", "6"},
                {"सात", "7"}, {"आठ", "8"}, {"नौ", "9"}, {"दस", "10"}, {"ग्यारह", "11"}, {"बारह", "12"}, {"ek", "1"}, {"teen", "3"},
                {"char", "4"}, {"chaar", "4"}, {"paanch", "5"}, {"panch", "5"}, {"chhe", "6"}, {"che", "6"}, {"saat", "7"}, {"aath", "8"}, {"nau", "9"},
                {"das", "10"}, {"gyarah", "11"}, {"barah", "12"}, {"baarah", "12"}};
        for (String[] p : n) NUM.put(p[0], Integer.parseInt(p[1]));
    }

    void setAlarmFromText(String t) {
        String s = " " + t + " ";
        for (Map.Entry<String, Integer> e : NUM.entrySet()) s = s.replace(" " + e.getKey() + " ", " " + e.getValue() + " ");
        Matcher m = Pattern.compile("(\\d{1,2})(?:\\s*[:.]\\s*(\\d{2}))?").matcher(s);
        if (!m.find()) {
            host.botSay("अलार्म ऐप खोल रहा हूँ — समय चुन लीजिए। अगली बार बोलिए, जैसे \"सुबह 6 बजे का अलार्म लगाओ\"।");
            start(new Intent(AlarmClock.ACTION_SHOW_ALARMS), "अलार्म ऐप नहीं मिला।");
            return;
        }
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (has(t, "साढ़े", "साढे", "sadhe", "saadhe")) min = 30;
        else if (has(t, "सवा", "sawa", "sava")) min = 15;
        else if (has(t, "पौने", "paune", "pone")) { min = 45; h = h - 1; if (h <= 0) h += 12; }
        boolean pm = has(t, "शाम", "sham", "shaam", "evening", "pm", "p.m.", "दोपहर", "dopahar", "dopehar", "afternoon");
        boolean night = has(t, "रात", "raat", "rat", "night");
        boolean am = has(t, "सुबह", "subah", "subha", "morning", "am", "a.m.", "सवेरे", "savere");
        if (h > 23 || min > 59) { host.botSay("समय समझ नहीं आया, फिर से बोलिए।"); return; }
        if (pm && h < 12) h += 12;
        if (night) { if (h >= 6 && h < 12) h += 12; else if (h == 12) h = 0; }
        if (am && h == 12) h = 0;
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, min)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "मित्र अलार्म")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String part = h < 4 ? "रात" : h < 12 ? "सुबह" : h < 16 ? "दोपहर" : h < 20 ? "शाम" : "रात";
        host.botSay(part + " " + h12 + ":" + (min < 10 ? "0" + min : String.valueOf(min)) + " बजे का अलार्म लगा रहा हूँ ⏰");
        start(i, "अलार्म ऐप नहीं मिला।");
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

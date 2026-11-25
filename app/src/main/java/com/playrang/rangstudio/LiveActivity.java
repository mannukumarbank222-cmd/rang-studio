package com.playrang.rangstudio;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "🎙️ Live बात": a full-screen voice conversation with a big animated orb, like ChatGPT's voice mode.
 * With a Gemini key it streams audio both ways over the Gemini Live API (barge-in, tools, Google Search).
 * Without a key (or if Live cannot start) it runs a quick turn-based loop: listen → AI → speak → listen again.
 */
public class LiveActivity extends Activity implements ActionHandler.Host, LiveSession.Listener {
    static final String EXTRA_CMD = "cmd";
    private static final int REQ_MIC = 21;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newFixedThreadPool(2);
    private SharedPreferences prefs;
    private ActionHandler actions;
    private String firstCmd;
    private boolean destroyed, leftForAction, started;

    // UI
    private OrbView orb;
    private TextView status, heard, reply, muteBtn, chip;
    private LinearLayout choiceBox;
    private HorizontalScrollView choiceScroll;

    // Live
    private LiveSession session;
    private boolean live;
    private Capture capture;
    private Intent pendingLaunch;
    private Bundle pendingOptions;
    private long responseAt;
    private String lastUser = "", lastModel = "";

    // Lite (turn-based) mode
    private TextToSpeech tts;
    private boolean ttsReady, liteMuted, liteBusy, liteListening;
    private SpeechRecognizer sr;
    private String lastUtt;
    private int utter;
    private final ListenBackoff backoff = new ListenBackoff();
    private SilentMic quiet;
    private final List<String> pendingSpeech = new ArrayList<>();

    private Lang lang() { return Lang.get(prefs.getString("lang", "auto")); }
    private int dp(float v) { return Ui.dp(this, v); }
    private String key() { return prefs.getString("gemini_key", "").trim(); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("mitra", MODE_PRIVATE);
        WakeService.pause(this, "voice", 0);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(WakeService.NOTIF_WAKE); } catch (Exception ignored) { }
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        actions = new ActionHandler(this, this, exec);
        firstCmd = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_CMD);
        buildUi();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("माइक की अनुमति दीजिए");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } else {
            begin();
        }
    }

    private void begin() {
        if (started) return;
        started = true;
        if (key().length() > 10) startLive();
        else startLite("मुफ़्त मोड • Live आवाज़ के लिए ⚙️ में Gemini key डालें");
    }

    // ------------------------------------------------------------------ UI
    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0F0B09"));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(20), dp(28), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("मित्र • Live");
        title.setTextColor(Color.parseColor("#FFE4C2"));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        chip = new TextView(this);
        chip.setTextColor(Color.parseColor("#C9B8AA"));
        chip.setTextSize(12);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(4), dp(12), dp(4));
        chip.setBackground(Ui.round(Color.parseColor("#221A15"), dp(12)));
        chip.setText("जुड़ रहा हूँ…");
        chip.setOnClickListener(v -> { if (!live) startActivity(new Intent(this, SettingsActivity.class)); });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(8);
        col.addView(chip, clp);

        orb = new OrbView(this);
        orb.setOnClickListener(v -> onOrbTap());
        col.addView(orb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(20);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setGravity(Gravity.CENTER);
        status.setText("जुड़ रहा हूँ…");
        col.addView(status);

        heard = new TextView(this);
        heard.setTextColor(Color.parseColor("#A8978A"));
        heard.setTextSize(15);
        heard.setGravity(Gravity.CENTER);
        heard.setMaxLines(2);
        heard.setEllipsize(android.text.TextUtils.TruncateAt.START);
        heard.setPadding(0, dp(10), 0, 0);
        col.addView(heard);

        reply = new TextView(this);
        reply.setTextColor(Color.parseColor("#F5E6D8"));
        reply.setTextSize(17);
        reply.setGravity(Gravity.CENTER);
        reply.setMaxLines(4);
        reply.setEllipsize(android.text.TextUtils.TruncateAt.START);
        reply.setLineSpacing(dp(2), 1f);
        reply.setPadding(0, dp(6), 0, dp(6));
        col.addView(reply);

        choiceScroll = new HorizontalScrollView(this);
        choiceScroll.setHorizontalScrollBarEnabled(false);
        choiceBox = new LinearLayout(this);
        choiceBox.setOrientation(LinearLayout.HORIZONTAL);
        choiceScroll.addView(choiceBox);
        col.addView(choiceScroll);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(18), 0, 0);
        muteBtn = circle("🎤", Color.parseColor("#2A211C"));
        muteBtn.setContentDescription("माइक बंद/चालू");
        muteBtn.setOnClickListener(v -> toggleMute());
        TextView close = circle("✕", Color.parseColor("#C8381E"));
        close.setContentDescription("बातचीत बंद करें");
        close.setOnClickListener(v -> finish());
        row.addView(muteBtn, new LinearLayout.LayoutParams(dp(68), dp(68)));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(68), dp(68));
        gap.leftMargin = dp(56);
        row.addView(close, gap);
        col.addView(row);

        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(Color.parseColor("#0F0B09"));
        if (Build.VERSION.SDK_INT >= 21) getWindow().setNavigationBarColor(Color.parseColor("#0F0B09"));
    }

    private TextView circle(String t, int bg) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(24);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Ui.round(bg, dp(34)));
        return v;
    }

    private void setStatus(int s) {
        switch (s) {
            case LiveSession.S_CONNECTING: status.setText("जुड़ रहा हूँ…"); orb.setMode(OrbView.CONNECT); break;
            case LiveSession.S_SPEAKING: status.setText("मित्र बोल रहा है…"); orb.setMode(OrbView.SPEAK); break;
            case LiveSession.S_MUTED: status.setText("माइक बंद है"); orb.setMode(OrbView.MUTED); break;
            default: status.setText("सुन रहा हूँ… बोलिए"); orb.setMode(OrbView.LISTEN); break;
        }
    }

    private void showChips(List<String> labels, int generation) {
        choiceBox.removeAllViews();
        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            TextView o = new TextView(this);
            o.setText(labels.get(i));
            o.setTextColor(Color.WHITE);
            o.setTextSize(15);
            o.setPadding(dp(14), dp(8), dp(14), dp(8));
            o.setBackground(Ui.stroke(Color.parseColor("#2A211C"), Ui.SAFFRON, dp(1), dp(18)));
            o.setOnClickListener(v -> { choiceBox.removeAllViews(); actions.choose(generation, idx); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            lp.topMargin = dp(8);
            choiceBox.addView(o, lp);
        }
    }

    private void toggleMute() {
        if (live && session != null) {
            boolean m = !session.isMuted();
            session.setMuted(m);
            muteBtn.setText(m ? "🔇" : "🎤");
            muteBtn.setBackground(Ui.round(m ? Color.parseColor("#5A4A40") : Color.parseColor("#2A211C"), dp(34)));
        } else {
            liteMuted = !liteMuted;
            muteBtn.setText(liteMuted ? "🔇" : "🎤");
            muteBtn.setBackground(Ui.round(liteMuted ? Color.parseColor("#5A4A40") : Color.parseColor("#2A211C"), dp(34)));
            if (liteMuted) { stopListening(); orb.setMode(OrbView.MUTED); status.setText("माइक बंद है"); }
            else if (!liteBusy && (tts == null || !tts.isSpeaking())) { backoff.reset(); listen(); }
        }
    }

    private void onOrbTap() {
        if (live) {
            if (session != null) session.stopSpeaking(); // tap = "रुको", like barge-in
        } else if (!liteListening && !liteMuted) {
            if (tts != null) tts.stop();
            backoff.reset();
            listen();
        }
    }

    // ------------------------------------------------------------------ Live mode
    private void startLive() {
        live = true;
        chip.setText("Gemini Live से जुड़ रहा हूँ…");
        session = new LiveSession(this, key(), liveSystem(), prefs.getString("live_voice", "Puck"), this);
        session.start(firstCmd);
    }

    private String liveSystem() {
        StringBuilder s = new StringBuilder();
        s.append("तुम 'मित्र' (Mitra) हो — PlayRang Technologies का बनाया एक गर्मजोशी भरा AI दोस्त, जो फ़ोन पर यूज़र से live आवाज़ में बात करता है, बिल्कुल एक समझदार भारतीय दोस्त की तरह।\n")
         .append("भाषा: यूज़र जिस भाषा/बोली में बोले उसी में जवाब दो — हिंदी, Hinglish, English, भोजपुरी, मैथिली, पंजाबी, मराठी, बंगाली, तमिल, तेलुगु आदि। ")
         .append("शुरुआत में और शक हो तो आसान, बोलचाल वाली हिंदी (Hinglish भी ठीक) बोलो। भारी शुद्ध हिंदी नहीं, रोज़मर्रा की भाषा।\n")
         .append("बोलने का तरीका: जवाब छोटे और बोलने लायक रखो — आम तौर पर 1 से 3 वाक्य; यूज़र विस्तार माँगे तभी लंबा। ")
         .append("कोई लिस्ट, markdown, URL या इमोजी मत बोलो। स्वाभाविक, प्यार से, थोड़े हास्य के साथ बोलो। यूज़र बीच में बोले तो तुरंत रुककर उसकी सुनो।\n")
         .append("हर सवाल का जवाब दो — पढ़ाई, विज्ञान, इतिहास, खेल, फ़िल्में, धर्म, गणित, सेहत, पैसा, रिश्ते, करियर, खेती, सरकारी योजनाएँ, कुछ भी। ")
         .append("'मुझे नहीं पता' कहकर मत छोड़ो; ताज़ा जानकारी (ख़बर, मौसम, क्रिकेट स्कोर, दाम, आज की घटनाएँ) के लिए Google Search इस्तेमाल करो अगर उपलब्ध हो। ")
         .append("गंभीर मेडिकल/कानूनी बात में विशेषज्ञ से मिलने की सलाह भी दो।\n")
         .append("फ़ोन के काम खुद करो: कॉल, WhatsApp, SMS, YouTube पर गाना, ऐप खोलना, अलार्म, टाइमर, टॉर्च, नक्शा, कैमरा, Google खोज — इनके लिए दिए गए functions तुरंत बुलाओ। ")
         .append("फ़ोन अपने कॉन्टैक्ट्स खुद ढूँढ लेता है, इसलिए कभी मत कहो 'मुझे नंबर नहीं पता' या 'मैं कॉल नहीं कर सकता' — नाम यूज़र के ही शब्दों में दो ('को/ko' हटाकर)। ")
         .append("function का जवाब आने पर एक छोटा वाक्य बोलो, जैसे 'ठीक है, सोनू को कॉल लगा रहा हूँ।' WhatsApp/SMS में Send यूज़र खुद दबाता है। ")
         .append("समय/तारीख़ के लिए current_time बुलाओ। काम पक्का न हो तो एक छोटा सवाल पूछ लो।\n")
         .append("आज की तारीख़: ").append(LocalInfo.todayText()).append("।\n");
        String li = lang().aiInstruction();
        if (li != null && li.length() > 0) s.append(li).append("\n");
        List<String[]> h = Convo.snapshot();
        if (!h.isEmpty()) {
            s.append("पिछली बातचीत (संदर्भ के लिए):\n");
            for (int i = Math.max(0, h.size() - 6); i < h.size(); i++) {
                String t = AiClient.lastUser(java.util.Collections.singletonList(h.get(i)));
                if (!"user".equals(h.get(i)[0])) t = h.get(i)[1];
                if (t.length() > 300) t = t.substring(0, 300);
                s.append("user".equals(h.get(i)[0]) ? "यूज़र: " : "मित्र: ").append(t.replace('\n', ' ')).append("\n");
            }
        }
        return s.toString();
    }

    @Override public void onStatus(int state) {
        if (destroyed) return;
        setStatus(state);
        if (state == LiveSession.S_LISTENING) maybeLaunchPending();
    }

    @Override public void onLevel(float level) { orb.setLevel(level); }

    @Override public void onUserText(String text) {
        lastUser = text;
        heard.setText("आप: " + text);
        choiceBox.removeAllViews();
    }

    @Override public void onModelText(String text) { lastModel = text; reply.setText(text); }

    @Override public void onTurnDone() {
        if (lastUser.trim().length() > 0) Convo.add("user", lastUser.trim());
        if (lastModel.trim().length() > 0) Convo.add("assistant", lastModel.trim());
        lastUser = "";
        lastModel = "";
        maybeLaunchPending();
    }

    @Override public void onConnected(String model, boolean search) {
        chip.setText("Gemini Live" + (search ? " • Google खोज" : "") + " • बीच में बोलकर रोक सकते हैं");
    }

    @Override public void onFatal(String message, boolean keyProblem) {
        if (destroyed || !live) return;
        if (session != null) session.stop();
        session = null;
        live = false;
        reply.setText(message);
        startLite(keyProblem ? "Gemini key नहीं चली • ⚙️ में जाँचें • अभी सामान्य बातचीत" : "Live नहीं चला • सामान्य बातचीत मोड");
    }

    // ---- tools
    private static final class Capture {
        final String id, name;
        final List<String> msgs = new ArrayList<>();
        Intent intent; Bundle options;
        List<String> choices; int gen;
        final long t0 = System.currentTimeMillis();
        Capture(String id, String name) { this.id = id; this.name = name; }
    }

    @Override public void onToolCall(String id, String name, JSONObject args) {
        if (session == null) return;
        try {
            if ("current_time".equals(name)) {
                session.sendToolResponse(id, name, new JSONObject().put("result", LocalInfo.timeReply() + " " + LocalInfo.dateReply()));
                return;
            }
            Map<String, String> m = new HashMap<>();
            Iterator<String> it = args.keys();
            while (it.hasNext()) { String k = it.next(); m.put(k, String.valueOf(args.opt(k))); }
            LiveTools.Act act = LiveTools.map(name, m);
            if (act == null) { session.sendToolResponse(id, name, new JSONObject().put("error", "यह function फ़ोन पर उपलब्ध नहीं है")); return; }
            if (capture != null) finishCapture(); // an older call still waiting: answer it now
            final Capture c = new Capture(id, name);
            capture = c;
            choiceBox.removeAllViews();
            actions.execute(act.type, act.a, act.b);
            pollCapture(c, act.type.contains("contact") || act.type.startsWith("call") || act.type.startsWith("sms") || act.type.startsWith("whatsapp"));
        } catch (Exception e) {
            try { session.sendToolResponse(id, name, new JSONObject().put("error", "काम नहीं हो पाया")); } catch (Exception ignored) { }
        }
    }

    private void pollCapture(final Capture c, final boolean slow) {
        if (capture != c) return;
        long age = System.currentTimeMillis() - c.t0;
        boolean done = c.intent != null || c.choices != null || age > 2600 || (!slow && age > 150) || (slow && !c.msgs.isEmpty() && age > 1200);
        if (done) finishCapture();
        else main.postDelayed(() -> pollCapture(c, slow), 120);
    }

    private void finishCapture() {
        Capture c = capture;
        capture = null;
        if (c == null || session == null) return;
        try {
            JSONObject r = new JSONObject();
            String said = android.text.TextUtils.join(" ", c.msgs).trim();
            if (c.choices != null) {
                r.put("result", "इस नाम से कई कॉन्टैक्ट मिले: " + android.text.TextUtils.join(", ", c.choices)
                        + "। यूज़र से छोटा सा पूछो कौन सा, फिर वही पूरा नाम देकर यह function दोबारा बुलाओ। (स्क्रीन पर भी विकल्प दिख रहे हैं)");
                showChips(c.choices, c.gen);
            } else if (c.intent != null) {
                r.put("result", (said.isEmpty() ? "काम शुरू हो गया।" : said) + " ऐप अभी खुलने वाला है — यूज़र को एक बहुत छोटा वाक्य बोलकर बताओ।");
                pendingLaunch = c.intent;
                pendingOptions = c.options;
                main.postDelayed(this::launchPending, 5000);
            } else {
                r.put("result", said.isEmpty() ? "हो गया।" : said);
            }
            responseAt = System.currentTimeMillis();
            session.sendToolResponse(c.id, c.name, r);
            if (!said.isEmpty()) reply.setText(MainActivity.clean(said));
        } catch (Exception ignored) { }
    }

    private void maybeLaunchPending() {
        if (pendingLaunch != null && System.currentTimeMillis() - responseAt > 900) main.postDelayed(this::launchPending, 350);
    }

    private void launchPending() {
        Intent i = pendingLaunch;
        Bundle o = pendingOptions;
        pendingLaunch = null;
        pendingOptions = null;
        if (i == null || destroyed) return;
        if (session != null) session.stopSpeaking();
        try { startActivity(i, o); } catch (Exception e) { reply.setText("यह ऐप नहीं खुल पाया।"); }
    }

    // ------------------------------------------------------------------ ActionHandler.Host
    @Override
    public void botSay(String text) {
        runOnUiThread(() -> {
            if (capture != null) { capture.msgs.add(text); return; }
            reply.setText(MainActivity.clean(text));
            if (!live) speakThenListen(text);
        });
    }

    @Override
    public void showChoices(String question, List<String> labels, int generation) {
        runOnUiThread(() -> {
            if (capture != null) { capture.choices = labels; capture.gen = generation; capture.msgs.add(question); return; }
            reply.setText(question);
            showChips(labels, generation);
            if (!live) speakThenListen(question);
        });
    }

    // ------------------------------------------------------------------ Lite (turn-based) mode
    private void startLite(String note) {
        live = false;
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        chip.setText(note);
        orb.setMode(OrbView.LISTEN);
        if (tts == null) initTts();
        String cmd = firstCmd;
        firstCmd = null;
        if (cmd != null && cmd.trim().length() > 0) process(cmd.trim());
        else speakThenListen("जी, बोलिए।");
    }

    private void initTts() {
        tts = new TextToSpeech(this, st -> {
            if (st != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(Locale.forLanguageTag(lang().tts));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(new Locale("hi", "IN"));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { main.post(() -> { if (!live) { orb.setMode(OrbView.SPEAK); status.setText("मित्र बोल रहा है…"); } }); }
                @Override public void onDone(String id) { main.post(() -> spoke(id)); }
                @Override public void onError(String id) { main.post(() -> spoke(id)); }
            });
            ttsReady = true;
            List<String> p = new ArrayList<>(pendingSpeech);
            pendingSpeech.clear();
            for (String s : p) say(s);
        });
    }

    private static String forSpeech(String text) {
        return text.replaceAll("https?://\\S+", "")
                .replaceAll("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}\\x{2B00}-\\x{2BFF}]", "")
                .replace("*", "").replace("#", "").replace("•", "").replaceAll("\\(स्रोत: [^)]*\\)", "").trim();
    }

    /** Queues one piece of speech (QUEUE_ADD); listening resumes after the last one. */
    private void say(String text) {
        String s = forSpeech(text);
        if (s.isEmpty()) return;
        if (!ttsReady || tts == null) { pendingSpeech.add(s); return; }
        try {
            int r = tts.setLanguage(Locale.forLanguageTag(Lang.ttsForText(s, lang())));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(new Locale("hi", "IN"));
        } catch (Exception ignored) { }
        lastUtt = "l" + (utter++);
        Bundle p = new Bundle();
        p.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        tts.speak(s, TextToSpeech.QUEUE_ADD, p, lastUtt);
    }

    private void speakThenListen(String text) {
        stopListening();
        if (tts != null && ttsReady) tts.stop();
        if (forSpeech(text).isEmpty()) { listen(); return; }
        say(text);
    }

    private void spoke(String id) {
        if (live || destroyed || id == null || !id.equals(lastUtt) || liteBusy) return;
        if (tts != null && tts.isSpeaking()) return;
        listen();
    }

    private void process(String text) {
        heard.setText("आप: " + text);
        choiceBox.removeAllViews();
        if (Convo.isStop(text)) { stopListening(); say("ठीक है, फिर मिलते हैं!"); main.postDelayed(this::finish, 1600); return; }
        try {
            if (actions.handlePending(text)) return;
            if (actions.tryLocal(text)) return;
        } catch (Exception ignored) { }
        if (liteBusy) return;
        liteBusy = true;
        status.setText("सोच रहा हूँ…");
        orb.setMode(OrbView.CONNECT);
        reply.setText("…");
        Convo.add("user", text);
        final List<String[]> snap = Convo.snapshot();
        if (CommandParser.looksLikeCommand(text)) {
            snap.set(snap.size() - 1, new String[]{"user", text
                    + "\n\n(ऐप का नोट: यह फ़ोन पर कोई काम कराने का आदेश लगता है। अगर ऐसा है तो एक छोटा वाक्य लिखो और आख़िर में ठीक एक सही [[ACTION:...]] टैग लगाओ।)"});
        }
        final String k = key(), model = prefs.getString("gemini_model", "gemini-flash-latest");
        final SentenceBuffer buf = new SentenceBuffer();
        final boolean[] spokeAny = {false};
        exec.execute(() -> {
            final AiClient.Result r = AiClient.askStream(snap, k, model, lang().aiInstruction(), 8, delta -> {
                final List<String> ready = buf.push(delta);
                final String vis = buf.visible();
                main.post(() -> {
                    if (destroyed) return;
                    reply.setText(MainActivity.clean(vis));
                    for (String s : ready) { spokeAny[0] = true; say(s); }
                });
            });
            main.post(() -> {
                if (destroyed) return;
                String full = r.text == null || r.text.trim().isEmpty() ? (buf.text().isEmpty() ? AiClient.sorry() : buf.text()) : r.text;
                String shown = MainActivity.clean(actions.runTags(full));
                if (shown.isEmpty()) shown = "ठीक है! 👍";
                Convo.add("assistant", shown);
                reply.setText(shown);
                if (spokeAny[0]) { String rest = buf.flush(); if (!rest.isEmpty()) say(rest); }
                else say(shown);
                liteBusy = false;
                if (tts == null || !tts.isSpeaking()) main.postDelayed(() -> { if (tts == null || !tts.isSpeaking()) spoke(lastUtt); }, 400);
            });
        });
    }

    private void listen() {
        if (live || destroyed || leftForAction || liteMuted || liteBusy) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            if (sr == null) {
                sr = SpeechRecognizer.createSpeechRecognizer(this);
                sr.setRecognitionListener(new L());
            }
            Intent i = SilentMic.intent(lang().sr, 1);
            liteListening = true;
            status.setText("सुन रहा हूँ… बोलिए");
            orb.setMode(OrbView.LISTEN);
            if (quiet == null) quiet = new SilentMic(this);
            quiet.mute(tts != null && tts.isSpeaking());
            sr.startListening(i);
        } catch (Exception e) {
            liteListening = false;
            if (quiet != null) quiet.unmuteNow();
        }
    }

    private void stopListening() {
        boolean was = liteListening;
        liteListening = false;
        try { if (sr != null) sr.cancel(); } catch (Exception ignored) { }
        if (was && quiet != null) quiet.unmuteSoon();
    }

    /** Heard nothing: wait 1 s, then 3 s, then stop and wait for an orb tap (no endless restarts / beeps). */
    private void emptyTry() {
        long d = backoff.onEmpty();
        if (d == ListenBackoff.STOP) {
            status.setText("बोलने के लिए orb दबाइए");
            orb.setMode(OrbView.IDLE);
            return;
        }
        status.setText("…");
        main.postDelayed(LiveActivity.this::listen, d);
    }

    private class L implements RecognitionListener {
        public void onReadyForSpeech(Bundle p) { }
        public void onBeginningOfSpeech() { }
        public void onRmsChanged(float v) { orb.setLevel(Math.max(0f, (v + 2f) / 12f)); }
        public void onBufferReceived(byte[] b) { }
        public void onEndOfSpeech() { }
        public void onEvent(int t, Bundle p) { }
        public void onPartialResults(Bundle b) {
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty() && r.get(0).length() > 0) heard.setText("आप: " + r.get(0));
        }
        public void onError(int error) {
            boolean was = liteListening;
            liteListening = false;
            if (quiet != null) quiet.unmuteSoon();
            if (!was || live || destroyed) return;
            if (SilentMic.retryOnline(error)) { main.postDelayed(LiveActivity.this::listen, 300); return; }
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                emptyTry();
                return;
            }
            if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) reply.setText("इंटरनेट जाँचिए।");
            if ((error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) && !backoff.stopped()) {
                long d = backoff.onEmpty();
                if (d != ListenBackoff.STOP) { main.postDelayed(LiveActivity.this::listen, d); return; }
            }
            status.setText("बोलने के लिए orb दबाइए");
            orb.setMode(OrbView.IDLE);
        }
        public void onResults(Bundle b) {
            liteListening = false;
            if (quiet != null) quiet.unmuteSoon();
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String t = r == null || r.isEmpty() ? "" : r.get(0).trim();
            if (t.isEmpty()) { emptyTry(); return; }
            backoff.reset();
            String w = WakeWord.split(t);
            if (w != null && w.isEmpty()) { speakThenListen("जी, बोलिए"); return; }
            if (w != null) t = w;
            process(t);
        }
    }

    // ------------------------------------------------------------------ starting other apps
    @Override
    public void startActivity(Intent intent, Bundle options) {
        if (capture != null && intent.resolveActivity(getPackageManager()) != null) {
            // Live: hold the app until Mitra has said its short confirmation
            capture.intent = intent;
            capture.options = options;
            return;
        }
        boolean internal = intent.getComponent() != null && getPackageName().equals(intent.getComponent().getPackageName());
        if (!internal) leftForAction = true;
        stopListening();
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean locked = km != null && km.isKeyguardLocked();
        boolean call = Intent.ACTION_CALL.equals(intent.getAction());
        if (locked && !call && Build.VERSION.SDK_INT >= 26 && intent.resolveActivity(getPackageManager()) != null) {
            final Intent in = intent;
            final Bundle op = options;
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { try { LiveActivity.super.startActivity(in, op); } catch (Exception ignored) { } }
            });
            return;
        }
        super.startActivity(intent, options);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_MIC) {
            if (ok) begin();
            else { status.setText("माइक की अनुमति के बिना Live बात नहीं हो सकती"); orb.setMode(OrbView.MUTED); }
            return;
        }
        actions.onPermissionResult(code, ok);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (started && !live && !liteListening && !liteBusy && tts != null && !tts.isSpeaking() && !leftForAction && !backoff.stopped()) main.postDelayed(this::listen, 500);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // the mic may only be used while this screen is visible; another app opened = conversation over
        if (!isChangingConfigurations() && (leftForAction || live)) finish();
        else stopListening();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (quiet != null) quiet.unmuteNow();
        if (session != null) { session.stop(); session = null; }
        if (sr != null) { try { sr.destroy(); } catch (Exception ignored) { } sr = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        exec.shutdown();
        WakeService.resume(this, "voice");
        super.onDestroy();
    }
}

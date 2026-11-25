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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small voice screen opened by the wake word (works over the lock screen). Says "जी, बोलिए", listens, answers by voice,
 * then listens ~6 s more for a follow-up.
 */
public class VoiceActivity extends Activity implements ActionHandler.Host {
    static final String EXTRA_CMD = "cmd";
    private static final long FOLLOW_UP_MS = 6000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newFixedThreadPool(2);
    private SharedPreferences prefs;
    private ActionHandler actions;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private Runnable pendingAfter;
    private Runnable afterSpeak;
    private String lastUtt;
    private int utter;
    private SpeechRecognizer sr;
    private boolean listening, busy, leftForAction, destroyed;
    private final ListenBackoff backoff = new ListenBackoff();
    private SilentMic quiet;
    private long lastSilenceMs = 8000;
    private boolean userSpoke;
    private final SilentMic.EarlyStop early = new SilentMic.EarlyStop();
    private String lastUserText = "";

    private TextView status, heard, answer, micBtn;
    private LinearLayout choiceBox;

    private Lang lang() { return Lang.get(prefs.getString("lang", "auto")); }
    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("mitra", MODE_PRIVATE);
        WakeService.pause(this, "voice", 0);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(WakeService.NOTIF_WAKE); } catch (Exception ignored) { }
        actions = new ActionHandler(this, this, exec);
        buildUi();
        initTts();
        final boolean gem = prefs.getString("gemini_key", "").trim().length() > 10;
        try { exec.execute(() -> AiClient.warmUp(gem)); } catch (Exception ignored) { }
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        stopListening();
        handle(i);
    }

    private void handle(Intent i) {
        String cmd = i == null ? null : i.getStringExtra(EXTRA_CMD);
        leftForAction = false;
        backoff.reset();
        if (cmd != null && cmd.trim().length() > 0) process(cmd.trim());
        else {
            // no beep (users asked for silent listening): Mitra just says "जी, बोलिए"
            status.setText("जी, बोलिए…");
            speak("जी, बोलिए", () -> listen(8000));
        }
    }

    // ------------------------------------------------------------------ UI
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.BOTTOM);
        root.setBackgroundColor(Color.parseColor("#99000000"));
        root.setOnClickListener(v -> finish());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(Ui.BG, dp(24)));
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setClickable(true);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView av = new ImageView(this);
        av.setImageResource(R.mipmap.ic_launcher_round);
        top.addView(av, new LinearLayout.LayoutParams(dp(40), dp(40)));
        status = new TextView(this);
        status.setTextSize(18);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setTextColor(Ui.SAFFRON_DARK);
        status.setPadding(dp(12), 0, 0, 0);
        top.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(22);
        close.setPadding(dp(12), dp(4), dp(4), dp(4));
        close.setTextColor(Ui.MUTED);
        close.setContentDescription("बंद करें");
        close.setOnClickListener(v -> finish());
        top.addView(close);
        card.addView(top);

        heard = new TextView(this);
        heard.setTextSize(15);
        heard.setTextColor(Ui.MUTED);
        heard.setPadding(0, dp(10), 0, 0);
        card.addView(heard);

        ScrollView sv = new ScrollView(this);
        answer = new TextView(this);
        answer.setTextSize(18);
        answer.setTextColor(Ui.TEXT);
        answer.setLineSpacing(dp(2), 1f);
        answer.setPadding(0, dp(8), 0, dp(8));
        sv.addView(answer);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        choiceBox = new LinearLayout(this);
        choiceBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(choiceBox);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(10), 0, 0);
        micBtn = pill("🎤 बोलिए", true);
        micBtn.setOnClickListener(v -> { if (listening) stopListening(); else { if (tts != null) tts.stop(); backoff.reset(); listen(8000); } });
        TextView chat = pill("💬 चैट खोलें", false);
        chat.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        row.addView(micBtn);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.leftMargin = dp(12);
        row.addView(chat, gap);
        card.addView(row);

        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.setMargins(dp(10), 0, dp(10), dp(16));
        root.addView(card, cl);
        setContentView(root);
    }

    private TextView pill(String t, boolean primary) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(16);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(primary ? Color.WHITE : Ui.SAFFRON_DARK);
        v.setBackground(primary ? Ui.round(Ui.SAFFRON, dp(22)) : Ui.stroke(Color.WHITE, Ui.SAFFRON, dp(1), dp(22)));
        v.setPadding(dp(18), dp(10), dp(18), dp(10));
        return v;
    }

    // ------------------------------------------------------------------ Host
    @Override
    public void botSay(String text) {
        runOnUiThread(() -> {
            answer.setText(MainActivity.clean(text));
            status.setText("मित्र");
            speak(text, this::followUp);
        });
    }

    @Override
    public void showChoices(String question, List<String> labels, int generation) {
        runOnUiThread(() -> {
            answer.setText(question);
            choiceBox.removeAllViews();
            for (int i = 0; i < labels.size(); i++) {
                final int idx = i;
                TextView o = pill((labels.size() > 1 ? (i + 1) + ".  " : "") + labels.get(i), false);
                o.setOnClickListener(v -> { choiceBox.removeAllViews(); stopListening(); actions.choose(generation, idx); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                choiceBox.addView(o, lp);
            }
            speak(question, () -> listen(8000));
        });
    }

    // ------------------------------------------------------------------ processing
    private void process(String text) {
        heard.setText("“" + text + "”");
        userSpoke = true;
        lastUserText = text;
        if (quiet != null) quiet.unmuteNow();
        String learned = Lang.learn(prefs.getString("auto_sr", "hi-IN"), text);
        if (learned != null) prefs.edit().putString("auto_sr", learned).apply();
        choiceBox.removeAllViews();
        if (Convo.isStop(text)) { speak("ठीक है।", this::finish); return; }
        try {
            if (actions.handlePending(text)) return;
            if (actions.tryLocal(text)) return;
        } catch (Exception ignored) { }
        if (busy) return;
        busy = true;
        status.setText("सोच रहा हूँ…");
        answer.setText("…");
        Convo.add("user", text);
        final List<String[]> snap = Convo.snapshot();
        if (CommandParser.looksLikeCommand(text)) {
            snap.set(snap.size() - 1, new String[]{"user", text
                    + "\n\n(ऐप का नोट: यह फ़ोन पर कोई काम कराने का आदेश लगता है। अगर ऐसा है तो एक छोटा वाक्य लिखो और आख़िर में ठीक एक सही [[ACTION:...]] टैग लगाओ।)"});
        }
        final String key = prefs.getString("gemini_key", ""), model = prefs.getString("gemini_model", "gemini-flash-latest");
        final Runnable slow = () -> { if (busy) speak("एक पल, सोच रहा हूँ।", null); };
        main.postDelayed(slow, 6000);
        final SentenceBuffer buf = new SentenceBuffer();
        final boolean[] spokeAny = {false};
        final String langLine = Lang.aiLine(lang(), text);
        try {
            exec.execute(() -> {
                // streamed: the first sentence is spoken while the rest is still being written
                final AiClient.Result r = AiClient.askStream(snap, key, model, langLine, 12, delta -> {
                    final List<String> ready = buf.push(delta);
                    final String vis = buf.visible();
                    main.post(() -> {
                        if (destroyed) return;
                        main.removeCallbacks(slow);
                        answer.setText(MainActivity.clean(vis));
                        for (String s1 : ready) { spokeAny[0] = true; queue(s1); }
                    });
                });
                runOnUiThread(() -> {
                    main.removeCallbacks(slow);
                    busy = false;
                    if (destroyed) return;
                    String t = r.text == null || r.text.trim().isEmpty() ? (buf.text().isEmpty() ? AiClient.sorry() : buf.text()) : r.text;
                    String shown = MainActivity.clean(actions.runTags(t));
                    if (shown.isEmpty()) shown = "ठीक है! 👍";
                    Convo.add("assistant", shown);
                    answer.setText(shown);
                    status.setText("मित्र");
                    if (spokeAny[0]) {
                        String rest = buf.flush();
                        if (!rest.isEmpty()) queueThen(rest, this::followUp);
                        else if (tts != null && tts.isSpeaking()) afterSpeak = this::followUp;
                        else followUp();
                    } else speak(shown, this::followUp);
                });
            });
        } catch (Exception e) { busy = false; }
    }

    private void followUp() {
        if (leftForAction || destroyed) return;
        listen(FOLLOW_UP_MS);
    }

    // ------------------------------------------------------------------ TTS (always speaks here, even if chat is muted)
    private void initTts() {
        tts = new TextToSpeech(this, st -> {
            if (st != TextToSpeech.SUCCESS) { if (pendingAfter != null) main.post(pendingAfter); return; }
            int r = tts.setLanguage(Locale.forLanguageTag(lang().tts));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(new Locale("hi", "IN"));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { done(id); }
                @Override public void onError(String id) { done(id); }
            });
            ttsReady = true;
            if (pendingSpeech != null) { String p = pendingSpeech; Runnable a = pendingAfter; pendingSpeech = null; pendingAfter = null; speak(p, a); }
        });
    }

    private void done(String id) {
        main.post(() -> {
            if (id != null && id.equals(lastUtt) && afterSpeak != null) { Runnable a = afterSpeak; afterSpeak = null; a.run(); }
        });
    }

    private void speak(String text, Runnable after) {
        String s = text.replaceAll("https?://\\S+", "")
                .replaceAll("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}\\x{2B00}-\\x{2BFF}]", "")
                .replace("*", "").replace("#", "").replace("•", "").replaceAll("\\(स्रोत: [^)]*\\)", "").trim();
        if (!ttsReady || tts == null) { pendingSpeech = s; pendingAfter = after; return; }
        if (s.isEmpty()) { if (after != null) main.post(after); return; }
        if (quiet != null && !listening) quiet.unmuteNow();
        try {
            String want = Lang.ttsForText(s, lang());
            int r = tts.setLanguage(Locale.forLanguageTag(want));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(new Locale("hi", "IN"));
        } catch (Exception ignored) { }
        afterSpeak = after;
        lastUtt = "v" + (utter++);
        Bundle p = new Bundle();
        p.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, p, lastUtt);
    }

    /** Adds a streamed sentence to the speech queue (no callback). */
    private void queue(String text) { queueThen(text, null); }

    private void queueThen(String text, Runnable after) {
        String s = text.replaceAll("https?://\\S+", "")
                .replaceAll("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}\\x{2B00}-\\x{2BFF}]", "")
                .replace("*", "").replace("#", "").replace("•", "").trim();
        if (s.isEmpty()) { if (after != null) main.post(after); return; }
        if (!ttsReady || tts == null) { pendingSpeech = pendingSpeech == null ? s : pendingSpeech + " " + s; pendingAfter = after; return; }
        try {
            int r = tts.setLanguage(Locale.forLanguageTag(Lang.ttsForText(s, lang())));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(new Locale("hi", "IN"));
        } catch (Exception ignored) { }
        afterSpeak = after;
        lastUtt = "v" + (utter++);
        Bundle p = new Bundle();
        p.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        tts.speak(s, TextToSpeech.QUEUE_ADD, p, lastUtt);
    }

    // ------------------------------------------------------------------ listening
    private final Runnable silenceTimeout = () -> {
        if (listening) { stopListening(); idle(); }
    };

    private void idle() {
        if (busy) return;
        status.setText("मित्र");
        if (answer.getText().length() == 0 || "…".contentEquals(answer.getText())) finish();
        else main.postDelayed(() -> { if (!listening && !busy && !destroyed && tts != null && !tts.isSpeaking()) finish(); }, 4000);
    }

    private void listen(long silenceMs) {
        if (destroyed || leftForAction) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            answer.setText("माइक की अनुमति चाहिए — मित्र ऐप खोलकर अनुमति दीजिए।");
            return;
        }
        try {
            if (sr == null) {
                sr = SpeechRecognizer.createSpeechRecognizer(this);
                sr.setRecognitionListener(new L());
            }
            Intent i = SilentMic.intent(Lang.srLocale(lang(), prefs.getString("auto_sr", "hi-IN")), 1);
            early.reset();
            listening = true;
            lastSilenceMs = silenceMs;
            status.setText("🎙️ सुन रहा हूँ…");
            micBtn.setText("■ रोकें");
            if (quiet == null) quiet = new SilentMic(this);
            quiet.mute(tts != null && tts.isSpeaking());
            sr.startListening(i);
            main.removeCallbacks(silenceTimeout);
            main.postDelayed(silenceTimeout, silenceMs);
        } catch (Exception e) {
            listening = false;
            if (quiet != null) quiet.unmuteNow();
            micBtn.setText("🎤 बोलिए");
        }
    }

    private void stopListening() {
        early.reset();
        main.removeCallbacks(silenceTimeout);
        boolean was = listening;
        listening = false;
        if (micBtn != null) micBtn.setText("🎤 बोलिए");
        try { if (sr != null) sr.cancel(); } catch (Exception ignored) { }
        if (was && quiet != null) quiet.unmuteSoon();
    }

    /** Heard nothing: try again after 1 s, then 3 s, then stop (no endless restarts / beeps). @return true if retrying */
    private boolean retryAfterSilence() {
        long d = backoff.onEmpty();
        if (d == ListenBackoff.STOP) { status.setText("बोलने के लिए 🎤 दबाइए"); return false; }
        final long ms = lastSilenceMs;
        main.postDelayed(() -> { if (!destroyed && !busy && !listening && (tts == null || !tts.isSpeaking())) listen(ms); }, d);
        return true;
    }

    private class L implements RecognitionListener {
        public void onReadyForSpeech(Bundle p) { }
        public void onBeginningOfSpeech() { main.removeCallbacks(silenceTimeout); main.postDelayed(silenceTimeout, 15000); }
        public void onRmsChanged(float v) { }
        public void onBufferReceived(byte[] b) { }
        public void onEndOfSpeech() { }
        public void onEvent(int t, Bundle p) { }
        public void onPartialResults(Bundle b) {
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty()) {
                heard.setText("“" + r.get(0) + "”");
                early.onPartial(r.get(0), () -> { if (listening && sr != null) try { sr.stopListening(); } catch (Exception ignored) { } });
            }
        }
        public void onError(int error) {
            boolean wasListening = listening;
            stopListening();
            if (!wasListening) return;
            if (SilentMic.retryOnline(error)) { listen(lastSilenceMs); return; }
            if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && retryAfterSilence()) return;
            if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                answer.setText("इंटरनेट जाँचिए, आवाज़ पहचानने के लिए नेटवर्क चाहिए।");
            }
            idle();
        }
        public void onResults(Bundle b) {
            stopListening();
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String t = r == null || r.isEmpty() ? "" : r.get(0).trim();
            if (t.isEmpty()) { if (!retryAfterSilence()) idle(); return; }
            backoff.reset();
            String w = WakeWord.split(t); // "मित्र, ..." said again in a follow-up
            if (w != null) t = w.isEmpty() ? t : w;
            if (w != null && w.isEmpty()) { speak("जी, बोलिए", () -> listen(8000)); return; }
            process(t);
        }
    }

    // ------------------------------------------------------------------ starting other apps from the lock screen
    @Override
    public void startActivity(Intent intent, Bundle options) {
        leftForAction = true;
        stopListening();
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean locked = km != null && km.isKeyguardLocked();
        boolean call = Intent.ACTION_CALL.equals(intent.getAction());
        boolean resolvable = intent.resolveActivity(getPackageManager()) != null;
        if (locked && !call && resolvable && Build.VERSION.SDK_INT >= 26) {
            final Intent in = intent;
            final Bundle op = options;
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { try { VoiceActivity.super.startActivity(in, op); } catch (Exception ignored) { } }
            });
            return;
        }
        super.startActivity(intent, options);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        actions.onPermissionResult(code, ok);
    }

    @Override
    protected void onResume() {
        super.onResume();
        actions.resumeQueue();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (leftForAction && !isChangingConfigurations()) finish();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        WakeGuard.GLOBAL.report(userSpoke, System.currentTimeMillis()); // false-trigger learning for the wake word
        early.reset();
        actions.dispose();
        main.removeCallbacksAndMessages(null);
        if (quiet != null) quiet.unmuteNow();
        if (sr != null) { try { sr.destroy(); } catch (Exception ignored) { } sr = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        exec.shutdown();
        WakeService.resume(this, "voice");
        super.onDestroy();
    }
}

package com.playrang.rangstudio;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements ActionHandler.Host {

    static final String GREETING = "नमस्ते, मैं मित्र हूँ, आपका मित्र। आप मुझसे कोई भी सवाल पूछ सकते हैं और कुछ भी काम करा सकते हैं।";
    static final String[] CHIPS = {"YouTube पर भजन चलाओ", "किसी को कॉल करो", "आज की तारीख", "मुझे एक चुटकुला सुनाओ", "WhatsApp खोलो",
            "सुबह 6 बजे का अलार्म लगाओ", "टॉर्च जलाओ", "अभी कितने बजे हैं?", "पढ़ाई में मन कैसे लगाएं?"};
    private static final int REQ_MIC = 7;
    static final String EXTRA_START_WAKE = "start_wake";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newFixedThreadPool(2);

    private LinearLayout chat;
    private ScrollView scroll;
    private EditText input;
    private TextView micBtn, sendBtn, muteBtn, statusText, convBtn;
    private HorizontalScrollView chipBar;

    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private SpeechRecognizer sr;
    private boolean listening;
    private boolean busy;
    private boolean convMode, resumed;
    private final ListenBackoff convBackoff = new ListenBackoff();
    private SilentMic quiet;
    private String lastUtt;
    private int utter;

    private SharedPreferences prefs;
    private ActionHandler actions;
    private String ttsLocale;
    private final java.util.Set<String> ttsWarned = new java.util.HashSet<>();

    Lang lang() { return Lang.get(prefs.getString("lang", "auto")); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("mitra", MODE_PRIVATE);
        actions = new ActionHandler(this, this, exec);
        buildUi();
        initTts();
        addBot(lang().greeting);
        speak(lang().greeting);
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_START_WAKE, false)) WakeService.start(this);
        else WakeService.startIfEnabled(this);
        main.postDelayed(this::offerWake, 2500);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && i.getBooleanExtra(EXTRA_START_WAKE, false)) WakeService.start(this);
    }

    private void openLive() {
        if (tts != null) tts.stop();
        if (convMode) endConversation(true);
        if (listening) stopListening();
        startActivity(new Intent(this, LiveActivity.class));
    }

    /** After first launch: offer to turn on "हमेशा सुनें" once. */
    private void offerWake() {
        if (isFinishing() || prefs.getBoolean("wake_offered", false) || WakeService.enabled(this)) return;
        prefs.edit().putBoolean("wake_offered", true).apply();
        new android.app.AlertDialog.Builder(this)
                .setTitle("हमेशा सुनें चालू करें?")
                .setMessage("फ़ोन लॉक हो या स्क्रीन बंद, बस “मित्र” बोलिए और सवाल पूछिए या काम कहिए — जैसे “मित्र, सोनू को कॉल करो”।\n\n"
                        + "‘मित्र’ शब्द फ़ोन पर ही पहचाना जाता है, आवाज़ रिकॉर्ड या अपलोड नहीं होती। इसमें बैटरी थोड़ी ज़्यादा लगेगी और एक नोटिफ़िकेशन दिखेगा। "
                        + "बाद में ⚙️ सेटिंग्स से कभी भी बदल सकते हैं।")
                .setPositiveButton("हाँ, चालू करें", (d, w) -> startActivity(new Intent(this, WakeSetupActivity.class).putExtra("enable", true)))
                .setNegativeButton("अभी नहीं", null)
                .show();
    }

    // ------------------------------------------------------------------ UI
    private int dp(float v) { return Ui.dp(this, v); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(Ui.gradient(Ui.SAFFRON_LIGHT, Ui.SAFFRON_DARK));
        header.setPadding(dp(14), dp(10), dp(8), dp(10));
        header.setElevation(dp(4));

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.mipmap.ic_launcher_round);
        FrameLayout avWrap = new FrameLayout(this);
        avWrap.setBackground(Ui.round(Color.WHITE, dp(22)));
        avWrap.setPadding(dp(2), dp(2), dp(2), dp(2));
        avWrap.addView(avatar, new FrameLayout.LayoutParams(dp(38), dp(38)));
        header.addView(avWrap, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("मित्र");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        statusText = new TextView(this);
        statusText.setText(lang().online);
        statusText.setTextColor(Color.parseColor("#FFF3E8"));
        statusText.setTextSize(12);
        titles.addView(title);
        titles.addView(statusText);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        convBtn = headerIcon("📞");
        convBtn.setContentDescription("बातचीत मोड");
        convBtn.setOnClickListener(v -> { if (convMode) endConversation(true); else startConversation(); });
        header.addView(convBtn);
        muteBtn = headerIcon(isMuted() ? "🔇" : "🔊");
        muteBtn.setContentDescription("आवाज़ चालू/बंद");
        muteBtn.setOnClickListener(v -> toggleMute());
        header.addView(muteBtn);
        TextView settings = headerIcon("⚙️");
        settings.setContentDescription("सेटिंग्स");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Prominent "Live बात" entry (ChatGPT-style voice conversation)
        TextView liveBtn = new TextView(this);
        liveBtn.setText("🎙️  Live बात — बोलिए, मित्र तुरंत जवाब देगा");
        liveBtn.setTextColor(Color.WHITE);
        liveBtn.setTextSize(16);
        liveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        liveBtn.setGravity(Gravity.CENTER);
        liveBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
        liveBtn.setElevation(dp(2));
        liveBtn.setContentDescription("Live बात शुरू करें");
        liveBtn.setOnClickListener(v -> openLive());
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMargins(dp(10), dp(8), dp(10), 0);
        liveBtn.setBackground(Ui.round(Color.parseColor("#2B2118"), dp(26)));
        root.addView(liveBtn, llp);

        // Chat list
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        chat.setPadding(dp(10), dp(12), dp(10), dp(12));
        TextView day = new TextView(this);
        day.setText("आज • " + LocalInfo.todayText());
        day.setTextSize(11.5f);
        day.setTextColor(Ui.MUTED);
        day.setGravity(Gravity.CENTER);
        day.setBackground(Ui.round(Color.parseColor("#FBE6D4"), dp(10)));
        day.setPadding(dp(10), dp(3), dp(10), dp(3));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        dlp.bottomMargin = dp(8);
        chat.addView(day, dlp);
        scroll.addView(chat);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Suggestion chips
        chipBar = new HorizontalScrollView(this);
        chipBar.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(8), dp(4), dp(8), dp(6));
        for (final String c : CHIPS) {
            TextView chip = new TextView(this);
            chip.setText(c);
            chip.setTextSize(13.5f);
            chip.setTextColor(Ui.SAFFRON_DARK);
            chip.setBackground(Ui.stroke(Ui.CHIP_BG, Color.parseColor("#FFC79A"), dp(1), dp(18)));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setOnClickListener(v -> submit(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chips.addView(chip, lp);
        }
        chipBar.addView(chips);
        root.addView(chipBar);

        // Input bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(8));
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));

        input = new EditText(this);
        input.setHint(lang().hint);
        input.setHintTextColor(Color.parseColor("#B3A396"));
        input.setTextColor(Ui.TEXT);
        input.setTextSize(16);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHorizontallyScrolling(false);
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setBackground(Ui.round(Color.parseColor("#F5EEE8"), dp(24)));
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { sendTyped(); return true; }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            public void afterTextChanged(Editable s) { updateButtons(); }
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sendBtn = roundButton("➤");
        sendBtn.setContentDescription("भेजें");
        sendBtn.setOnClickListener(v -> sendTyped());
        micBtn = roundButton("🎤");
        micBtn.setContentDescription("बोलकर पूछें");
        micBtn.setOnClickListener(v -> { if (listening) stopListening(); else startListening(); });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(48), dp(48));
        blp.leftMargin = dp(8);
        bar.addView(sendBtn, blp);
        bar.addView(micBtn, new LinearLayout.LayoutParams(blp));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        updateButtons();
    }

    private TextView headerIcon(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(21);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(6), dp(10), dp(6));
        v.setClickable(true);
        return v;
    }

    private TextView roundButton(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(20);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Ui.round(Ui.SAFFRON, dp(24)));
        v.setElevation(dp(2));
        return v;
    }

    private void updateButtons() {
        boolean hasText = input.getText().toString().trim().length() > 0;
        sendBtn.setVisibility(hasText && !listening ? View.VISIBLE : View.GONE);
        micBtn.setVisibility(hasText && !listening ? View.GONE : View.VISIBLE);
        micBtn.setBackground(Ui.round(listening ? Color.parseColor("#E53935") : Ui.SAFFRON, dp(24)));
        micBtn.setText(listening ? "■" : "🎤");
    }

    private String now() { return new SimpleDateFormat("h:mm a", Locale.ENGLISH).format(new Date()); }

    private TextView addBubble(String text, boolean mine) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.bubble(mine ? Ui.SAFFRON : Ui.BOT_BUBBLE, dp(18), mine));
        box.setElevation(dp(1));
        box.setPadding(dp(14), dp(9), dp(14), dp(6));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setLineSpacing(dp(2), 1f);
        tv.setTextColor(mine ? Color.WHITE : Ui.TEXT);
        tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78f));
        box.addView(tv);

        TextView ts = new TextView(this);
        ts.setText(now());
        ts.setTextSize(10.5f);
        ts.setTextColor(mine ? Color.parseColor("#FFE3CF") : Ui.MUTED);
        LinearLayout.LayoutParams tsl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tsl.gravity = Gravity.END;
        box.addView(ts, tsl);

        box.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Mitra", tv.getText()));
            Toast.makeText(this, "कॉपी हो गया", Toast.LENGTH_SHORT).show();
            return true;
        });
        if (!mine) box.setOnClickListener(v -> speakForce(tv.getText().toString()));

        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (mine) bl.leftMargin = dp(40); else bl.rightMargin = dp(40);
        row.addView(box, bl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = dp(4);
        rl.bottomMargin = dp(4);
        chat.addView(row, rl);
        row.setAlpha(0f);
        row.setTranslationY(dp(10));
        row.animate().alpha(1f).translationY(0).setDuration(220).start();
        scrollDown();
        return tv;
    }

    private void addBot(String text) { addBubble(text, false); }

    private void scrollDown() { scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)); }

    static String clean(String s) {
        return s.replace("**", "").replace("__", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[*-]\\s+", "• ")
                .replaceAll("\n{3,}", "\n\n").trim();
    }

    // ------------------------------------------------------------------ Host
    @Override
    public void botSay(String text) {
        runOnUiThread(() -> { addBot(text); speak(text); });
    }

    @Override
    public void showChoices(String question, List<String> labels, int generation) {
        runOnUiThread(() -> {
            addBot(question);
            speak(question);
            final LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(4), 0, dp(40), dp(4));
            final boolean[] done = {false};
            for (int i = 0; i < labels.size(); i++) {
                final int idx = i;
                TextView opt = new TextView(this);
                opt.setText((labels.size() > 1 ? (i + 1) + ".  " : "") + labels.get(i));
                opt.setTextSize(15);
                opt.setTextColor(Ui.SAFFRON_DARK);
                opt.setBackground(Ui.stroke(Ui.CHIP_BG, Color.parseColor("#FFC79A"), dp(1), dp(16)));
                opt.setPadding(dp(14), dp(10), dp(14), dp(10));
                opt.setOnClickListener(v -> {
                    if (done[0]) return;
                    addBubble(labels.get(idx), true);
                    if (actions.choose(generation, idx)) { done[0] = true; box.setAlpha(0.5f); }
                    else Toast.makeText(this, "यह सवाल पुराना हो गया है", Toast.LENGTH_SHORT).show();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                box.addView(opt, lp);
            }
            chat.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            scrollDown();
        });
    }

    // ------------------------------------------------------------------ sending
    private void sendTyped() {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) return;
        input.setText("");
        hideKeyboard();
        submit(t);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void submit(String text) {
        if (tts != null) tts.stop();
        addBubble(text, true);
        chipBar.setVisibility(View.GONE);
        if (convMode && Convo.isStop(text)) { endConversation(false); return; }
        try {
            if (actions.handlePending(text)) return;
            if (actions.tryLocal(text)) return;
        } catch (Exception e) {
            // fall through to AI
        }
        if (busy) {
            addBot("एक पल रुकिए, पिछले सवाल का जवाब आ रहा है… 🙏");
            return;
        }
        Convo.add("user", text);
        askAi(true);
    }

    private String geminiKey() { return prefs.getString("gemini_key", ""); }

    private void askAi(final boolean allowRetry) {
        busy = true;
        final TextView typing = addBubble("लिख रहा हूँ", false);
        final Runnable[] anim = new Runnable[1];
        final int[] dots = {0};
        anim[0] = () -> {
            dots[0] = (dots[0] + 1) % 4;
            StringBuilder s = new StringBuilder("लिख रहा हूँ");
            for (int i = 0; i < dots[0]; i++) s.append('.');
            typing.setText(s.toString());
            main.postDelayed(anim[0], 400);
        };
        main.post(anim[0]);
        statusText.setText(lang().typing);
        final List<String[]> snapshot = Convo.snapshot();
        if (!snapshot.isEmpty()) {
            String[] lastMsg = snapshot.get(snapshot.size() - 1);
            if ("user".equals(lastMsg[0]) && CommandParser.looksLikeCommand(lastMsg[1])) {
                // The phone parser was unsure: nudge the AI to answer with one exact action tag.
                snapshot.set(snapshot.size() - 1, new String[]{"user", lastMsg[1]
                        + "\n\n(ऐप का नोट: यह फ़ोन पर कोई काम कराने का आदेश लगता है। अगर ऐसा है तो एक छोटा वाक्य लिखो और आख़िर में ठीक एक सही [[ACTION:...]] टैग लगाओ। "
                        + "नंबर न पता हो तो भी call_contact / whatsapp_contact / sms_contact टैग में नाम लिखो — फ़ोन खुद कॉन्टैक्ट्स में ढूँढ लेगा।)"});
            }
        }
        final String key = geminiKey();
        final String model = prefs.getString("gemini_model", "gemini-flash-latest");
        final SentenceBuffer buf = new SentenceBuffer();
        final boolean[] spoke = {false};
        final TextView[] live = {null};
        exec.execute(() -> {
            final AiClient.Result r = AiClient.askStream(snapshot, key, model, lang().aiInstruction(), 45, delta -> {
                final List<String> ready = buf.push(delta);
                final String vis = clean(buf.visible());
                main.post(() -> {
                    if (live[0] == null) {
                        if (vis.isEmpty()) return;
                        main.removeCallbacks(anim[0]);
                        View row = (View) typing.getParent().getParent();
                        chat.removeView(row);
                        live[0] = addBubble(vis, false);
                    } else {
                        live[0].setText(vis);
                        scrollDown();
                    }
                    for (String sn : ready) { spoke[0] = true; speakQueued(sn); }
                });
            });
            runOnUiThread(() -> {
                main.removeCallbacks(anim[0]);
                if (live[0] == null) {
                    View row = (View) typing.getParent().getParent();
                    chat.removeView(row);
                }
                statusText.setText(lang().online);
                String txt = r.text == null || r.text.trim().isEmpty() ? (buf.text().trim().isEmpty() ? AiClient.sorry() : buf.text()) : r.text;
                String shown = clean(actions.runTags(txt));
                if (shown.isEmpty()) shown = "ठीक है! 👍";
                Convo.add("assistant", shown);
                if (live[0] != null) { live[0].setText(shown); scrollDown(); }
                else addBot(shown);
                busy = false;
                if (spoke[0]) {
                    String rest = buf.flush();
                    if (!rest.isEmpty()) speakQueued(rest);
                    else if (tts == null || !tts.isSpeaking()) spoke(lastUtt);
                } else {
                    speak(shown);
                }
            });
        });
    }

    /** Shows "थोड़ा रुकिए, X सेकंड में..." with a live countdown, then runs next. */
    private void waitThen(final int seconds, final Runnable next, boolean sayIt) {
        final TextView tv = addBubble("⏳ थोड़ा रुकिए, " + seconds + " सेकंड में फिर पूछिए — मैं अपने-आप दोबारा पूछ लूँगा।", false);
        if (sayIt) speak("थोड़ा रुकिए, " + seconds + " सेकंड में जवाब देता हूँ।");
        statusText.setText("इंतज़ार… " + seconds + "s");
        final int[] left = {seconds};
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            left[0]--;
            if (left[0] <= 0) {
                chat.removeView((View) tv.getParent().getParent());
                statusText.setText(lang().online);
                next.run();
                return;
            }
            tv.setText("⏳ थोड़ा रुकिए, " + left[0] + " सेकंड में फिर पूछिए — मैं अपने-आप दोबारा पूछ लूँगा।");
            statusText.setText("इंतज़ार… " + left[0] + "s");
            main.postDelayed(tick[0], 1000);
        };
        main.postDelayed(tick[0], 1000);
    }

    // ------------------------------------------------------------------ TTS
    private boolean isMuted() { return prefs.getBoolean("muted", false); }

    private void toggleMute() {
        boolean m = !isMuted();
        prefs.edit().putBoolean("muted", m).apply();
        muteBtn.setText(m ? "🔇" : "🔊");
        if (m && tts != null) tts.stop();
        Toast.makeText(this, m ? "आवाज़ बंद" : "आवाज़ चालू", Toast.LENGTH_SHORT).show();
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(new Locale("hi", "IN"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(new Locale("hi"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault());
            }
            tts.setSpeechRate(1.0f);
            tts.setPitch(1.05f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { main.post(() -> spoke(id)); }
                @Override public void onError(String id) { main.post(() -> spoke(id)); }
            });
            ttsReady = true;
            if (pendingSpeech != null) { String p = pendingSpeech; pendingSpeech = null; speakForce(p); }
        });
    }

    private void speak(String text) {
        if (isMuted() && !convMode) return; // conversation mode always speaks
        speakForce(text);
    }

    /** Adds a sentence to the speech queue (streamed replies start speaking before they are complete). */
    private void speakQueued(String text) {
        if (isMuted() && !convMode) return;
        String s = text.replaceAll("https?://\\S+", "")
                .replaceAll("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}\\x{2B00}-\\x{2BFF}]", "")
                .replace("*", "").replace("#", "").replace("•", "").trim();
        if (s.isEmpty()) return;
        if (!ttsReady || tts == null) { pendingSpeech = pendingSpeech == null ? s : pendingSpeech + " " + s; return; }
        useVoiceFor(s);
        lastUtt = "mitra" + (utter++);
        tts.speak(s, TextToSpeech.QUEUE_ADD, null, lastUtt);
    }

    /** Called when an utterance finished: in conversation mode, listen again. */
    private void spoke(String id) {
        if (id == null || !id.equals(lastUtt)) return;
        if (convMode && resumed && !busy && !listening && (tts == null || !tts.isSpeaking())) convListen();
    }

    // ------------------------------------------------------------------ conversation mode ("बातचीत मोड")
    private void startConversation() {
        convMode = true;
        convBackoff.reset();
        WakeService.pause(this, "ui", 0);
        convBtn.setText("⏹️");
        convBtn.setBackground(Ui.round(Color.parseColor("#33FFFFFF"), dp(20)));
        Toast.makeText(this, "बातचीत मोड चालू — बोलते रहिए। रोकने के लिए “बस” बोलें या ⏹️ दबाएँ।", Toast.LENGTH_LONG).show();
        addBot("📞 बातचीत मोड चालू — फ़ोन कॉल की तरह बात कीजिए। बंद करने के लिए “बस” या “बंद करो” बोलिए।");
        speakForce("बातचीत मोड चालू। बोलिए।");
    }

    private void endConversation(boolean silent) {
        convMode = false;
        convBtn.setText("📞");
        convBtn.setBackground(null);
        if (listening) stopListening();
        WakeService.resume(this, "ui");
        if (!silent) { addBot("ठीक है, बातचीत बंद। जब चाहें फिर बुलाइए 🙏"); speakForce("ठीक है, बातचीत बंद।"); }
        else if (tts != null) tts.stop();
    }

    private void convListen() {
        if (!convMode || !resumed || busy || listening) return;
        startListening();
    }

    private void speakForce(String text) {
        String s = text.replaceAll("https?://\\S+", "")
                .replaceAll("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}\\x{2B00}-\\x{2BFF}]", "")
                .replace("*", "").replace("#", "").replace("•", "").trim();
        if (s.isEmpty()) return;
        if (!ttsReady || tts == null) { pendingSpeech = s; return; }
        useVoiceFor(s);
        lastUtt = "mitra" + (utter++);
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, lastUtt);
    }

    /** Picks the TTS voice from the reply's script / chosen language; falls back to Hindi, then English. */
    private void useVoiceFor(String s) {
        String want = Lang.ttsForText(s, lang());
        if (want.equals(ttsLocale)) return;
        int r = tts.setLanguage(Locale.forLanguageTag(want));
        boolean missing = r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED;
        if (missing) {
            if (ttsWarned.add(want)) {
                Toast.makeText(this, "इस भाषा (" + want + ") की आवाज़ फ़ोन में नहीं मिली — Google Text-to-speech ऐप में यह भाषा की voice "
                        + "डाउनलोड करें (⚙️ सेटिंग्स › आवाज़)। अभी हिंदी आवाज़ में सुना रहा हूँ।", Toast.LENGTH_LONG).show();
            }
            r = tts.setLanguage(new Locale("hi", "IN"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(new Locale("en", "IN"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault());
            }
        }
        ttsLocale = want;
    }

    // ------------------------------------------------------------------ speech input
    /** In-app recognizer only (never the Google popup), quiet and preferring on-device recognition. */
    private Intent recognizerIntent() {
        Intent i = SilentMic.intent(lang().sr, 1);
        i.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"hi-IN", "en-IN"});
        return i;
    }

    private void startListening() {
        if (tts != null) tts.stop();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { noRecognizer(); return; }
        try {
            if (sr == null) {
                sr = SpeechRecognizer.createSpeechRecognizer(this);
                sr.setRecognitionListener(new Listener());
            }
            listening = true;
            WakeService.pause(this, "ui", convMode ? 0 : 60000);
            input.setHint(lang().listening);
            statusText.setText("सुन रहा है…");
            updateButtons();
            if (quiet == null) quiet = new SilentMic(this);
            quiet.mute(tts != null && tts.isSpeaking());
            sr.startListening(recognizerIntent());
        } catch (Exception e) {
            listening = false;
            if (quiet != null) quiet.unmuteNow();
            updateButtons();
            noRecognizer();
        }
    }

    private void stopListening() {
        if (sr != null) sr.stopListening();
        endListeningUi();
    }

    private void endListeningUi() {
        listening = false;
        if (!convMode) WakeService.resume(this, "ui");
        input.setHint(lang().hint);
        statusText.setText(lang().online);
        updateButtons();
    }

    /** No popup fallback any more (the Google mic dialog kept appearing): just tell the user. */
    private void noRecognizer() {
        if (convMode) endConversation(true);
        Toast.makeText(this, "इस फ़ोन में आवाज़ पहचानने की सुविधा नहीं मिली। Google ऐप इंस्टॉल/अपडेट करें, या लिखकर पूछिए।", Toast.LENGTH_LONG).show();
    }

    private class Listener implements RecognitionListener {
        public void onReadyForSpeech(Bundle p) { } // stay muted until the session ends so the stop beep is silent too
        public void onBeginningOfSpeech() { }
        public void onRmsChanged(float v) {
            if (listening) micBtn.setScaleX(1f + Math.min(Math.max(v, 0f), 10f) / 40f);
            if (listening) micBtn.setScaleY(micBtn.getScaleX());
        }
        public void onBufferReceived(byte[] b) { }
        public void onEndOfSpeech() { micBtn.setScaleX(1f); micBtn.setScaleY(1f); }
        public void onError(int error) {
            micBtn.setScaleX(1f); micBtn.setScaleY(1f);
            if (quiet != null) quiet.unmuteSoon();
            endListeningUi();
            if (SilentMic.retryOnline(error)) { main.postDelayed(MainActivity.this::startListening, 300); return; }
            if (convMode && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                long d = convBackoff.onEmpty();
                if (d == ListenBackoff.STOP) { endConversation(true); addBot("कोई आवाज़ नहीं आई, बातचीत मोड बंद कर दिया। 📞 दबाकर फिर शुरू करें।"); }
                else main.postDelayed(MainActivity.this::convListen, d);
                return;
            }
            if (convMode && (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT)) {
                main.postDelayed(MainActivity.this::convListen, 800);
                return;
            }
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Toast.makeText(MainActivity.this, "आवाज़ साफ़ नहीं आई, फिर से 🎤 दबाकर बोलिए।", Toast.LENGTH_SHORT).show();
            } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                Toast.makeText(MainActivity.this, "इंटरनेट जाँचिए, आवाज़ पहचानने के लिए नेटवर्क चाहिए।", Toast.LENGTH_SHORT).show();
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                Toast.makeText(MainActivity.this, "एक पल रुकिए, फिर 🎤 दबाइए।", Toast.LENGTH_SHORT).show();
            } else {
                // unknown recognizer error: recreate it next time, never open the Google popup
                if (sr != null) { try { sr.destroy(); } catch (Exception ignored) { } sr = null; }
                if (convMode) endConversation(true);
                Toast.makeText(MainActivity.this, "आवाज़ पहचान में दिक्कत आई, फिर से 🎤 दबाइए।", Toast.LENGTH_SHORT).show();
            }
        }
        public void onResults(Bundle results) {
            micBtn.setScaleX(1f); micBtn.setScaleY(1f);
            if (quiet != null) quiet.unmuteSoon();
            endListeningUi();
            ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            input.setText("");
            if (r != null && !r.isEmpty() && r.get(0).trim().length() > 0) { convBackoff.reset(); submit(r.get(0).trim()); }
            else if (convMode) {
                long d = convBackoff.onEmpty();
                if (d == ListenBackoff.STOP) { endConversation(true); addBot("कोई आवाज़ नहीं आई, बातचीत मोड बंद कर दिया। 📞 दबाकर फिर शुरू करें।"); }
                else main.postDelayed(MainActivity.this::convListen, d);
            }
        }
        public void onPartialResults(Bundle partial) {
            ArrayList<String> r = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty()) { input.setText(r.get(0)); input.setSelection(input.getText().length()); }
        }
        public void onEvent(int t, Bundle p) { }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (actions.onPermissionResult(code, ok)) return;
        if (code == REQ_MIC) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else Toast.makeText(this, "माइक की अनुमति के बिना बोलकर नहीं पूछ सकते — लिखकर पूछिए।", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (convMode) WakeService.pause(this, "ui", 0);
        if (convMode && !busy && !listening && (tts == null || !tts.isSpeaking())) main.postDelayed(this::convListen, 600);
        if (muteBtn != null) muteBtn.setText(isMuted() ? "🔇" : "🔊");
        if (input != null && !listening) { input.setHint(lang().hint); statusText.setText(lang().online); }
        ttsLocale = null;
        if (prefs.getBoolean("clear_chat", false)) {
            prefs.edit().putBoolean("clear_chat", false).apply();
            Convo.clear();
            actions.reset();
            while (chat.getChildCount() > 1) chat.removeViewAt(1);
            chipBar.setVisibility(View.VISIBLE);
            addBot(lang().greeting);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        if (listening) stopListening();
        if (quiet != null) quiet.unmuteNow();
        WakeService.resume(this, "ui");
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (quiet != null) quiet.unmuteNow();
        if (sr != null) { sr.destroy(); sr = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        exec.shutdownNow();
        super.onDestroy();
    }
}

package com.playrang.rangstudio;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
    // v4.1: fewer, shorter suggestions so the home screen stays clean on small phones
    static final String[] CHIPS = {"YouTube पर भजन चलाओ", "किसी को कॉल करो", "सुबह 6 बजे का अलार्म लगाओ", "मुझे एक चुटकुला सुनाओ", "बैटरी कितनी है?"};
    private static final int MENU_LIVE = 1, MENU_CONV = 2, MENU_MUTE = 3, MENU_WAKE = 4, MENU_SETTINGS = 5;
    private static final String BUBBLE_TAG = "bubble";
    private static final int REQ_MIC = 7;
    static final String EXTRA_START_WAKE = "start_wake";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newFixedThreadPool(2);

    private LinearLayout chat;
    private ScrollView scroll;
    private EditText input;
    private TextView micBtn, sendBtn, muteBtn, statusText, convBtn;
    private HorizontalScrollView chipBar;
    private boolean chipsUsed;

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
    private final SilentMic.EarlyStop early = new SilentMic.EarlyStop();
    private String lastUserText = "";

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
        // v4.0 speed: warm the AI connection and the recognizer while the greeting plays
        final boolean gem = geminiKey().trim().length() > 10;
        try { exec.execute(() -> AiClient.warmUp(gem)); } catch (Exception ignored) { }
        main.postDelayed(this::prewarmRecognizer, 1200);
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_START_WAKE, false)) WakeService.start(this);
        else WakeService.startIfEnabled(this);
        main.postDelayed(this::offerWake, 2500);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && i.getBooleanExtra(EXTRA_START_WAKE, false)) WakeService.start(this);
    }

    private void prewarmRecognizer() {
        if (isFinishing() || sr != null) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
            sr = SpeechRecognizer.createSpeechRecognizer(this); // binds the recognition service now, not on the first tap
            sr.setRecognitionListener(new Listener());
        } catch (Exception e) { sr = null; }
    }

    private String autoSr() { return prefs.getString("auto_sr", "hi-IN"); }

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
                        + "बाद में ऊपर ⋮ मेनू › सेटिंग्स से कभी भी बदल सकते हैं।")
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
        root.setFitsSystemWindows(true); // keeps header / input bar clear of the status bar, nav bar and keyboard (adjustResize)

        // Header: avatar, name + status, and ONE overflow menu (⋮) for everything secondary.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(Ui.gradient(Ui.SAFFRON_LIGHT, Ui.SAFFRON_DARK));
        header.setPadding(dp(12), dp(4), dp(4), dp(4));
        header.setMinimumHeight(dp(56));
        header.setElevation(dp(4));

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.mipmap.ic_launcher_round);
        avatar.setContentDescription("मित्र");
        FrameLayout avWrap = new FrameLayout(this);
        avWrap.setBackground(Ui.round(Color.WHITE, dp(20)));
        avWrap.setPadding(dp(2), dp(2), dp(2), dp(2));
        avWrap.addView(avatar, new FrameLayout.LayoutParams(dp(36), dp(36)));
        header.addView(avWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, dp(4), 0);
        TextView title = new TextView(this);
        title.setText("मित्र");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        Ui.oneLine(title);
        statusText = new TextView(this);
        statusText.setText(lang().online);
        statusText.setTextColor(Color.parseColor("#FFF3E8"));
        statusText.setTextSize(12);
        Ui.oneLine(statusText);
        titles.addView(title);
        titles.addView(statusText);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Shown only while it matters: stop conversation mode / unmute. Otherwise the header stays clean.
        convBtn = headerIcon("⏹️");
        convBtn.setContentDescription("बातचीत मोड बंद करें");
        convBtn.setOnClickListener(v -> { if (convMode) endConversation(true); else startConversation(); });
        convBtn.setVisibility(View.GONE);
        header.addView(convBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        muteBtn = headerIcon("🔇");
        muteBtn.setContentDescription("आवाज़ चालू करें");
        muteBtn.setOnClickListener(v -> toggleMute());
        header.addView(muteBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView more = headerIcon("⋮");
        more.setTextColor(Color.WHITE);
        more.setTypeface(Typeface.DEFAULT_BOLD);
        more.setContentDescription("और विकल्प: Live बात, बातचीत मोड, आवाज़, हमेशा सुनें, सेटिंग्स");
        more.setOnClickListener(this::showMenu);
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateHeader();

        // Chat list (the main area). Centred and width-capped on tablets / landscape.
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        chat = new Ui.MaxWidthColumn(this, 720);
        chat.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView day = new TextView(this);
        day.setText("आज • " + LocalInfo.todayText());
        day.setTextSize(12);
        day.setTextColor(Ui.MUTED);
        day.setGravity(Gravity.CENTER);
        day.setBackground(Ui.round(Color.parseColor("#FBE6D4"), dp(10)));
        day.setPadding(dp(10), dp(3), dp(10), dp(3));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        dlp.bottomMargin = dp(8);
        chat.addView(day, dlp);
        FrameLayout.LayoutParams chatLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chatLp.gravity = Gravity.CENTER_HORIZONTAL;
        scroll.addView(chat, chatLp);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // A few short suggestions in one scrollable row (hidden after the first message and on short screens)
        chipBar = new HorizontalScrollView(this);
        chipBar.setHorizontalScrollBarEnabled(false);
        chipBar.setClipToPadding(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(dp(12), dp(2), dp(12), dp(4));
        for (final String c : CHIPS) {
            TextView chip = new TextView(this);
            chip.setText(c);
            chip.setTextSize(14);
            chip.setTextColor(Ui.SAFFRON_DARK);
            chip.setGravity(Gravity.CENTER);
            Ui.oneLine(chip);
            chip.setBackground(Ui.ripple(Ui.stroke(Ui.CHIP_BG, Color.parseColor("#FFC79A"), dp(1), dp(20)), 0x33F26A1B));
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setMinHeight(dp(40));
            chip.setOnClickListener(v -> submit(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
            lp.rightMargin = dp(8);
            chips.addView(chip, lp);
        }
        chipBar.addView(chips);
        root.addView(chipBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Compact input bar: text field + one big primary button (🎤 talk to Mitra, turns into ➤ send while typing)
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(8));
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));

        input = new EditText(this);
        input.setHint(lang().hint);
        input.setHintTextColor(Color.parseColor("#9C8B7E"));
        input.setTextColor(Ui.TEXT);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHorizontallyScrolling(false);
        input.setMaxLines(Ui.shortScreen(this) ? 2 : 4);
        input.setMinHeight(dp(48));
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setBackground(Ui.round(Color.parseColor("#F5EEE8"), dp(24)));
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
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
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(52), dp(52));
        blp.leftMargin = dp(8);
        bar.addView(sendBtn, blp);
        bar.addView(micBtn, new LinearLayout.LayoutParams(blp));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        applyScreenSize();
        updateButtons();
    }

    /** The single overflow menu: every secondary feature lives here instead of crowding the home screen. */
    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, MENU_LIVE, 0, "🎙️  Live बात (आवाज़ में बात)");
        m.add(0, MENU_CONV, 1, convMode ? "⏹️  बातचीत मोड बंद करें" : "📞  बातचीत मोड (लगातार बात)");
        m.add(0, MENU_MUTE, 2, isMuted() ? "🔊  जवाब बोलकर सुनाएँ" : "🔇  आवाज़ बंद करें");
        m.add(0, MENU_WAKE, 3, "👂  हमेशा सुनें (‘मित्र’ बोलकर जगाएँ)");
        m.add(0, MENU_SETTINGS, 4, "⚙️  सेटिंग्स और जानकारी");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_LIVE: openLive(); return true;
                case MENU_CONV: if (convMode) endConversation(true); else startConversation(); return true;
                case MENU_MUTE: toggleMute(); return true;
                case MENU_WAKE: startActivity(new Intent(this, WakeSetupActivity.class)); return true;
                case MENU_SETTINGS: startActivity(new Intent(this, SettingsActivity.class)); return true;
                default: return false;
            }
        });
        pm.show();
    }

    /** Header shows the stop-conversation / unmute shortcuts only while they are relevant. */
    private void updateHeader() {
        if (convBtn != null) convBtn.setVisibility(convMode ? View.VISIBLE : View.GONE);
        if (muteBtn != null) muteBtn.setVisibility(isMuted() ? View.VISIBLE : View.GONE);
    }

    /** Re-fits the layout when the window size changes (rotation, split-screen, font size) without recreating it. */
    private void applyScreenSize() {
        if (chipBar != null) chipBar.setVisibility(chipsUsed || Ui.shortScreen(this) ? View.GONE : View.VISIBLE);
        if (input != null) input.setMaxLines(Ui.shortScreen(this) ? 2 : 4);
        if (chat == null) return;
        int maxW = Ui.bubbleMaxWidth(this);
        for (int i = 0; i < chat.getChildCount(); i++) {
            View row = chat.getChildAt(i);
            if (!(row instanceof ViewGroup)) continue;
            View box = ((ViewGroup) row).getChildCount() > 0 ? ((ViewGroup) row).getChildAt(0) : null;
            if (box instanceof ViewGroup && ((ViewGroup) box).getChildCount() > 0 && ((ViewGroup) box).getChildAt(0) instanceof TextView
                    && BUBBLE_TAG.equals(((ViewGroup) box).getChildAt(0).getTag())) {
                ((TextView) ((ViewGroup) box).getChildAt(0)).setMaxWidth(maxW);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyScreenSize();
        scrollDown();
    }

    private TextView headerIcon(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22); // icon glyph: fixed size so it always fits its 48dp button
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setBackground(Ui.ripple(null, 0x40FFFFFF));
        Ui.touch(v);
        return v;
    }

    private TextView roundButton(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22); // icon glyph: fixed size so it never overflows the circle
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setBackground(Ui.ripple(Ui.round(Ui.SAFFRON, dp(26)), 0x55FFFFFF));
        v.setElevation(dp(2));
        Ui.touch(v);
        return v;
    }

    private void updateButtons() {
        boolean hasText = input.getText().toString().trim().length() > 0;
        sendBtn.setVisibility(hasText && !listening ? View.VISIBLE : View.GONE);
        micBtn.setVisibility(hasText && !listening ? View.GONE : View.VISIBLE);
        micBtn.setBackground(Ui.ripple(Ui.round(listening ? Color.parseColor("#E53935") : Ui.SAFFRON, dp(26)), 0x55FFFFFF));
        micBtn.setText(listening ? "■" : "🎤");
        micBtn.setContentDescription(listening ? "सुनना बंद करें" : "बोलकर पूछें");
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
        tv.setMaxWidth(Ui.bubbleMaxWidth(this));
        tv.setTag(BUBBLE_TAG);
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
        if (mine) bl.leftMargin = dp(32); else bl.rightMargin = dp(32);
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
            box.setPadding(dp(4), 0, dp(32), dp(4));
            final boolean[] done = {false};
            for (int i = 0; i < labels.size(); i++) {
                final int idx = i;
                TextView opt = new TextView(this);
                opt.setText((labels.size() > 1 ? (i + 1) + ".  " : "") + labels.get(i));
                opt.setTextSize(15);
                opt.setTextColor(Ui.SAFFRON_DARK);
                opt.setBackground(Ui.ripple(Ui.stroke(Ui.CHIP_BG, Color.parseColor("#FFC79A"), dp(1), dp(16)), 0x33F26A1B));
                opt.setPadding(dp(14), dp(10), dp(14), dp(10));
                opt.setGravity(Gravity.CENTER_VERTICAL);
                opt.setMinHeight(dp(48));
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
        if (quiet != null) quiet.unmuteNow(); // an instant local answer must not be swallowed by the mic's beep-mute
        addBubble(text, true);
        lastUserText = text;
        String learned = Lang.learn(autoSr(), text);
        if (learned != null && !learned.equals(autoSr())) prefs.edit().putString("auto_sr", learned).apply();
        chipsUsed = true;
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
            final AiClient.Result r = AiClient.askStream(snapshot, key, model, Lang.aiLine(lang(), lastUserText), 20, delta -> {
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
        updateHeader();
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
        if (quiet != null && !listening) quiet.unmuteNow();
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
        updateHeader();
        Toast.makeText(this, "बातचीत मोड चालू — बोलते रहिए। रोकने के लिए “बस” बोलें या ऊपर ⏹️ दबाएँ।", Toast.LENGTH_LONG).show();
        addBot("📞 बातचीत मोड चालू — फ़ोन कॉल की तरह बात कीजिए। बंद करने के लिए “बस” या “बंद करो” बोलिए।");
        speakForce("बातचीत मोड चालू। बोलिए।");
    }

    private void endConversation(boolean silent) {
        convMode = false;
        updateHeader();
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
        if (quiet != null && !listening) quiet.unmuteNow();
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
                        + "डाउनलोड करें (⋮ मेनू › सेटिंग्स › आवाज़)। अभी हिंदी आवाज़ में सुना रहा हूँ।", Toast.LENGTH_LONG).show();
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
        return SilentMic.intent(Lang.srLocale(lang(), autoSr()), 1);
    }

    private void startListening() {
        if (tts != null) tts.stop();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askMic();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { noRecognizer(); return; }
        early.reset();
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

    /** Explains in Hindi why the mic is needed, then shows Android's permission prompt. */
    private void askMic() {
        if (prefs.getBoolean("mic_explained", false)) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle("🎤 माइक की अनुमति")
                .setMessage("बोलकर सवाल पूछने और काम कराने के लिए मित्र को माइक चाहिए। आपकी आवाज़ सिर्फ़ उसी समय टेक्स्ट में बदली जाती है जब आप 🎤 दबाते हैं — "
                        + "मित्र आवाज़ रिकॉर्ड या सेव नहीं करता।")
                .setPositiveButton("ठीक है", (d, w) -> {
                    prefs.edit().putBoolean("mic_explained", true).apply();
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                })
                .setNegativeButton("अभी नहीं", null)
                .show();
    }

    private void stopListening() {
        early.reset();
        if (sr != null) sr.stopListening();
        endListeningUi();
    }

    private void endListeningUi() {
        early.reset();
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
                if (d == ListenBackoff.STOP) { endConversation(true); addBot("कोई आवाज़ नहीं आई, बातचीत मोड बंद कर दिया। ⋮ मेनू › 📞 बातचीत मोड से फिर शुरू करें।"); }
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
                if (d == ListenBackoff.STOP) { endConversation(true); addBot("कोई आवाज़ नहीं आई, बातचीत मोड बंद कर दिया। ⋮ मेनू › 📞 बातचीत मोड से फिर शुरू करें।"); }
                else main.postDelayed(MainActivity.this::convListen, d);
            }
        }
        public void onPartialResults(Bundle partial) {
            ArrayList<String> r = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty()) {
                input.setText(r.get(0));
                input.setSelection(input.getText().length());
                // a complete command already? finish now instead of waiting for the silence timeout
                early.onPartial(r.get(0), () -> { if (listening && sr != null) try { sr.stopListening(); } catch (Exception ignored) { } });
            }
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
        updateHeader();
        actions.resumeQueue(); // multi-step: continue after a call / another app
        if (input != null && !listening) { input.setHint(lang().hint); statusText.setText(lang().online); }
        ttsLocale = null;
        if (prefs.getBoolean("clear_chat", false)) {
            prefs.edit().putBoolean("clear_chat", false).apply();
            Convo.clear();
            actions.reset();
            ActionHandler.clearQueue();
            while (chat.getChildCount() > 1) chat.removeViewAt(1);
            chipsUsed = false;
            applyScreenSize();
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
        early.reset();
        actions.dispose();
        if (quiet != null) quiet.unmuteNow();
        if (sr != null) { sr.destroy(); sr = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        exec.shutdownNow();
        super.onDestroy();
    }
}

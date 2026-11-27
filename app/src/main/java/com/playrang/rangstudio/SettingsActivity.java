package com.playrang.rangstudio;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings + About screen, built with native views. */
public class SettingsActivity extends Activity {

    static final String PRIVACY_URL = "https://mannukumarbank222-cmd.github.io/rang-studio/privacy-policy-mitra.html";

    private SharedPreferences prefs;
    private LinearLayout body;
    private Switch wakeSwitch;

    @Override
    protected void onResume() {
        super.onResume();
        if (wakeSwitch != null) wakeSwitch.setChecked(WakeService.enabled(this));
    }

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("mitra", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(Ui.gradient(Ui.SAFFRON_LIGHT, Ui.SAFFRON_DARK));
        header.setPadding(dp(4), dp(4), dp(16), dp(4));
        header.setMinimumHeight(dp(56));
        header.setElevation(dp(4));
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.ripple(null, 0x40FFFFFF));
        Ui.touch(back);
        back.setContentDescription("वापस");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = new TextView(this);
        title.setText("सेटिंग्स और जानकारी");
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        title.setPadding(dp(8), 0, 0, 0);
        Ui.oneLine(title);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        body = new Ui.MaxWidthColumn(this);
        body.setPadding(dp(16), dp(16), dp(16), dp(24));
        android.widget.FrameLayout.LayoutParams blp = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        sv.addView(body, blp);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        // About card
        LinearLayout about = card();
        about.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher_round);
        about.addView(logo, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView name = text("मित्र (Mitra)", 22, Ui.TEXT, true);
        name.setGravity(Gravity.CENTER);
        about.addView(name);
        String ver = "4.1";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception ignored) { }
        TextView v = text("संस्करण / Version " + ver, 13, Ui.MUTED, false);
        v.setGravity(Gravity.CENTER);
        about.addView(v);
        TextView by = text("PlayRang Technologies", 15, Ui.SAFFRON_DARK, true);
        by.setGravity(Gravity.CENTER);
        by.setPadding(0, dp(6), 0, 0);
        about.addView(by);
        TextView desc = text("आपका AI मित्र — सवाल पूछिए, बातें कीजिए और फ़ोन के काम बोलकर कराइए।", 14, Ui.TEXT, false);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(6), 0, 0);
        about.addView(desc);

        // Language
        LinearLayout lc = card();
        lc.addView(text("भाषा चुनें / Language", 16, Ui.TEXT, true));
        lc.addView(text("\"अपने-आप\" में मित्र हर बार आपकी भाषा और लिपि पहचानता है (हिंदी, Hinglish, English, বাংলা, தமிழ், తెలుగు, मराठी, ગુજરાતી, "
                + "ਪੰਜਾਬੀ, ಕನ್ನಡ, മലയാളം, ଓଡ଼ିଆ, اردو …), उसी भाषा में जवाब देता है, उसी भाषा की आवाज़ में बोलता है और अगली बार उसी भाषा में सुनता है। "
                + "कोई एक भाषा चुनेंगे तो सुनना, बोलना और जवाब हमेशा उसी में होगा। भोजपुरी, मैथिली, मगही, हरियाणवी के लिए हिंदी आवाज़-पहचान इस्तेमाल होती है।", 13f, Ui.MUTED, false));
        final TextView cur = button("🌐  " + Lang.get(prefs.getString("lang", "auto")).label, false);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = dp(10);
        lc.addView(cur, ll);
        cur.setOnClickListener(x -> {
            String[] labels = new String[Lang.ALL.length];
            int sel = 0;
            String now = prefs.getString("lang", "auto");
            for (int i = 0; i < Lang.ALL.length; i++) { labels[i] = Lang.ALL[i].label; if (Lang.ALL[i].id.equals(now)) sel = i; }
            new android.app.AlertDialog.Builder(this)
                    .setTitle("भाषा चुनें")
                    .setSingleChoiceItems(labels, sel, (d, which) -> {
                        Lang l = Lang.ALL[which];
                        prefs.edit().putString("lang", l.id).remove("auto_sr").apply();
                        cur.setText("🌐  " + l.label);
                        Toast.makeText(this, l.label + " ✅", Toast.LENGTH_SHORT).show();
                        d.dismiss();
                    })
                    .setNegativeButton("रद्द करें", null)
                    .show();
        });

        // Voice
        LinearLayout voice = card();
        voice.addView(text("आवाज़", 16, Ui.TEXT, true));
        Switch speak = new Switch(this);
        speak.setText("जवाब बोलकर सुनाएँ");
        speak.setTextSize(15);
        speak.setTextColor(Ui.TEXT);
        speak.setMinHeight(dp(48));
        speak.setChecked(!prefs.getBoolean("muted", false));
        speak.setOnCheckedChangeListener((CompoundButton bt, boolean on) -> prefs.edit().putBoolean("muted", !on).apply());
        speak.setPadding(0, dp(8), 0, dp(4));
        voice.addView(speak);
        TextView ttsHelp = link("आवाज़ ठीक नहीं या आपकी भाषा की आवाज़ नहीं? Text-to-speech सेटिंग खोलें और Google TTS में अपनी भाषा की voice डाउनलोड करें");
        ttsHelp.setOnClickListener(x -> {
            try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
            catch (Exception e) { Toast.makeText(this, "यह सेटिंग नहीं खुल पाई", Toast.LENGTH_SHORT).show(); }
        });
        voice.addView(ttsHelp);

        // AI engine
        LinearLayout ai = card();
        ai.addView(text("AI इंजन", 16, Ui.TEXT, true));
        ai.addView(text("डिफ़ॉल्ट रूप से मित्र मुफ़्त AI सेवाएँ इस्तेमाल करता है (एक न चले तो दूसरी, फिर Wikipedia)। मुफ़्त सेवा में कभी-कभी कुछ सेकंड रुकना पड़ सकता है। "
                + "तेज़ और बिना रुकावट जवाबों के लिए अपनी मुफ़्त Google Gemini API key यहाँ डालें (वैकल्पिक)।", 13.5f, Ui.MUTED, false));
        final EditText key = new EditText(this);
        key.setHint("Gemini API key (AIza…)");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(prefs.getString("gemini_key", ""));
        key.setTextSize(15);
        key.setBackground(Ui.stroke(Color.WHITE, Color.parseColor("#E8D9CC"), dp(1), dp(10)));
        key.setPadding(dp(12), dp(10), dp(12), dp(10));
        key.setMinHeight(dp(48));
        key.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kl.topMargin = dp(10);
        ai.addView(key, kl);
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, dp(10), 0, 0);
        TextView save = button("सेव करें", true);
        save.setOnClickListener(x -> {
            String k = key.getText().toString().trim();
            prefs.edit().putString("gemini_key", k).apply();
            Toast.makeText(this, k.isEmpty() ? "मुफ़्त AI इस्तेमाल होगा" : "Gemini key सेव हो गई ✅", Toast.LENGTH_SHORT).show();
        });
        TextView remove = button("हटाएँ", false);
        remove.setOnClickListener(x -> {
            key.setText("");
            prefs.edit().remove("gemini_key").apply();
            Toast.makeText(this, "Key हटा दी, मुफ़्त AI इस्तेमाल होगा", Toast.LENGTH_SHORT).show();
        });
        btns.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        gap.leftMargin = dp(10);
        btns.addView(remove, gap);
        ai.addView(btns, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView getKey = link("मुफ़्त Gemini key पाएँ: aistudio.google.com");
        getKey.setOnClickListener(x -> open("https://aistudio.google.com/apikey"));
        ai.addView(getKey);
        ai.addView(text("🎙️ Live बात (ChatGPT जैसी आवाज़ वाली बातचीत) के लिए यही key चाहिए: aistudio.google.com खोलें → Google से लॉगिन → \"Get API key\" → \"Create API key\" → कॉपी करके ऊपर डालें और सेव करें। मुफ़्त है।",
                13f, Ui.MUTED, false));

        // Live voice
        ai.addView(text("Live आवाज़", 15, Ui.TEXT, true));
        final String[][] voices = {{"Puck", "Puck (पुरुष, खुशमिज़ाज)"}, {"Charon", "Charon (पुरुष, गंभीर)"}, {"Orus", "Orus (पुरुष)"},
                {"Kore", "Kore (महिला)"}, {"Aoede", "Aoede (महिला, मधुर)"}};
        android.widget.RadioGroup vg = new android.widget.RadioGroup(this);
        String curVoice = prefs.getString("live_voice", "Puck");
        for (int vi = 0; vi < voices.length; vi++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setId(4000 + vi);
            rb.setText(voices[vi][1]);
            rb.setTextColor(Ui.TEXT);
            rb.setTextSize(14);
            rb.setMinHeight(dp(48));
            if (voices[vi][0].equals(curVoice)) rb.setChecked(true);
            vg.addView(rb);
        }
        vg.setOnCheckedChangeListener((group, id) -> {
            int vi = id - 4000;
            if (vi >= 0 && vi < voices.length) prefs.edit().putString("live_voice", voices[vi][0]).apply();
        });
        ai.addView(vg);

        // Always listen (wake word)
        LinearLayout wake = card();
        wake.addView(text("हमेशा सुनें (‘मित्र’ बोलकर जगाएँ)", 16, Ui.TEXT, true));
        wake.addView(text("‘मित्र’ बोलिए और सवाल पूछिए (फ़ोन लॉक हो तब भी)। ‘मित्र’ शब्द फ़ोन पर ही, चुपचाप (बिना बीप) पहचाना जाता है, आवाज़ रिकॉर्ड/अपलोड नहीं होती। "
                + "बैटरी थोड़ी ज़्यादा लगती है और एक नोटिफ़िकेशन दिखता है।", 13.5f, Ui.MUTED, false));
        wakeSwitch = new Switch(this);
        wakeSwitch.setText("हमेशा सुनें");
        wakeSwitch.setTextSize(15);
        wakeSwitch.setTextColor(Ui.TEXT);
        wakeSwitch.setMinHeight(dp(48));
        wakeSwitch.setPadding(0, dp(8), 0, dp(4));
        wakeSwitch.setChecked(WakeService.enabled(this));
        wakeSwitch.setOnCheckedChangeListener((CompoundButton bt, boolean on) -> {
            if (on == WakeService.enabled(this)) return;
            if (on) {
                startActivity(new Intent(this, WakeSetupActivity.class).putExtra("enable", true));
            } else {
                prefs.edit().putBoolean(WakeService.PREF_ON, false).apply();
                WakeService.stop(this);
                Toast.makeText(this, "हमेशा सुनें बंद", Toast.LENGTH_SHORT).show();
            }
        });
        wake.addView(wakeSwitch);
        if (!ModelManager.ready(this)) {
            wake.addView(text("⚠️ ‘मित्र’ सुनने के लिए एक बार ऑफ़लाइन आवाज़ मॉडल (~44 MB) डाउनलोड करें — नीचे ‘सेटअप’ दबाएँ। तब तक ‘हमेशा सुनें’ बंद रहेगा।",
                    13f, Ui.SAFFRON_DARK, false));
        }
        Switch screenOff = new Switch(this);
        screenOff.setText("स्क्रीन बंद पर भी सुनें");
        screenOff.setTextSize(15);
        screenOff.setTextColor(Ui.TEXT);
        screenOff.setMinHeight(dp(48));
        screenOff.setPadding(0, dp(8), 0, dp(2));
        screenOff.setChecked(prefs.getBoolean(WakeService.PREF_SCREEN_OFF, false));
        screenOff.setOnCheckedChangeListener((CompoundButton bt, boolean on) -> {
            prefs.edit().putBoolean(WakeService.PREF_SCREEN_OFF, on).apply();
            WakeService.refresh(this);
        });
        wake.addView(screenOff);
        wake.addView(text("बंद रहने पर (बैटरी बचत) मित्र सिर्फ़ स्क्रीन चालू होने पर ‘मित्र’ सुनता है।", 12.5f, Ui.MUTED, false));
        TextView setup = button("🛠️ सेटअप (बैटरी, Autostart, मॉडल)", false);
        setup.setOnClickListener(x -> startActivity(new Intent(this, WakeSetupActivity.class)));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.topMargin = dp(8);
        wl.bottomMargin = dp(8);
        wake.addView(setup, wl);
        wake.addView(text("बातचीत मोड और 🎙️ Live बात: चैट में ऊपर ⋮ मेनू दबाएँ — फ़ोन कॉल की तरह लगातार बात कीजिए, बंद करने के लिए “बस” बोलें।", 13f, Ui.MUTED, false));

        // Chat
        LinearLayout chat = card();
        chat.addView(text("बातचीत", 16, Ui.TEXT, true));
        TextView clear = button("बातचीत साफ़ करें", false);
        clear.setOnClickListener(x -> {
            prefs.edit().putBoolean("clear_chat", true).apply();
            Toast.makeText(this, "बातचीत साफ़ हो गई", Toast.LENGTH_SHORT).show();
            finish();
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = dp(8);
        chat.addView(clear, cl);

        // Privacy
        LinearLayout priv = card();
        priv.addView(text("निजता / Privacy", 16, Ui.TEXT, true));
        priv.addView(text("माइक की आवाज़ सिर्फ़ बोले गए शब्दों को टेक्स्ट में बदलने के लिए इस्तेमाल होती है। ‘हमेशा सुनें’ में ‘मित्र’ शब्द फ़ोन पर ही पहचाना जाता है — आवाज़ न रिकॉर्ड होती है, न अपलोड। "
                + "आपके सवाल का सिर्फ़ टेक्स्ट जवाब पाने के लिए AI सेवा (और ज़रूरत पड़ने पर Wikipedia/DuckDuckGo) को भेजा जाता है। "
                + "हम अपने किसी सर्वर पर आपकी बातचीत नहीं रखते। नाम से कॉल/मैसेज के लिए कॉन्टैक्ट्स सिर्फ़ फ़ोन पर पढ़े जाते हैं — न अपलोड होते हैं, न AI को भेजे जाते हैं।", 13.5f, Ui.MUTED, false));
        TextView pp = link("प्राइवेसी पॉलिसी पढ़ें / Privacy Policy");
        pp.setOnClickListener(x -> open(PRIVACY_URL));
        priv.addView(pp);
        TextView mail = link("संपर्क: mannukumarbank22@gmail.com");
        mail.setOnClickListener(x -> {
            try {
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:mannukumarbank22@gmail.com"))
                        .putExtra(Intent.EXTRA_SUBJECT, "Mitra app"));
            } catch (Exception e) { Toast.makeText(this, "ईमेल ऐप नहीं मिला", Toast.LENGTH_SHORT).show(); }
        });
        priv.addView(mail);

        TextView foot = text("© PlayRang Technologies", 12, Ui.MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(12), 0, 0);
        body.addView(foot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "ब्राउज़र नहीं मिला", Toast.LENGTH_SHORT).show(); }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(Color.WHITE, dp(16)));
        c.setElevation(dp(1.5f));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        body.addView(c, lp);
        return c;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(dp(2), 1f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView link(String s) {
        TextView t = text(s, 14, Ui.SAFFRON_DARK, false);
        t.setPadding(0, dp(8), 0, dp(8));
        t.setMinHeight(dp(48));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        t.setBackground(Ui.ripple(null, 0x22F26A1B));
        t.setClickable(true);
        return t;
    }

    private TextView button(String s, boolean primary) {
        TextView t = text(s, 15, primary ? Color.WHITE : Ui.SAFFRON_DARK, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.ripple(primary ? Ui.round(Ui.SAFFRON, dp(24)) : Ui.stroke(Color.WHITE, Ui.SAFFRON, dp(1), dp(24)),
                primary ? 0x55FFFFFF : 0x33F26A1B));
        t.setPadding(dp(18), dp(8), dp(18), dp(8));
        t.setMinHeight(dp(48));
        t.setClickable(true);
        return t;
    }

    @SuppressWarnings("unused")
    private void unused(View v) { }
}

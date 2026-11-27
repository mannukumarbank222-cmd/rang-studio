package com.playrang.rangstudio;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Hindi setup screen for "हमेशा सुनें": permissions, battery, Xiaomi/Redmi autostart, offline model download. */
public class WakeSetupActivity extends Activity {
    private static final int REQ_PERMS = 21;
    private SharedPreferences prefs;
    private LinearLayout body;
    private Switch toggle;
    private TextView micState, batState, overlayState, modelState;
    private ProgressBar bar;
    private boolean enableAfterPerms;

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
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(Ui.gradient(Ui.SAFFRON_LIGHT, Ui.SAFFRON_DARK));
        header.setPadding(dp(4), dp(4), dp(16), dp(4));
        header.setMinimumHeight(dp(56));
        header.setElevation(dp(4));
        TextView back = text("←", 24, Color.WHITE, false);
        back.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.ripple(null, 0x40FFFFFF));
        Ui.touch(back);
        back.setContentDescription("वापस");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("हमेशा सुनें — सेटअप", 19, Color.WHITE, true);
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

        LinearLayout c0 = card();
        c0.addView(text("“मित्र” बोलकर जगाएँ", 17, Ui.TEXT, true));
        c0.addView(text("चालू करने पर मित्र फ़ोन लॉक या स्क्रीन बंद होने पर भी ‘मित्र’ शब्द सुनता रहेगा। ‘मित्र’ बोलिए, फिर अपना सवाल या काम — "
                + "जैसे “मित्र, सोनू को कॉल करो”। ‘मित्र’ शब्द फ़ोन पर ही पहचाना जाता है; आवाज़ रिकॉर्ड या अपलोड नहीं होती। "
                + "इससे बैटरी थोड़ी ज़्यादा खर्च होगी और एक स्थायी नोटिफ़िकेशन दिखेगा।", 13.5f, Ui.MUTED, false));
        toggle = new Switch(this);
        toggle.setText("हमेशा सुनें");
        toggle.setTextSize(16);
        toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setTextColor(Ui.TEXT);
        toggle.setPadding(0, dp(10), 0, dp(4));
        toggle.setMinHeight(dp(48));
        toggle.setChecked(WakeService.enabled(this));
        toggle.setOnCheckedChangeListener((bt, on) -> { if (on) enable(); else disable(); });
        c0.addView(toggle);

        LinearLayout c1 = card();
        c1.addView(text("1. अनुमतियाँ", 16, Ui.TEXT, true));
        micState = text("", 13.5f, Ui.MUTED, false);
        c1.addView(micState);
        c1.addView(button("🎤 माइक और नोटिफ़िकेशन की अनुमति दें", () -> askPerms(false)));

        LinearLayout c2 = card();
        c2.addView(text("2. ऑफ़लाइन ‘मित्र’ पहचान मॉडल (44 MB, एक बार)", 16, Ui.TEXT, true));
        modelState = text("", 13.5f, Ui.MUTED, false);
        c2.addView(modelState);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14));
        barLp.topMargin = dp(6);
        c2.addView(bar, barLp);
        c2.addView(button("⬇️ मॉडल डाउनलोड करें", this::downloadModel));

        LinearLayout c3 = card();
        c3.addView(text("3. बैटरी — “Unrestricted / कोई रोक नहीं”", 16, Ui.TEXT, true));
        batState = text("", 13.5f, Ui.MUTED, false);
        c3.addView(batState);
        c3.addView(button("🔋 बैटरी रोक हटाएँ", this::battery));

        LinearLayout c4 = card();
        c4.addView(text("4. Xiaomi / Redmi / POCO फ़ोन के लिए", 16, Ui.TEXT, true));
        c4.addView(text("Redmi फ़ोन बैकग्राउंड ऐप बंद कर देते हैं। Autostart चालू करें, और ‘अन्य अनुमतियाँ’ में “लॉक स्क्रीन पर दिखाएँ”, "
                + "“बैकग्राउंड में पॉप-अप विंडो खोलें” और “पॉप-अप विंडो दिखाएँ” चालू करें।", 13.5f, Ui.MUTED, false));
        c4.addView(button("🚀 Autostart चालू करें", this::autostart));
        c4.addView(button("⚙️ अन्य अनुमतियाँ (MIUI)", this::miuiPerms));
        overlayState = text("", 13.5f, Ui.MUTED, false);
        c4.addView(overlayState);
        c4.addView(button("🪟 दूसरे ऐप्स के ऊपर दिखाएँ", this::overlay));
        TextView tip = text("💡 टिप: Recents (हाल के ऐप्स) में मित्र को नीचे खींचकर / देर तक दबाकर 🔒 लॉक करें, ताकि ‘सब साफ़ करें’ से बंद न हो।",
                13.5f, Ui.TEXT, false);
        tip.setPadding(0, dp(10), 0, 0);
        c4.addView(tip);

        LinearLayout c5 = card();
        c5.addView(text("संवेदनशीलता", 16, Ui.TEXT, true));
        c5.addView(text("ग़लती से जागता हो तो ‘कम’ चुनें; सुनता न हो तो ‘ज़्यादा’।", 13.5f, Ui.MUTED, false));
        LinearLayout row = new LinearLayout(this);
        String[] names = {"कम", "सामान्य", "ज़्यादा"};
        final List<TextView> opts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView o = text(names[i], 15, Ui.SAFFRON_DARK, true);
            o.setPadding(dp(12), dp(8), dp(12), dp(8));
            o.setGravity(Gravity.CENTER);
            o.setMinHeight(dp(48));
            Ui.oneLine(o);
            o.setOnClickListener(v -> { prefs.edit().putInt("wake_sens", idx).apply(); paintSens(opts); });
            opts.add(o);
            // equal-width options: always fit the card, even on a narrow phone with a big font
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) lp.rightMargin = dp(8);
            lp.topMargin = dp(8);
            row.addView(o, lp);
        }
        paintSens(opts);
        c5.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (getIntent().getBooleanExtra("enable", false) && !WakeService.enabled(this)) toggle.setChecked(true);
    }

    private void paintSens(List<TextView> opts) {
        int s = prefs.getInt("wake_sens", 1);
        for (int i = 0; i < opts.size(); i++) {
            boolean sel = i == s;
            opts.get(i).setTextColor(sel ? Color.WHITE : Ui.SAFFRON_DARK);
            opts.get(i).setBackground(sel ? Ui.round(Ui.SAFFRON, dp(18)) : Ui.stroke(Color.WHITE, Ui.SAFFRON, dp(1), dp(18)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean mic = WakeService.hasMic(this);
        boolean notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        micState.setText((mic ? "✅ माइक" : "❌ माइक") + "    " + (notif ? "✅ नोटिफ़िकेशन" : "❌ नोटिफ़िकेशन"));
        boolean bat = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            bat = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        batState.setText(bat ? "✅ बैटरी रोक हटी हुई है" : "❌ अभी बैटरी बचत मित्र को रोक सकती है");
        boolean ov = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        overlayState.setText(ov ? "✅ दूसरे ऐप्स के ऊपर दिखाने की अनुमति है" : "❌ दूसरे ऐप्स के ऊपर दिखाने की अनुमति नहीं (लॉक स्क्रीन पर जवाब के लिए ज़रूरी)");
        if (!ModelManager.isDownloading()) {
            boolean ready = ModelManager.ready(this);
            modelState.setText(ready ? "✅ मॉडल तैयार है — ‘मित्र’ फ़ोन पर ही पहचाना जाएगा" : "⚠️ मॉडल नहीं है — एक बार डाउनलोड करें (~44 MB)। तब तक ‘हमेशा सुनें’ बंद रहेगा।");
            bar.setProgress(ready ? 100 : 0);
        }
        if (toggle.isChecked() != WakeService.enabled(this)) toggle.setChecked(WakeService.enabled(this));
    }

    // ------------------------------------------------------------------ actions
    private void enable() {
        if (!WakeService.hasMic(this)) { enableAfterPerms = true; askPerms(true); return; }
        prefs.edit().putBoolean(WakeService.PREF_ON, true).apply();
        if (ModelManager.ready(this)) {
            WakeService.start(this);
            Toast.makeText(this, "हमेशा सुनें चालू ✅ — ‘मित्र’ बोलकर देखें", Toast.LENGTH_LONG).show();
        } else {
            // the wake word needs the offline model; it starts by itself when the download finishes
            Toast.makeText(this, "आवाज़ मॉडल डाउनलोड हो रहा है — पूरा होते ही ‘हमेशा सुनें’ चालू हो जाएगा", Toast.LENGTH_LONG).show();
            downloadModel();
        }
    }

    private void disable() {
        prefs.edit().putBoolean(WakeService.PREF_ON, false).apply();
        WakeService.stop(this);
        Toast.makeText(this, "हमेशा सुनें बंद", Toast.LENGTH_SHORT).show();
    }

    private void askPerms(boolean forEnable) {
        if (Build.VERSION.SDK_INT < 23) { if (forEnable) enable(); return; }
        List<String> p = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (p.isEmpty()) { if (forEnable) enable(); else Toast.makeText(this, "अनुमतियाँ पहले से हैं ✅", Toast.LENGTH_SHORT).show(); return; }
        requestPermissions(p.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refresh();
        if (code != REQ_PERMS) return;
        if (enableAfterPerms) {
            enableAfterPerms = false;
            if (WakeService.hasMic(this)) enable();
            else {
                toggle.setChecked(false);
                Toast.makeText(this, "माइक की अनुमति के बिना मित्र सुन नहीं सकता। सेटिंग्स › ऐप्स › मित्र › अनुमतियाँ में चालू करें।", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void downloadModel() {
        if (ModelManager.ready(this)) { Toast.makeText(this, "मॉडल पहले से तैयार है ✅", Toast.LENGTH_SHORT).show(); return; }
        if (ModelManager.isDownloading()) { Toast.makeText(this, "डाउनलोड चल रहा है…", Toast.LENGTH_SHORT).show(); return; }
        modelState.setText("डाउनलोड शुरू हो रहा है…");
        new Thread(() -> {
            String err = ModelManager.download(getApplicationContext(), (pct, msg) -> runOnUiThread(() -> { bar.setProgress(pct); modelState.setText(msg); }));
            runOnUiThread(() -> {
                if (err != null) { modelState.setText("❌ " + err); return; }
                modelState.setText("✅ मॉडल तैयार है — ‘मित्र’ फ़ोन पर ही पहचाना जाएगा");
                bar.setProgress(100);
                try { ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(WakeService.NOTIF_MODEL); } catch (Exception ignored) { }
                if (!isFinishing() && WakeService.enabled(this) && WakeService.hasMic(this)) {
                    WakeService.stop(this); WakeService.start(this);
                    Toast.makeText(this, "हमेशा सुनें चालू ✅ — ‘मित्र’ बोलकर देखें", Toast.LENGTH_LONG).show();
                }
            });
        }, "mitra-model-ui").start();
    }

    private void battery() {
        if (Build.VERSION.SDK_INT < 23) return;
        // v4.0: no REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission (store policy) — open the system list instead
        if (tryStart(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            Toast.makeText(this, "सूची में ‘सभी ऐप्स’ चुनकर ‘मित्र’ पर ‘Don't optimise / ऑप्टिमाइज़ न करें’ चुनें। Redmi में ऐप जानकारी › बैटरी सेवर › ‘No restrictions’।",
                    Toast.LENGTH_LONG).show();
            return;
        }
        appDetails();
    }

    private void autostart() {
        Intent i = new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        if (tryStart(i)) { Toast.makeText(this, "सूची में ‘मित्र’ का Autostart चालू करें", Toast.LENGTH_LONG).show(); return; }
        i = new Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT);
        if (tryStart(i)) return;
        Toast.makeText(this, "ऐप जानकारी में ‘Autostart’ चालू करें", Toast.LENGTH_LONG).show();
        appDetails();
    }

    private void miuiPerms() {
        Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", getPackageName());
        if (tryStart(i)) return;
        i = new Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter",
                "com.miui.permcenter.permissions.AppPermissionsEditorActivity").putExtra("extra_pkgname", getPackageName());
        if (tryStart(i)) return;
        Toast.makeText(this, "ऐप जानकारी › अन्य अनुमतियाँ खोलें", Toast.LENGTH_LONG).show();
        appDetails();
    }

    private void overlay() {
        if (Build.VERSION.SDK_INT < 23) return;
        if (!tryStart(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))) appDetails();
    }

    private void appDetails() {
        tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private boolean tryStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ views
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

    private TextView button(String s, Runnable r) {
        TextView t = text(s, 15, Ui.SAFFRON_DARK, true);
        t.setBackground(Ui.ripple(Ui.stroke(Color.WHITE, Ui.SAFFRON, dp(1), dp(24)), 0x33F26A1B));
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(48));
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }
}

package com.playrang.rangstudio;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Always-listening foreground service. Detects the wake word "मित्र" on the phone with an offline Vosk grammar
 * recognizer (audio never leaves the phone and is never saved). Falls back to Android's SpeechRecognizer when the
 * offline model is not downloaded yet. On a wake word it opens {@link VoiceActivity} (also over the lock screen).
 */
public class WakeService extends Service {
    static final String ACT_START = "com.playrang.rangstudio.WAKE_START", ACT_STOP = "com.playrang.rangstudio.WAKE_STOP",
            ACT_PAUSE = "com.playrang.rangstudio.WAKE_PAUSE", ACT_RESUME = "com.playrang.rangstudio.WAKE_RESUME";
    static final String CH_LISTEN = "mitra_listen", CH_WAKE = "mitra_wake";
    static final int NOTIF_ID = 401, NOTIF_WAKE = 402;
    static final String PREF_ON = "always_listen";

    static volatile boolean running;
    private static final Map<String, Long> pauses = new HashMap<>(); // reason -> until (ms)

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean alive;
    private Thread voskThread;
    private PowerManager.WakeLock wl;
    private SpeechRecognizer sr;
    private boolean srMode, srListening;
    private String lastState = "";
    private SharedPreferences prefs;

    // ------------------------------------------------------------------ static helpers
    static boolean enabled(Context c) { return c.getSharedPreferences("mitra", MODE_PRIVATE).getBoolean(PREF_ON, false); }

    static boolean hasMic(Context c) {
        return Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** Starts the service if the setting is on and the mic permission is granted. Call from a visible activity. */
    static void startIfEnabled(Context c) {
        if (enabled(c) && hasMic(c)) start(c);
    }

    static void start(Context c) {
        Intent i = new Intent(c, WakeService.class).setAction(ACT_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, WakeService.class));
    }

    /** Pause listening for a reason ("ui", "voice"); ms <= 0 means until resume(). */
    static void pause(Context c, String why, long ms) {
        synchronized (pauses) { pauses.put(why, ms <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + ms); }
        if (running) send(c, ACT_PAUSE);
    }

    static void resume(Context c, String why) {
        synchronized (pauses) { pauses.remove(why); }
        if (running) send(c, ACT_RESUME);
    }

    private static void send(Context c, String action) {
        try { c.startService(new Intent(c, WakeService.class).setAction(action)); } catch (Exception ignored) { }
    }

    private boolean inCall() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return false;
        int m = am.getMode();
        return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE;
    }

    /** @return the pause reason, or null when we should listen */
    private String pausedWhy() {
        if (inCall()) return "call";
        long now = System.currentTimeMillis();
        synchronized (pauses) {
            String r = null;
            for (Map.Entry<String, Long> e : new ArrayList<>(pauses.entrySet())) {
                if (e.getValue() < now) pauses.remove(e.getKey()); else r = e.getKey();
            }
            return r;
        }
    }

    // ------------------------------------------------------------------ lifecycle
    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("mitra", MODE_PRIVATE);
        channels(this);
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel a = new NotificationChannel(CH_LISTEN, "हमेशा सुनें", NotificationManager.IMPORTANCE_LOW);
        a.setDescription("मित्र 'मित्र' शब्द सुनने के लिए चालू है");
        a.setShowBadge(false);
        nm.createNotificationChannel(a);
        NotificationChannel b = new NotificationChannel(CH_WAKE, "मित्र जवाब", NotificationManager.IMPORTANCE_HIGH);
        b.setDescription("'मित्र' बोलने पर जवाब स्क्रीन दिखाने के लिए");
        b.setSound(null, null);
        nm.createNotificationChannel(b);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? ACT_START : intent.getAction();
        if (ACT_STOP.equals(a)) {
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running || ACT_START.equals(a)) {
            if (!goForeground("मित्र सुन रहा है… 'मित्र' बोलें")) { stopSelf(); return START_NOT_STICKY; }
        }
        if (!running) {
            running = true;
            alive = true;
            acquireWakeLock();
            begin();
        }
        if (ACT_RESUME.equals(a) && srMode) main.postDelayed(this::srListen, 400);
        if (ACT_PAUSE.equals(a) && srMode) srStop();
        return START_STICKY;
    }

    private boolean goForeground(String text) {
        try {
            Notification n = build(text);
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTIF_ID, n);
            return true;
        } catch (Exception e) {
            // Android 11+: a microphone service may not start from the background (e.g. right after boot).
            return false;
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
    }

    private Notification build(String text) {
        int fl = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), fl);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, WakeService.class).setAction(ACT_STOP), fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_LISTEN) : new Notification.Builder(this);
        b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle(text)
                .setContentText("बंद करने के लिए ‘रोकें’ दबाएँ")
                .setOngoing(true).setShowWhen(false).setContentIntent(open)
                .addAction(new Notification.Action.Builder(0, "रोकें", stop).build())
                .addAction(new Notification.Action.Builder(0, "खोलें", open).build());
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    private void state(String s) {
        if (s.equals(lastState)) return;
        lastState = s;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        try { nm.notify(NOTIF_ID, build(s)); } catch (Exception ignored) { }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mitra:wake");
            wl.setReferenceCounted(false);
            wl.acquire();
        } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        alive = false;
        running = false;
        main.removeCallbacksAndMessages(null);
        srStop();
        if (sr != null) { try { sr.destroy(); } catch (Exception ignored) { } sr = null; }
        if (voskThread != null) voskThread.interrupt();
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ engine choice
    private void begin() {
        if (ModelManager.ready(this)) { startVosk(); return; }
        // No offline model yet: listen with the Android recognizer and fetch the model in the background.
        startSr();
        if (!ModelManager.isDownloading()) {
            new Thread(() -> {
                String err = ModelManager.download(this, (pct, msg) -> { if (srMode) state("मित्र सुन रहा है… (ऑफ़लाइन मॉडल " + pct + "%)"); });
                if (err == null && alive) main.post(() -> { srMode = false; srStop(); if (sr != null) { sr.destroy(); sr = null; } startVosk(); });
            }, "mitra-model").start();
        }
    }

    // ------------------------------------------------------------------ offline (Vosk) detector
    private void startVosk() {
        srMode = false;
        voskThread = new Thread(this::voskLoop, "mitra-wake");
        voskThread.start();
    }

    private void voskLoop() {
        Model model;
        try {
            model = new Model(ModelManager.dir(this).getAbsolutePath());
        } catch (Throwable e) {
            main.post(() -> { ModelManager.deleteAll(ModelManager.dir(this)); startSr(); });
            return;
        }
        final int rate = 16000;
        int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        byte[] buf = new byte[3200]; // 100 ms
        AudioRecord rec = null;
        Recognizer gram = null;
        ByteArrayOutputStream seg = new ByteArrayOutputStream();
        long total = 0, segStart = 0;
        long lastCheck = 0;
        boolean paused = true;
        try {
            while (alive && !Thread.currentThread().isInterrupted()) {
                long now = System.currentTimeMillis();
                if (now - lastCheck > 700 || paused) {
                    lastCheck = now;
                    String why = pausedWhy();
                    if (why != null) {
                        if (!paused) { paused = true; if (rec != null) { rec.stop(); rec.release(); rec = null; } }
                        state("call".equals(why) ? "मित्र रुका है — कॉल चल रही है" : "मित्र रुका है — ऐप में बात हो रही है");
                        Thread.sleep(500);
                        continue;
                    }
                    if (paused) {
                        paused = false;
                        rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT, Math.max(min, rate * 2));
                        if (rec.getState() != AudioRecord.STATE_INITIALIZED) { rec.release(); rec = null; paused = true; Thread.sleep(2000); continue; }
                        rec.startRecording();
                        if (gram != null) gram.close();
                        gram = new Recognizer(model, rate, WakeWord.VOSK_GRAMMAR);
                        gram.setWords(true);
                        seg.reset();
                        total = 0;
                        segStart = 0;
                        state("मित्र सुन रहा है… 'मित्र' बोलें");
                    }
                }
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) { Thread.sleep(50); continue; }
                seg.write(buf, 0, n);
                total += n / 2;
                boolean end = gram.acceptWaveForm(buf, n);
                if (end) {
                    byte[] audio = seg.toByteArray();
                    String cmd = check(model, gram.getResult(), audio, segStart / (double) rate);
                    seg.reset();
                    segStart = total;
                    if (cmd != null) {
                        rec.stop(); rec.release(); rec = null; paused = true;
                        pause(this, "voice", 20000); // VoiceActivity takes over the mic; auto-resume if it never opens
                        final String c = cmd;
                        main.post(() -> onWake(c));
                        Thread.sleep(800);
                    }
                } else if (seg.size() > rate * 2 * 12) {
                    // very long speech without a pause: keep the last 6 s
                    byte[] all = seg.toByteArray();
                    int keep = rate * 2 * 6;
                    seg.reset();
                    seg.write(all, all.length - keep, keep);
                    segStart += (all.length - keep) / 2;
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable e) {
            if (alive) main.postDelayed(() -> { if (alive) startVosk(); }, 3000);
        } finally {
            try { if (rec != null) { rec.stop(); rec.release(); } } catch (Exception ignored) { }
            if (gram != null) gram.close();
            model.close();
        }
    }

    private static List<WakeWord.W> words(String json) {
        List<WakeWord.W> out = new ArrayList<>();
        try {
            JSONArray r = new JSONObject(json).optJSONArray("result");
            if (r == null) return out;
            for (int i = 0; i < r.length(); i++) {
                JSONObject o = r.getJSONObject(i);
                out.add(new WakeWord.W(o.optString("word"), o.optDouble("conf", 1.0), o.optDouble("start"), o.optDouble("end")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Two-pass check of one speech segment. @return command text ("" for none) when the wake word started it; else null */
    private String check(Model model, String grammarJson, byte[] audio, double segStartSec) {
        double thr = WakeWord.threshold(prefs.getInt("wake_sens", 1));
        List<WakeWord.W> g = words(grammarJson);
        if (!WakeWord.hasCandidate(g, thr)) return null;
        // Vosk word times count from the recognizer start; make them relative to this segment
        boolean cumulative = false;
        for (WakeWord.W w : g) if (w.start >= segStartSec - 0.3) cumulative = true;
        List<WakeWord.W> rel = new ArrayList<>();
        for (WakeWord.W w : g) rel.add(cumulative ? new WakeWord.W(w.w, w.conf, w.start - segStartSec, w.end - segStartSec) : w);
        List<WakeWord.W> full = new ArrayList<>();
        Recognizer f = null;
        try {
            f = new Recognizer(model, 16000);
            f.setWords(true);
            f.acceptWaveForm(audio, audio.length);
            full = words(f.getFinalResult());
        } catch (Throwable ignored) {
        } finally {
            if (f != null) f.close();
        }
        if (full.isEmpty()) return "";
        return WakeWord.evaluate(rel, full, thr);
    }

    // ------------------------------------------------------------------ fallback: Android SpeechRecognizer loop
    private int srErrors;

    private void startSr() {
        srMode = true;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { state("मित्र: आवाज़ पहचान उपलब्ध नहीं — मॉडल डाउनलोड का इंतज़ार"); return; }
        state("मित्र सुन रहा है… 'मित्र' बोलें");
        srListen();
    }

    private void srListen() {
        if (!alive || !srMode || srListening) return;
        String why = pausedWhy();
        if (why != null) {
            state("call".equals(why) ? "मित्र रुका है — कॉल चल रही है" : "मित्र रुका है — ऐप में बात हो रही है");
            main.postDelayed(this::srListen, 1500);
            return;
        }
        state("मित्र सुन रहा है… 'मित्र' बोलें");
        try {
            if (sr == null) {
                sr = SpeechRecognizer.createSpeechRecognizer(this);
                sr.setRecognitionListener(new SrListener());
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            srListening = true;
            sr.startListening(i);
        } catch (Exception e) {
            srListening = false;
            main.postDelayed(this::srListen, 3000);
        }
    }

    private void srStop() {
        srListening = false;
        try { if (sr != null) sr.cancel(); } catch (Exception ignored) { }
    }

    private class SrListener implements RecognitionListener {
        public void onReadyForSpeech(Bundle p) { }
        public void onBeginningOfSpeech() { }
        public void onRmsChanged(float v) { }
        public void onBufferReceived(byte[] b) { }
        public void onEndOfSpeech() { }
        public void onPartialResults(Bundle b) { }
        public void onEvent(int t, Bundle p) { }
        public void onError(int error) {
            srListening = false;
            long delay = 250;
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                srErrors++;
                delay = Math.min(10000, 1000L * srErrors);
                if (sr != null && srErrors % 3 == 0) { try { sr.destroy(); } catch (Exception ignored) { } sr = null; }
            } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                    || error == SpeechRecognizer.ERROR_SERVER) {
                delay = 5000;
            } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                stopSelf();
                return;
            }
            main.postDelayed(WakeService.this::srListen, delay);
        }
        public void onResults(Bundle b) {
            srListening = false;
            srErrors = 0;
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null) for (String t : r) {
                String cmd = WakeWord.split(t);
                if (cmd != null) {
                    pause(WakeService.this, "voice", 20000);
                    onWake(cmd);
                    return;
                }
            }
            main.postDelayed(WakeService.this::srListen, 150);
        }
    }

    // ------------------------------------------------------------------ wake
    private void onWake(String cmd) {
        // With a Gemini key the wake word opens the Live conversation; otherwise the small voice screen.
        boolean live = getSharedPreferences("mitra", MODE_PRIVATE).getString("gemini_key", "").trim().length() > 10;
        Intent i = new Intent(this, live ? LiveActivity.class : VoiceActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(VoiceActivity.EXTRA_CMD, cmd == null ? "" : cmd);
        boolean canDraw = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        if (canDraw || Build.VERSION.SDK_INT < 29) {
            try { startActivity(i); } catch (Exception ignored) { }
        }
        // Full-screen notification: opens the voice screen over the lock screen when a direct start is not allowed.
        int fl = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 3, i, fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_WAKE) : new Notification.Builder(this);
        b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("जी, बोलिए…").setContentText("मित्र सुन रहा है")
                .setCategory(Notification.CATEGORY_CALL).setAutoCancel(true).setContentIntent(pi).setFullScreenIntent(pi, true);
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(30000);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MAX);
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_WAKE, b.build()); } catch (Exception ignored) { }
    }
}

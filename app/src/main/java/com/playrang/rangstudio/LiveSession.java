package com.playrang.rangstudio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One Gemini Live (BidiGenerateContent) voice session: 16 kHz mic audio streams up, 24 kHz speech streams down,
 * with barge-in, transcripts, function calls and automatic reconnect (session resumption).
 * Listener callbacks always run on the main thread.
 */
final class LiveSession {
    static final int S_CONNECTING = 0, S_LISTENING = 1, S_SPEAKING = 2, S_MUTED = 3;
    private static final String WS = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=";
    private static final int IN_RATE = 16000, OUT_RATE = 24000;

    interface Listener {
        void onStatus(int state);
        void onLevel(float level);
        void onUserText(String text);
        void onModelText(String text);
        void onTurnDone();
        void onToolCall(String id, String name, JSONObject args);
        void onConnected(String model, boolean search);
        /** The session could not start or was lost for good. keyProblem = the API key itself was refused. */
        void onFatal(String message, boolean keyProblem);
    }

    private final Context ctx;
    private final String key, system, voice;
    private final Listener ui;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;

    private volatile WsClient ws;
    private volatile boolean ready, stopped, muted, muteSent;
    private volatile int gen;
    private volatile String resumeHandle;
    private LiveModels.Attempt current;
    private int reconnects;
    private String firstText;

    private Thread micThread, playThread;
    private AudioRecord rec;
    private AudioTrack track;
    private final LinkedBlockingQueue<byte[]> playQ = new LinkedBlockingQueue<>();
    private volatile int playGen;
    private volatile long framesWritten;
    private volatile boolean audible, turnOpen;
    private boolean aec;
    private final StringBuilder userBuf = new StringBuilder(), modelBuf = new StringBuilder();

    private AudioManager am;
    private int oldMode;
    private boolean oldSpeaker;

    LiveSession(Context ctx, String key, String system, String voice, Listener ui) {
        this.ctx = ctx.getApplicationContext();
        this.key = key.trim();
        this.system = system;
        this.voice = voice == null || voice.isEmpty() ? "Puck" : voice;
        this.ui = ui;
        this.prefs = ctx.getSharedPreferences("mitra", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ public API
    void start(String firstText) {
        this.firstText = firstText;
        post(() -> ui.onStatus(S_CONNECTING));
        new Thread(() -> connectAll(false), "mitra-live-connect").start();
    }

    void setMuted(boolean m) {
        muted = m;
        if (!m) muteSent = false;
        post(() -> ui.onStatus(m ? S_MUTED : (audible ? S_SPEAKING : S_LISTENING)));
    }

    boolean isMuted() { return muted; }

    void sendText(String t) {
        try { send(new JSONObject().put("realtimeInput", new JSONObject().put("text", t)).toString()); } catch (Exception ignored) { }
    }

    void sendToolResponse(String id, String name, JSONObject response) {
        try {
            JSONObject fr = new JSONObject().put("name", name).put("response", response);
            if (id != null && !id.isEmpty()) fr.put("id", id);
            send(new JSONObject().put("toolResponse", new JSONObject().put("functionResponses", new JSONArray().put(fr))).toString());
        } catch (Exception ignored) { }
    }

    /** Stop the model's current speech (e.g. when an app is opened). */
    void stopSpeaking() { clearPlayback(); }

    void stop() {
        stopped = true;
        ready = false;
        WsClient w = ws;
        ws = null;
        if (w != null) new Thread(w::close).start();
        clearPlayback();
        if (micThread != null) micThread.interrupt();
        if (playThread != null) playThread.interrupt();
        restoreAudio();
    }

    private void post(Runnable r) { if (!stopped) main.post(r); }

    private boolean send(String json) {
        WsClient w = ws;
        return w != null && ready && w.sendText(json);
    }

    // ------------------------------------------------------------------ connection
    private void connectAll(boolean resume) {
        String lastReason = "";
        List<LiveModels.Attempt> list = LiveModels.attempts(prefs.getInt("live_attempt", 0));
        if (resume && current != null) { list.clear(); list.add(current); }
        for (LiveModels.Attempt a : list) {
            if (stopped) return;
            int[] code = {0};
            String[] reason = {""};
            boolean ok = tryOnce(a, resume ? resumeHandle : null, code, reason);
            if (ok) {
                current = a;
                reconnects = 0; // a good connection: the next drop gets the full set of retries again
                prefs.edit().putInt("live_attempt", LiveModels.indexOf(a)).apply();
                final LiveModels.Attempt fa = a;
                if (!resume) post(() -> ui.onConnected(fa.model, fa.search));
                startAudio();
                if (!resume) {
                    String ft = firstText;
                    sendText(ft != null && ft.trim().length() > 0 ? ft
                            : "(यूज़र ने अभी Live बातचीत शुरू की है। एक बहुत छोटा, गर्मजोशी भरा अभिवादन बोलो, जैसे 'हाँ जी, बोलिए! क्या मदद करूँ?')");
                }
                post(() -> ui.onStatus(muted ? S_MUTED : S_LISTENING));
                return;
            }
            lastReason = reason[0];
            if (LiveModels.isKeyProblem(code[0], reason[0])) {
                final String r = reason[0];
                post(() -> ui.onFatal("Gemini API key काम नहीं कर रही: " + r, true));
                return;
            }
            if (LiveModels.isQuota(code[0], reason[0])) {
                final String r = reason[0];
                post(() -> ui.onFatal("Gemini की मुफ़्त सीमा अभी पूरी हो गई है (" + r + ")", false));
                return;
            }
            if (resume) break;
        }
        if (resume && !stopped) { // could not resume: start a fresh session
            resumeHandle = null;
            current = null;
            connectAll(false);
            return;
        }
        final String r = lastReason;
        post(() -> ui.onFatal("Live बातचीत शुरू नहीं हो पाई" + (r.isEmpty() ? "" : " (" + r + ")"), false));
    }

    private boolean tryOnce(LiveModels.Attempt a, String handle, int[] code, String[] reason) {
        final int myGen = ++gen;
        final CountDownLatch setup = new CountDownLatch(1);
        final boolean[] setupOk = {false};
        WsClient w = new WsClient();
        try {
            w.connect(WS + URLEncoder.encode(key, "UTF-8"), new WsClient.Listener() {
                @Override public void onMessage(String text) {
                    if (myGen != gen) return;
                    if (!setupOk[0] && text.contains("\"setupComplete\"")) { setupOk[0] = true; setup.countDown(); return; }
                    handle(text);
                }
                @Override public void onClosed(int c, String r) {
                    if (!setupOk[0]) { if (setup.getCount() > 0) { code[0] = c; reason[0] = r; } setup.countDown(); return; }
                    if (myGen == gen) lost(c, r);
                }
            });
        } catch (WsClient.HandshakeException e) {
            code[0] = -1;
            reason[0] = e.status + " " + e.body;
            return false;
        } catch (Exception e) {
            code[0] = -1;
            reason[0] = "इंटरनेट से जुड़ नहीं पाया";
            return false;
        }
        ws = w;
        boolean sent;
        try { sent = w.sendText(setupJson(a, handle)); } catch (Exception e) { sent = false; }
        if (!sent) { code[0] = -1; reason[0] = "setup भेज नहीं पाया"; ws = null; return false; }
        try { setup.await(15, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        if (setupOk[0] && !stopped) { ready = true; return true; }
        setup.countDown(); // late close callbacks must not overwrite the reason
        if (reason[0].isEmpty()) reason[0] = "सर्वर ने जवाब नहीं दिया";
        ws = null;
        w.close();
        return false;
    }

    private void lost(int c, String r) {
        ready = false;
        if (stopped) return;
        clearPlayback();
        if (LiveModels.isKeyProblem(c, r)) { post(() -> ui.onFatal("Gemini API key काम नहीं कर रही: " + r, true)); return; }
        if (reconnects++ >= 4) { post(() -> ui.onFatal("बातचीत टूट गई (" + r + ")", false)); return; }
        post(() -> ui.onStatus(S_CONNECTING));
        new Thread(() -> connectAll(true), "mitra-live-reconnect").start();
    }

    private String setupJson(LiveModels.Attempt a, String handle) throws Exception {
        JSONObject gc = new JSONObject()
                .put("responseModalities", new JSONArray().put("AUDIO"))
                .put("speechConfig", new JSONObject().put("voiceConfig",
                        new JSONObject().put("prebuiltVoiceConfig", new JSONObject().put("voiceName", voice))));
        JSONArray tools = new JSONArray().put(new JSONObject().put("functionDeclarations", LiveTools.declarations(a.blockingBehavior())));
        if (a.search) tools.put(new JSONObject().put("googleSearch", new JSONObject()));
        JSONObject sr = new JSONObject();
        if (handle != null) sr.put("handle", handle);
        JSONObject s = new JSONObject()
                .put("model", "models/" + a.model)
                .put("generationConfig", gc)
                .put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", system))))
                .put("tools", tools)
                .put("inputAudioTranscription", new JSONObject())
                .put("outputAudioTranscription", new JSONObject());
        if (!a.minimal) {
            s.put("realtimeInputConfig", new JSONObject().put("automaticActivityDetection",
                    new JSONObject().put("prefixPaddingMs", 160).put("silenceDurationMs", 500)
                            .put("endOfSpeechSensitivity", "END_SENSITIVITY_HIGH")))
                    .put("sessionResumption", sr)
                    .put("contextWindowCompression", new JSONObject().put("slidingWindow", new JSONObject()));
        }
        return new JSONObject().put("setup", s).toString();
    }

    // ------------------------------------------------------------------ server messages (reader thread)
    private void handle(String text) {
        JSONObject m;
        try { m = new JSONObject(text); } catch (Exception e) { return; }
        JSONObject sc = m.optJSONObject("serverContent");
        if (sc != null) {
            if (sc.optBoolean("interrupted", false)) {
                clearPlayback();
                synchronized (modelBuf) { modelBuf.setLength(0); }
            }
            JSONObject in = sc.optJSONObject("inputTranscription");
            if (in != null && in.optString("text", "").length() > 0) {
                final String t;
                synchronized (userBuf) {
                    if (!turnOpen) { userBuf.setLength(0); turnOpen = true; }
                    userBuf.append(in.optString("text"));
                    t = userBuf.toString().trim();
                }
                post(() -> ui.onUserText(t));
            }
            JSONObject mt = sc.optJSONObject("modelTurn");
            JSONArray parts = mt == null ? null : mt.optJSONArray("parts");
            if (parts != null) for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.optJSONObject(i);
                if (p == null) continue;
                JSONObject inl = p.optJSONObject("inlineData");
                if (inl != null && inl.optString("mimeType", "audio/pcm").startsWith("audio")) {
                    byte[] pcm = B64.decode(inl.optString("data", ""));
                    if (pcm.length > 0) { playQ.offer(pcm); turnOpen = false; }
                }
            }
            JSONObject out = sc.optJSONObject("outputTranscription");
            if (out != null && out.optString("text", "").length() > 0) {
                final String t;
                synchronized (modelBuf) { modelBuf.append(out.optString("text")); t = modelBuf.toString().trim(); }
                turnOpen = false;
                post(() -> ui.onModelText(t));
            }
            if (sc.optBoolean("turnComplete", false)) {
                synchronized (modelBuf) { modelBuf.setLength(0); }
                turnOpen = false;
                post(ui::onTurnDone);
            }
        }
        JSONObject tc = m.optJSONObject("toolCall");
        if (tc != null) {
            JSONArray calls = tc.optJSONArray("functionCalls");
            if (calls != null) for (int i = 0; i < calls.length(); i++) {
                JSONObject c = calls.optJSONObject(i);
                if (c == null) continue;
                final String id = c.optString("id", ""), name = c.optString("name", "");
                final JSONObject args = c.optJSONObject("args") == null ? new JSONObject() : c.optJSONObject("args");
                post(() -> ui.onToolCall(id, name, args));
            }
        }
        JSONObject ru = m.optJSONObject("sessionResumptionUpdate");
        if (ru != null && ru.optBoolean("resumable", false) && ru.optString("newHandle", "").length() > 0) resumeHandle = ru.optString("newHandle");
        if (m.has("goAway") && resumeHandle != null) {
            // the server will drop this connection soon: move to a fresh one now, keeping the conversation
            final WsClient old = ws;
            gen++;
            ready = false;
            new Thread(() -> { connectAll(true); if (old != null) old.close(); }, "mitra-live-goaway").start();
        }
    }

    // ------------------------------------------------------------------ audio
    private synchronized void startAudio() {
        if (micThread != null || stopped) return;
        routeAudio();
        playThread = new Thread(this::playLoop, "mitra-live-play");
        playThread.start();
        micThread = new Thread(this::micLoop, "mitra-live-mic");
        micThread.start();
    }

    @SuppressWarnings("deprecation")
    private void routeAudio() {
        try {
            am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            oldMode = am.getMode();
            oldSpeaker = am.isSpeakerphoneOn();
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            boolean headset = false;
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || (Build.VERSION.SDK_INT >= 26 && t == AudioDeviceInfo.TYPE_USB_HEADSET)) headset = true;
            }
            if (!headset) {
                if (Build.VERSION.SDK_INT >= 31) {
                    for (AudioDeviceInfo d : am.getAvailableCommunicationDevices())
                        if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { am.setCommunicationDevice(d); break; }
                } else {
                    am.setSpeakerphoneOn(true);
                }
            }
        } catch (Exception ignored) { }
    }

    @SuppressWarnings("deprecation")
    private void restoreAudio() {
        try {
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice();
            else am.setSpeakerphoneOn(oldSpeaker);
            am.setMode(oldMode);
        } catch (Exception ignored) { }
    }

    @SuppressLint("MissingPermission")
    private void micLoop() {
        int min = AudioRecord.getMinBufferSize(IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, IN_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, IN_RATE * 2 / 5));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException();
        } catch (Exception e) {
            post(() -> ui.onFatal("माइक चालू नहीं हो पाया — शायद कोई और ऐप माइक इस्तेमाल कर रहा है।", false));
            return;
        }
        int sid = rec.getAudioSessionId();
        AcousticEchoCanceler ec = null;
        NoiseSuppressor ns = null;
        AutomaticGainControl agc = null;
        try { if (AcousticEchoCanceler.isAvailable()) { ec = AcousticEchoCanceler.create(sid); if (ec != null) { ec.setEnabled(true); aec = true; } } } catch (Exception ignored) { }
        try { if (NoiseSuppressor.isAvailable()) { ns = NoiseSuppressor.create(sid); if (ns != null) ns.setEnabled(true); } } catch (Exception ignored) { }
        try { if (AutomaticGainControl.isAvailable()) { agc = AutomaticGainControl.create(sid); if (agc != null) agc.setEnabled(true); } } catch (Exception ignored) { }
        byte[] buf = new byte[1280]; // 40 ms
        int tick = 0;
        try {
            rec.startRecording();
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                int n = 0;
                while (n < buf.length && !stopped) {
                    int r = rec.read(buf, n, buf.length - n);
                    if (r <= 0) break;
                    n += r;
                }
                if (n <= 0) { Thread.sleep(20); continue; }
                if (muted) {
                    if (!muteSent && ready) {
                        muteSent = true;
                        send("{\"realtimeInput\":{\"audioStreamEnd\":true}}");
                    }
                    continue;
                }
                if (!ready) continue;
                boolean playing = audible;
                if (playing) {
                    // echo guard: while Mitra talks, pass only clearly louder speech (barge-in), not its own echo
                    double gate = aec ? 700 : 2200;
                    if (LiveModels.rms(buf, n) < gate) java.util.Arrays.fill(buf, 0, n, (byte) 0);
                } else if ((tick++ & 1) == 0) {
                    final float lv = LiveModels.level(buf, n);
                    post(() -> ui.onLevel(lv));
                }
                send("{\"realtimeInput\":{\"audio\":{\"mimeType\":\"audio/pcm;rate=16000\",\"data\":\"" + B64.encode(buf, 0, n) + "\"}}}");
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            if (!stopped) post(() -> ui.onFatal("माइक बंद हो गया।", false));
        } finally {
            try { rec.stop(); } catch (Exception ignored) { }
            try { rec.release(); } catch (Exception ignored) { }
            try { if (ec != null) ec.release(); if (ns != null) ns.release(); if (agc != null) agc.release(); } catch (Exception ignored) { }
        }
    }

    private void playLoop() {
        int min = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            AudioFormat fmt = new AudioFormat.Builder().setSampleRate(OUT_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
            track = new AudioTrack(attrs, fmt, Math.max(min, OUT_RATE * 2 / 4), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            track.play();
        } catch (Exception e) {
            post(() -> ui.onFatal("आवाज़ नहीं चल पाई।", false));
            return;
        }
        long lastAudio = 0;
        try {
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                byte[] chunk = playQ.poll(40, TimeUnit.MILLISECONDS);
                if (chunk != null) {
                    int g = playGen;
                    final float lv = LiveModels.level(chunk, chunk.length);
                    post(() -> ui.onLevel(lv));
                    if (!audible) { audible = true; if (!muted) post(() -> ui.onStatus(S_SPEAKING)); }
                    for (int off = 0; off < chunk.length && g == playGen && !stopped; ) {
                        int len = Math.min(1920, chunk.length - off); // 40 ms slices so barge-in stops fast
                        int w = track.write(chunk, off, len);
                        if (w <= 0) break;
                        off += w;
                        framesWritten += w / 2;
                    }
                    lastAudio = System.currentTimeMillis();
                } else if (audible) {
                    long played = track.getPlaybackHeadPosition() & 0xFFFFFFFFL;
                    boolean drained = played >= framesWritten - OUT_RATE / 20 || System.currentTimeMillis() - lastAudio > 1500;
                    if (drained && playQ.isEmpty() && System.currentTimeMillis() - lastAudio > 250) {
                        audible = false;
                        post(() -> { ui.onLevel(0f); ui.onStatus(muted ? S_MUTED : S_LISTENING); });
                    }
                }
            }
        } catch (InterruptedException ignored) {
        } finally {
            try { track.stop(); } catch (Exception ignored) { }
            try { track.release(); } catch (Exception ignored) { }
        }
    }

    private void clearPlayback() {
        playGen++;
        playQ.clear();
        AudioTrack t = track;
        if (t != null) {
            try { t.pause(); t.flush(); t.play(); } catch (Exception ignored) { }
        }
        framesWritten = 0;
    }
}

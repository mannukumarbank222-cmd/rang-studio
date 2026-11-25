package com.playrang.rangstudio;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps Android's in-app SpeechRecognizer quiet: the Google recognizer plays a start/stop "tun-tun" beep on every
 * startListening() and a stop beep at the end. We mute the notification/system streams (and music, if nothing is
 * playing) just before starting and unmute ~0.45 s after the session ends (results / error / cancel), so both beeps
 * are swallowed. Only streams we muted ourselves are unmuted, so a
 * stream the user had muted stays muted. A safety timer always unmutes.
 */
final class SilentMic {
    // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31+)
    static final int ERR_LANG_NOT_SUPPORTED = 12, ERR_LANG_UNAVAILABLE = 13;
    /** Set once the offline recognizer refused the language; then we stop asking for offline. */
    static volatile boolean offlineRefused;

    private final AudioManager am;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Integer> muted = new ArrayList<>();
    private final Runnable unmuteRun = this::unmuteNow;

    SilentMic(Context c) {
        am = (AudioManager) c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    /** Call right before SpeechRecognizer.startListening(). @param ttsPlaying true if our TTS (music stream) is talking */
    synchronized void mute(boolean ttsPlaying) {
        h.removeCallbacks(unmuteRun);
        if (am == null) return;
        boolean ringerNormal = true;
        try { ringerNormal = am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL; } catch (Exception ignored) { }
        if (ringerNormal) muteOne(AudioManager.STREAM_NOTIFICATION);
        if (ringerNormal) muteOne(AudioManager.STREAM_SYSTEM);
        boolean musicBusy = ttsPlaying;
        try { musicBusy = musicBusy || am.isMusicActive(); } catch (Exception ignored) { }
        if (!musicBusy) muteOne(AudioManager.STREAM_MUSIC);
        h.postDelayed(unmuteRun, 20000); // safety: never leave the phone muted
    }

    private void muteOne(int stream) {
        if (muted.contains(stream)) return;
        try {
            if (am.isStreamMute(stream)) return; // user muted it: leave it alone
            am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0);
            muted.add(stream);
        } catch (Exception ignored) { } // e.g. Do Not Disturb policy on some phones
    }

    /** Unmute a little later so the recognizer's start/stop beep is swallowed too. */
    void unmuteSoon() {
        h.removeCallbacks(unmuteRun);
        h.postDelayed(unmuteRun, 450);
    }

    synchronized void unmuteNow() {
        h.removeCallbacks(unmuteRun);
        if (am == null) { muted.clear(); return; }
        for (int s : muted) {
            try { am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0); } catch (Exception ignored) { }
        }
        muted.clear();
    }

    /** In-app recognizer intent (never shown as a popup): prefers on-device recognition and waits longer in silence. */
    static Intent intent(String lang, int maxResults) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults);
        if (!offlineRefused) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        return i;
    }

    /** @return true when this error means "offline language pack missing": caller should retry once (now online). */
    static boolean retryOnline(int error) {
        if (offlineRefused) return false;
        if (error == ERR_LANG_NOT_SUPPORTED || error == ERR_LANG_UNAVAILABLE) { offlineRefused = true; return true; }
        return false;
    }
}

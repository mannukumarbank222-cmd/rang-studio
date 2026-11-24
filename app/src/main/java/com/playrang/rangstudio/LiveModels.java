package com.playrang.rangstudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Which Gemini Live models to try, in order, and how to read the server's close reasons. Pure Java (unit-tested). */
final class LiveModels {
    private LiveModels() {}

    /** Free-tier Live models with native audio + function calling + Google Search (Nov 2026 docs). */
    static final String[] MODELS = {
            "gemini-3.8-live",
            "gemini-3.1-flash-live-preview",
            "gemini-2.5-flash-native-audio-preview-12-2025",
    };

    static final class Attempt {
        final String model; final boolean search;
        /** minimal = no VAD tuning / resumption / compression, in case a newer server rejects one of them */
        final boolean minimal;
        Attempt(String model, boolean search) { this(model, search, false); }
        Attempt(String model, boolean search, boolean minimal) { this.model = model; this.search = search; this.minimal = minimal; }
        /** gemini-3.8 runs tools asynchronously by default; Mitra asks for blocking calls there. */
        boolean blockingBehavior() { return model.startsWith("gemini-3.8"); }
        @Override public String toString() { return model + (search ? "+search" : "") + (minimal ? "-min" : ""); }
    }

    /** Each model with Google Search first, then without; starts at `preferred` (a previously working attempt). */
    static List<Attempt> attempts(int preferred) {
        List<Attempt> all = new ArrayList<>();
        for (String m : MODELS) { all.add(new Attempt(m, true)); all.add(new Attempt(m, false)); }
        all.add(new Attempt(MODELS[0], false, true));
        all.add(new Attempt(MODELS[1], false, true));
        if (preferred > 0 && preferred < all.size()) {
            Attempt p = all.remove(preferred);
            all.add(0, p);
        }
        return all;
    }

    static int indexOf(Attempt a) {
        List<Attempt> all = attempts(0);
        for (int i = 0; i < all.size(); i++)
            if (all.get(i).model.equals(a.model) && all.get(i).search == a.search && all.get(i).minimal == a.minimal) return i;
        return 0;
    }

    /** The server closed the socket because the API key is wrong, blocked or out of quota: trying other models will not help. */
    static boolean isKeyProblem(int code, String reason) {
        String r = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        return r.contains("api key") || r.contains("api_key") || r.contains("permission_denied") || r.contains("permission denied")
                || r.contains("unregistered callers") || r.contains("billing") || r.contains("location is not supported")
                || r.contains("user location");
    }

    static boolean isQuota(int code, String reason) {
        String r = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        return r.contains("quota") || r.contains("resource_exhausted") || r.contains("rate limit") || r.contains("too many");
    }

    /** "HH:MM", "7", "7.30", "19:05" → {hour, minute}; null when no time is found. */
    static int[] parseClock(String s) {
        if (s == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})(?:\\s*[:.]\\s*(\\d{1,2}))?").matcher(s);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (h > 23 || min > 59) return null;
        return new int[]{h, min};
    }

    /** Peak-ish loudness of 16-bit little-endian PCM, 0..1 (for the orb animation). */
    static float level(byte[] pcm, int len) {
        if (len < 2) return 0f;
        double sum = 0;
        int n = 0;
        for (int i = 0; i + 1 < len; i += 2) {
            int v = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            sum += (double) v * v;
            n++;
        }
        double rms = Math.sqrt(sum / Math.max(1, n));
        float l = (float) (rms / 6000.0);
        return l > 1f ? 1f : l;
    }

    /** Root-mean-square of 16-bit PCM samples (raw units). */
    static double rms(byte[] pcm, int len) {
        double sum = 0;
        int n = 0;
        for (int i = 0; i + 1 < len; i += 2) {
            int v = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            sum += (double) v * v;
            n++;
        }
        return Math.sqrt(sum / Math.max(1, n));
    }
}

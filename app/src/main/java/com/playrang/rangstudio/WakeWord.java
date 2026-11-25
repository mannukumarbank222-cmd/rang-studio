package com.playrang.rangstudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure-Java wake word logic (no Android classes, so it is unit-tested on the JVM).
 *
 * The offline detector runs a grammar-restricted Vosk recognizer that only knows the wake word, a few
 * look-alike "decoy" words and [unk]. When it hears the wake word, the same speech segment is decoded once more
 * with the full Hindi vocabulary; word timings from both passes decide whether "मित्र" really started a phrase
 * (not "आज हमें मित्रों से…" or "पितृपक्ष") and give the command spoken in the same breath.
 */
final class WakeWord {
    private WakeWord() {}

    /** Grammar for the offline (Vosk) recognizer. Decoys keep similar common words from turning into the wake word. */
    static final String VOSK_GRAMMAR = "[\"मित्र\", \"मित्रा\", \"हे मित्र\", \"हेलो मित्र\", \"मित्रों\", \"मित्रता\", \"चित्र\", \"क्षेत्र\", \"पितृ\", \"[unk]\"]";

    /** Tokens that count as the wake word in recognizer output (Vosk Devanagari + Android recognizer Latin/Hinglish). */
    static final Set<String> WAKE = new HashSet<>(Arrays.asList(
            "मित्र", "मित्रा", "मित्तर", "मितर", "मित्रजी", "मित्र।", "mitra", "mitr", "mitraa", "mithra", "mitar", "meetra", "mittar", "mitter"));

    /** Words that may come before the wake word ("हे मित्र", "Hey Mitra", "अरे मित्र"). */
    static final Set<String> PREFIX = new HashSet<>(Arrays.asList(
            "हे", "हेलो", "हैलो", "हाय", "अरे", "ओ", "ओए", "ओके", "hey", "hi", "hello", "helo", "are", "arey", "arre", "o", "oye", "ok", "okay"));

    /** Real words the full decoder may hear when the grammar pass wrongly matched the wake word. */
    static final Set<String> CONFUSABLE = new HashSet<>(Arrays.asList(
            "मीटर", "मिट्टी", "मंत्र", "मंत्री", "मित्रों", "मित्रो", "मित्रता", "चित्र", "क्षेत्र", "पितृ", "पितृपक्ष", "मित्रवत"));

    /** Polite fillers that may follow the wake word before the actual command. */
    private static final Set<String> LEAD_FILLER = new HashSet<>(Arrays.asList(
            "जी", "ji", "जरा", "ज़रा", "zara", "please", "प्लीज", "प्लीज़", "भाई", "bhai", "yaar", "यार", "सुनो", "suno", "सुनिए", "suniye", "हाँ", "हां"));

    /** Detection thresholds for the three sensitivity levels (index 0 = less, 1 = normal, 2 = more). */
    static final double[] THRESHOLD = {0.92, 0.75, 0.62};

    /** Above this grammar confidence the full decoder does not have to agree (it sometimes drops a short word). */
    static final double SURE = 0.93;

    static double threshold(int sensitivity) {
        return THRESHOLD[Math.max(0, Math.min(2, sensitivity))];
    }

    /** One recognized word with its confidence and timing in seconds from the start of the speech segment. */
    static final class W {
        final String w; final double conf, start, end;
        W(String w, double conf, double start, double end) { this.w = w; this.conf = conf; this.start = start; this.end = end; }
        @Override public String toString() { return w + "(" + conf + "," + start + "-" + end + ")"; }
    }

    static boolean isWake(String token) {
        if (token == null) return false;
        String t = token.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}।,!?]", "").trim();
        return WAKE.contains(t);
    }

    /** True when the grammar pass contains a wake word at or above the threshold (cheap pre-check). */
    static boolean hasCandidate(List<W> grammar, double thr) {
        for (W c : grammar) if (isWake(c.w) && c.conf >= thr) return true;
        return false;
    }

    /**
     * Decides from both passes whether the wake word was said at the start of a phrase.
     * @return null when it was not; otherwise the command said in the same breath ("" when only the wake word).
     */
    static String evaluate(List<W> grammar, List<W> full, double thr) {
        for (W c : grammar) {
            if (!isWake(c.w) || c.conf < thr) continue;
            boolean ok = true;
            for (W f : full) {
                boolean overlaps = f.start < c.end - 0.02 && f.end > c.start + 0.02;
                if (overlaps) {
                    // one long real word swallowed the match ("पितृपक्ष") or the full decoder heard a look-alike word
                    if (c.start - f.start > 0.25 || f.end - c.end > 0.25) { ok = false; break; }
                    if (CONFUSABLE.contains(f.w) && c.conf < 0.95) { ok = false; break; }
                    // v4.0 false-trigger guard: the full decoder heard a different real word in the same place
                    double ov = Math.min(f.end, c.end) - Math.max(f.start, c.start);
                    double len = Math.max(0.05, c.end - c.start);
                    if (!isWake(f.w) && !PREFIX.contains(f.w.toLowerCase(Locale.ROOT)) && ov / len > 0.6 && c.conf < SURE) { ok = false; break; }
                    continue;
                }
                // the wake word must start a phrase: no other speech right before it (except "हे"/"hey")
                boolean before = f.end > c.start - 0.25 && f.end <= c.start + 0.1 && f.start < c.start - 0.05;
                if (before && !PREFIX.contains(f.w.toLowerCase(Locale.ROOT))) { ok = false; break; }
            }
            if (!ok) continue;
            StringBuilder cmd = new StringBuilder();
            for (W f : full) {
                if (f.start >= c.end - 0.12) {
                    if (cmd.length() > 0) cmd.append(' ');
                    cmd.append(f.w);
                }
            }
            return cleanCommand(cmd.toString());
        }
        return null;
    }

    /**
     * For text from the Android recognizer ("मित्र सोनू को कॉल करो", "Hey Mitra, what's the time").
     * @return null if the text does not start with the wake word, otherwise the rest ("" if nothing follows)
     */
    static String split(String text) {
        if (text == null) return null;
        String t = text.replaceAll("[,.!?।:;]", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return null;
        String[] tok = t.split(" ");
        int i = 0;
        while (i < tok.length && i < 2 && PREFIX.contains(tok[i].toLowerCase(Locale.ROOT))) i++;
        if (i >= tok.length || !isWake(tok[i])) return null;
        StringBuilder rest = new StringBuilder();
        for (int k = i + 1; k < tok.length; k++) {
            if (rest.length() > 0) rest.append(' ');
            rest.append(tok[k]);
        }
        return cleanCommand(rest.toString());
    }

    /** True when the wake word appears anywhere in a partial result (used to stop early in the fallback listener). */
    static boolean mentions(String text) {
        if (text == null) return false;
        for (String s : text.replaceAll("[,.!?।:;]", " ").split("\\s+")) if (isWake(s)) return true;
        return false;
    }

    static String cleanCommand(String s) {
        if (s == null) return "";
        List<String> tok = new ArrayList<>(Arrays.asList(s.trim().split("\\s+")));
        while (!tok.isEmpty() && (tok.get(0).isEmpty() || LEAD_FILLER.contains(tok.get(0).toLowerCase(Locale.ROOT)) || isWake(tok.get(0)))) tok.remove(0);
        StringBuilder b = new StringBuilder();
        for (String x : tok) { if (b.length() > 0) b.append(' '); b.append(x); }
        return b.toString().trim();
    }
}

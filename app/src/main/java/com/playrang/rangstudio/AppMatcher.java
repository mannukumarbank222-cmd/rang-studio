package com.playrang.rangstudio;

import java.util.List;
import java.util.Locale;

/**
 * Fuzzy match of a spoken/typed app name ("फ्लिपकार्ट", "insta", "phone pay", "मीशो") against the phone's launcher
 * app labels. Devanagari and other Indian scripts are transliterated to Latin first (via {@link NameMatcher}), then
 * compared with prefix / containment / phonetic-key / edit-distance scores. Pure Java (unit-tested).
 */
final class AppMatcher {
    private AppMatcher() {}

    /** One launcher entry: its visible label and package name. */
    static final class App {
        final String label, pkg;
        App(String label, String pkg) { this.label = label == null ? "" : label; this.pkg = pkg == null ? "" : pkg; }
        @Override public String toString() { return label + " (" + pkg + ")"; }
    }

    static final double MIN_SCORE = 0.72;

    /** Latin, lowercase, letters/digits only, single spaces ("Google Pay" → "google pay", "फ्लिपकार्ट" → "flipkaart"). */
    static String key(String s) {
        return NameMatcher.clean(s == null ? "" : s);
    }

    /** Phonetic key with spaces removed ("phone pe" → "fonpe", "PhonePe" → "fonpe"). */
    static String phon(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : key(s).split(" ")) if (!w.isEmpty()) b.append(NameMatcher.foldWord(w));
        return NameMatcher.foldWord(b.toString());
    }

    static String skeleton(String p) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if ("aeiouy".indexOf(c) >= 0) continue;
            if (b.length() > 0 && b.charAt(b.length() - 1) == c) continue;
            b.append(c);
        }
        return b.toString();
    }

    /** Similarity 0..1 of a query to one app (label and package words). */
    static double score(String query, App app) {
        String q = key(query), l = key(app.label);
        if (q.isEmpty() || l.isEmpty()) return 0;
        String qc = q.replace(" ", ""), lc = l.replace(" ", "");
        if (qc.equals(lc)) return 1.0;
        String qp = phon(query), lp = phon(app.label);
        if (!qp.isEmpty() && qp.equals(lp)) return 0.97;
        double best = 0;
        if (lc.startsWith(qc) && qc.length() >= 3) best = Math.max(best, 0.86 + 0.1 * qc.length() / lc.length());
        if (lp.startsWith(qp) && qp.length() >= 3) best = Math.max(best, 0.84 + 0.1 * qp.length() / Math.max(1, lp.length()));
        // a whole word of the label ("Pay" in "Google Pay", "Lite" not enough alone)
        for (String w : l.split(" ")) {
            if (w.length() >= 3 && w.equals(qc)) best = Math.max(best, 0.9);
            String wp = NameMatcher.foldWord(w);
            if (wp.length() >= 3 && wp.equals(qp)) best = Math.max(best, 0.88);
        }
        if (qc.length() >= 4 && lc.contains(qc)) best = Math.max(best, 0.8);
        if (lc.length() >= 4 && qc.contains(lc)) best = Math.max(best, 0.78);
        // edit distance on phonetic keys (handles "watsap", "instagran", "flipcart")
        if (qp.length() >= 3 && lp.length() >= 3) {
            int d = NameMatcher.lev(qp, lp);
            double s = 1.0 - d / (double) Math.max(qp.length(), lp.length());
            best = Math.max(best, s * 0.95);
            // compare with the label's first word too ("whatsapp business" vs "watsap")
            String first = l.split(" ")[0];
            String fp = NameMatcher.foldWord(first);
            if (fp.length() >= 3) {
                int d2 = NameMatcher.lev(qp, fp);
                best = Math.max(best, (1.0 - d2 / (double) Math.max(qp.length(), fp.length())) * 0.93);
            }
        }
        // consonant skeleton: Hindi spellings differ mostly in vowels ("ट्रूकॉलर" trukolar ~ "truecaller")
        String qs = skeleton(qp), ls = skeleton(lp);
        if (qs.length() >= 3 && qs.equals(ls)) best = Math.max(best, 0.86);
        else if (qs.length() >= 4 && ls.length() >= 4) {
            int d = NameMatcher.lev(qs, ls);
            if (d == 1) best = Math.max(best, 0.76);
        }
        // package name words ("com.phonepe.app" for a label written in another language)
        String pk = app.pkg.toLowerCase(Locale.ROOT);
        if (qc.length() >= 4) for (String part : pk.split("[._]")) {
            if (part.equals(qc)) best = Math.max(best, 0.85);
            else if (part.length() >= 4 && NameMatcher.foldWord(part).equals(qp)) best = Math.max(best, 0.83);
        }
        return Math.min(1.0, best);
    }

    /** Index of the best matching app, or -1 when nothing scores at least {@link #MIN_SCORE}. Ties prefer the shorter label. */
    static int best(String query, List<App> apps) {
        int bi = -1;
        double bs = 0;
        for (int i = 0; i < apps.size(); i++) {
            double s = score(query, apps.get(i));
            if (s > bs + 1e-9 || (Math.abs(s - bs) < 1e-9 && bi >= 0 && apps.get(i).label.length() < apps.get(bi).label.length())) { bs = s; bi = i; }
        }
        return bs >= MIN_SCORE ? bi : -1;
    }

    /** Best score in the list (for tests / deciding whether to ask). */
    static double bestScore(String query, List<App> apps) {
        double bs = 0;
        for (App a : apps) bs = Math.max(bs, score(query, a));
        return bs;
    }
}

package com.playrang.rangstudio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java fuzzy matching of a spoken/typed name against a contact name.
 * Works across scripts: Devanagari text is transliterated to Latin, then both sides are
 * folded to a rough phonetic key (aa→a, ee→i, w→v, sh→s, double letters collapsed…)
 * and compared token by token with edit distance. No Android dependencies, so it is unit tested.
 */
final class NameMatcher {
    private NameMatcher() {}

    // ------------------------------------------------------------ transliteration
    private static final Map<Character, String> CONS = new HashMap<>();
    private static final Map<Character, String> VOW = new HashMap<>();
    private static final Map<Character, String> MATRA = new HashMap<>();
    static {
        String c = "क:k ख:kh ग:g घ:gh ङ:n च:ch छ:chh ज:j झ:jh ञ:n ट:t ठ:th ड:d ढ:dh ण:n त:t थ:th द:d ध:dh न:n "
                + "प:p फ:ph ब:b भ:bh म:m य:y र:r ल:l ळ:l व:v श:sh ष:sh स:s ह:h";
        for (String p : c.split(" ")) { String[] kv = p.split(":"); CONS.put(kv[0].charAt(0), kv[1]); }
        CONS.put('\u0958', "q"); CONS.put('\u0959', "kh"); CONS.put('\u095A', "g"); CONS.put('\u095B', "z");
        CONS.put('\u095C', "r"); CONS.put('\u095D', "rh"); CONS.put('\u095E', "f"); CONS.put('\u095F', "y");
        String v = "अ:a आ:aa इ:i ई:ee उ:u ऊ:oo ऋ:ri ए:e ऐ:ai ओ:o औ:au ऑ:o ऍ:e";
        for (String p : v.split(" ")) { String[] kv = p.split(":"); VOW.put(kv[0].charAt(0), kv[1]); }
        String m = "ा:aa ि:i ी:ee ु:u ू:oo ृ:ri े:e ै:ai ो:o ौ:au ॉ:o ॅ:e";
        for (String p : m.split(" ")) { String[] kv = p.split(":"); MATRA.put(kv[0].charAt(0), kv[1]); }
    }

    static boolean isDeva(char ch) { return ch >= '\u0900' && ch <= '\u097F'; }

    static boolean hasDeva(String s) {
        for (int i = 0; i < s.length(); i++) if (isDeva(s.charAt(i))) return true;
        return false;
    }

    /**
     * Bengali, Gurmukhi, Gujarati, Odia, Tamil, Telugu, Kannada and Malayalam Unicode blocks are laid out
     * parallel to Devanagari, so shifting the code point gives a usable Devanagari spelling for name matching.
     */
    static String toDevanagari(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u0A70') { b.append('\u0902'); continue; }   // Gurmukhi tippi
            if (c == '\u0A71') continue;                            // Gurmukhi addak (gemination)
            if (c >= '\u0980' && c <= '\u0D7F') {
                int off = (c - 0x0900) % 0x80;
                b.append((char) (0x0900 + off));
            } else b.append(c);
        }
        return b.toString();
    }

    /** Query variants with case suffixes removed: Marathi "सोनूला", Bengali "সোনুকে", Gujarati "સોનુને". */
    static List<String> variants(String q) {
        List<String> out = new ArrayList<>();
        out.add(q);
        String d = toDevanagari(q).trim();
        String[] suf = {"क्कु", "उक्कु", "ला", "ना", "ले", "कडे", "के", "क", "ने", "नूं", "नू", "कु", "कि", "गे", "ku", "la", "nu"};
        for (String x : suf) {
            if (d.endsWith(x) && d.length() > x.length() + 1) { out.add(d.substring(0, d.length() - x.length())); break; }
        }
        return out;
    }

    static double bestScore(String query, String contactName) {
        double best = 0;
        List<String> v = variants(query);
        for (int i = 0; i < v.size(); i++) best = Math.max(best, score(v.get(i), contactName) * (i == 0 ? 1 : 0.98));
        return best;
    }

    /** Simple Hindi → Latin transliteration (with word-final schwa deletion). */
    static String toLatin(String s) {
        s = toDevanagari(s);
        StringBuilder out = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if (ch == '\u093C') continue; // nukta
            String cons = CONS.get(ch);
            if (cons != null) {
                out.append(cons);
                int j = i + 1;
                while (j < n && s.charAt(j) == '\u093C') j++;
                char nx = j < n ? s.charAt(j) : ' ';
                if (nx == '\u094D') { i = j; continue; }       // virama: no vowel
                if (MATRA.containsKey(nx)) continue;            // matra follows
                boolean wordEnd = j >= n || !isDeva(nx) || nx == '\u0964';
                boolean nextIsMark = nx == '\u0902' || nx == '\u0901' || nx == '\u0903';
                if (!wordEnd || nextIsMark) out.append('a');
                continue;
            }
            String vw = VOW.get(ch);
            if (vw != null) { out.append(vw); continue; }
            String mt = MATRA.get(ch);
            if (mt != null) { out.append(mt); continue; }
            if (ch == '\u0902' || ch == '\u0901') {
                boolean end = i + 1 >= n || !isDeva(s.charAt(i + 1));
                if (!end) out.append('n');   // word-final nasal (सोनूं, ਸੋਨੂੰ, मां) is only nasalisation
                continue;
            }
            if (ch == '\u0903') { out.append('h'); continue; }
            if (ch == '\u094D') continue;
            if (ch >= '\u0966' && ch <= '\u096F') { out.append((char) ('0' + (ch - '\u0966'))); continue; }
            if (isDeva(ch)) continue;
            out.append(ch);
        }
        return out.toString();
    }

    /** Lowercase Latin letters/digits only, Devanagari transliterated, words separated by single spaces. */
    static String clean(String s) {
        if (s == null) return "";
        String t = toLatin(s.replace("\u093E\u0945", "\u0949")).toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (char ch : t.toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) b.append(ch);
            else b.append(' ');
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    /** Rough phonetic key of one Latin word. */
    static String foldWord(String w) {
        String t = w;
        t = t.replace("chh", "ch").replace("ph", "f").replace("kh", "k").replace("gh", "g").replace("th", "t")
                .replace("dh", "d").replace("bh", "b").replace("jh", "j").replace("sh", "s").replace("ck", "k")
                .replace("w", "v").replace("z", "j").replace("q", "k").replace("x", "ks");
        t = t.replaceAll("c(?!h)", "k");
        t = t.replace("ee", "i").replace("ii", "i").replace("oo", "u").replace("uu", "u").replace("aa", "a");
        t = t.replace("ey", "e").replace("ay", "e").replace("ai", "e").replace("ou", "u").replace("au", "o");
        if (t.length() > 2 && t.endsWith("y")) t = t.substring(0, t.length() - 1) + "i";
        if (t.length() > 3 && t.endsWith("h") && "aeiou".indexOf(t.charAt(t.length() - 2)) >= 0) t = t.substring(0, t.length() - 1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (b.length() > 0 && b.charAt(b.length() - 1) == ch) continue;
            b.append(ch);
        }
        return b.toString();
    }

    static List<String> keys(String s) {
        List<String> out = new ArrayList<>();
        for (String w : clean(s).split(" ")) if (!w.isEmpty()) out.add(foldWord(w));
        return out;
    }

    // ------------------------------------------------------------ relations / honorifics
    private static final Map<String, String> GROUP = new HashMap<>();      // folded key → group (only unambiguous)
    private static final Map<String, String> GROUP_RAW = new HashMap<>();  // cleaned (unfolded) word → group
    private static final java.util.Set<String> HONORIFIC = new java.util.HashSet<>();
    static {
        String[][] g = {
                {"mummy", "mumy", "mumma", "mummi", "mammi", "mommy", "mom", "mamma", "maa", "ma", "mata", "mataji", "mother", "ammi", "amma",
                        "मम्मी", "मम्मा", "माँ", "मां", "मा", "माता", "माताजी", "अम्मी", "अम्मा", "मॉम"},
                {"papa", "pappa", "papaji", "dad", "daddy", "father", "pitaji", "pita", "abbu", "baba", "पापा", "पापाजी", "पिताजी", "पिता", "डैडी", "अब्बू", "बाबा"},
                {"bhaiya", "bhaiyya", "bhaia", "bhai", "brother", "bro", "भैया", "भईया", "भाई", "भय्या"},
                {"didi", "dii", "sister", "behen", "bahan", "दीदी", "दिदी", "बहन"},
                {"wife", "biwi", "bivi", "patni", "gharwali", "mrs", "बीवी", "पत्नी", "घरवाली"},
                {"husband", "pati", "hubby", "पति"},
                {"chacha", "chachaji", "चाचा", "चाचाजी"}, {"chachi", "चाची"}, {"mama", "mamaji", "मामा", "मामाजी"}, {"mami", "मामी"},
                {"dada", "dadaji", "दादा", "दादाजी"}, {"dadi", "daadi", "दादी"}, {"nana", "nanaji", "नाना", "नानाजी"}, {"nani", "नानी"},
                {"bua", "buaa", "बुआ"}, {"mausi", "mosi", "मौसी"}, {"jiju", "jijaji", "jija", "जीजू", "जीजाजी", "जीजा"}, {"bhabhi", "भाभी"},
                {"beta", "बेटा"}, {"beti", "बेटी"}, {"sir", "सर"}, {"madam", "maam", "मैडम"}
        };
        java.util.Set<String> clash = new java.util.HashSet<>();
        for (int i = 0; i < g.length; i++) {
            for (String v : g[i]) {
                for (String w : clean(v).split(" ")) {
                    if (w.isEmpty()) continue;
                    GROUP_RAW.put(w, "g" + i);
                    String k = foldWord(w);
                    String old = GROUP.get(k);
                    if (old != null && !old.equals("g" + i)) clash.add(k);
                    GROUP.put(k, "g" + i);
                }
            }
        }
        for (String k : clash) GROUP.remove(k);
        for (String h : new String[]{"ji", "jee", "जी", "sir", "सर", "sahab", "saheb", "साहब", "bhaiya", "bhai", "भैया", "भाई", "didi", "दीदी",
                "uncle", "अंकल", "aunty", "anty", "आंटी", "bhabhi", "भाभी", "madam", "मैडम"}) {
            for (String k : keys(h)) HONORIFIC.add(k);
        }
    }

    // ------------------------------------------------------------ similarity
    static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    private static String group(String raw, String folded) {
        String g = GROUP_RAW.get(raw);
        return g != null ? g : GROUP.get(folded);
    }

    static double sim(String a, String b) { return sim(a, b, GROUP.get(a), GROUP.get(b)); }

    static double sim(String a, String b, String ga, String gb) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1;
        if (ga != null && ga.equals(gb)) return 0.97;
        int d = lev(a, b);
        int max = Math.max(a.length(), b.length());
        double s = 1.0 - (double) d / max;
        if (Math.min(a.length(), b.length()) <= 3 && d > 0) s = Math.min(s, 0.6); // short words must match exactly
        if (a.length() >= 3 && b.startsWith(a)) s = Math.max(s, 0.84);          // "son" → "sonu"
        if (ga != null && gb != null && !ga.equals(gb)) s = Math.min(s, 0.6);  // mama ≠ mami
        return s;
    }

    static final double TOKEN_OK = 0.74;

    /** 0..1 score of how well the query names this contact. 1.0 = exact (after folding). */
    static double score(String query, String contactName) {
        List<String> qr = raw(query), nr = raw(contactName);
        if (qr.isEmpty() || nr.isEmpty()) return 0;
        List<String> q = new ArrayList<>(), n = new ArrayList<>(), qg = new ArrayList<>(), ng = new ArrayList<>();
        for (String w : qr) { String f = foldWord(w); q.add(f); qg.add(group(w, f)); }
        for (String w : nr) { String f = foldWord(w); n.add(f); ng.add(group(w, f)); }
        String jq = join(q), jn = join(n);
        if (jq.equals(jn)) return 1.0;
        List<Integer> core = new ArrayList<>();
        for (int i = 0; i < q.size(); i++) if (!HONORIFIC.contains(q.get(i))) core.add(i);
        if (core.isEmpty()) for (int i = 0; i < q.size(); i++) core.add(i);
        double sum = 0;
        boolean all = true;
        boolean[] used = new boolean[n.size()];
        for (int qi : core) {
            double best = 0;
            int bi = -1;
            for (int i = 0; i < n.size(); i++) {
                double s = sim(q.get(qi), n.get(i), qg.get(qi), ng.get(i));
                if (s > best) { best = s; bi = i; }
            }
            if (best < TOKEN_OK) { all = false; break; }
            sum += best;
            if (bi >= 0) used[bi] = true;
        }
        double joined = sim(jq.replace(" ", ""), jn.replace(" ", ""), null, null);
        if (!all) return joined >= 0.8 ? joined * 0.9 : 0;
        int cov = 0;
        for (boolean u : used) if (u) cov++;
        double s = (sum / core.size()) * 0.9 + 0.08 * ((double) cov / n.size());
        for (String t : q) if (HONORIFIC.contains(t) && n.contains(t)) s += 0.02;
        return Math.min(0.99, Math.max(s, joined >= 0.85 ? joined * 0.95 : 0));
    }

    private static List<String> raw(String s) {
        List<String> out = new ArrayList<>();
        for (String w : clean(s).split(" ")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(' '); b.append(s); }
        return b.toString();
    }
}

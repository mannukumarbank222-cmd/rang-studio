package com.playrang.rangstudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java understanding of phone commands in Hindi / Hinglish / English.
 * It only decides WHAT to do (a {@link Cmd}); {@link ActionHandler} does it with Android intents.
 * No Android dependencies, so it is covered by plain JUnit tests.
 */
final class CommandParser {
    private CommandParser() {}

    /** A parsed action: type plus up to two arguments. */
    static final class Cmd {
        final String type, a, b;
        Cmd(String type, String a, String b) { this.type = type; this.a = a == null ? "" : a; this.b = b == null ? "" : b; }
        @Override public String toString() { return type + "|" + a + "|" + b; }
    }

    /** Name placeholder meaning "the person we talked about last" (usko / उसे / dobara). */
    static final String LAST = "@last";

    private static Cmd cmd(String type, String a, String b) { return new Cmd(type, a, b); }

    // ---------------------------------------------------------------- vocabulary
    static final String[] YT = {"ਯੂਟਿਊਬ", "યુટ્યુબ", "યૂટ્યૂબ", "ইউটিউব", "யூடியூப்", "యూట్యూబ్", "ಯೂಟ್ಯೂಬ್", "യൂട്യൂബ്", "ୟୁଟ୍ୟୁବ", "یوٹیوب", "यूट्यूबवर", "youtube", "यूट्यूब", "यूटयूब", "यू ट्यूब", "you tube", "यूट्यूब्स", "utube", "youtub", "yutub", "yt", "यूट्यूबपे"};
    static final String[] PLAY = {"चलावऽ", "चलाव", "बजावऽ", "बजाव", "चला द", "लगा द", "सुनावऽ", "ਚਲਾਓ", "ਲਗਾਓ", "ਸੁਣਾਓ", "ચલાવો", "વગાડો", "સંભળાવો", "लाव", "लावा", "वाजव", "ऐकव", "চালাও", "বাজাও", "শোনাও", "चलाओ", "चला दो", "चलादो", "चलाइए", "चलाइये", "चलाना", "चला", "लगाओ", "लगा दो", "लगादो", "बजाओ", "बजा दो",
            "सुनाओ", "सुना दो", "दिखाओ", "दिखा दो", "प्ले", "play", "chalao", "chala do", "chalado", "chalaiye", "chala", "lagao", "laga do",
            "bajao", "baja do", "sunao", "suna do", "dikhao", "dikha do", "chalaao", "chlao"};
    static final String[] SONG = {"ਗੀਤ", "ਗਾਣਾ", "ગીત", "ગાયન", "गाणे", "गाणं", "গান", "ভজন", "ਭਜਨ", "ભજન", "गाना", "गाने", "गीत", "भजन", "song", "songs", "gana", "gaana", "gane", "gaane", "bhajan", "video", "वीडियो",
            "विडियो", "music", "म्यूजिक", "संगीत", "आरती", "aarti", "arti", "कथा", "katha", "चालीसा", "chalisa", "movie", "मूवी", "फिल्म",
            "film", "trailer", "ट्रेलर"};
    static final String[] OPEN = {"खोलऽ", "खोल द", "खोलीं", "खोलू", "ਖੋਲ੍ਹੋ", "ਖੋਲੋ", "ਖੋਲ੍ਹ ਦਿਓ", "ખોલો", "ખોલ", "ચાલુ કરો", "उघड", "उघडा", "उघड ना", "চালু করো", "খোলো", "খোল", "খুলুন", "திற", "తెరువు", "ತೆರೆ", "തുറക്കൂ", "ଖୋଲ", "کھولو", "खोलो", "खोल दो", "खोलदो", "खोलिए", "खोलिये", "खोलना", "खोल", "ओपन", "open", "kholo", "khol do", "kholdo",
            "kholiye", "khol", "kholna", "चालू करो", "chalu karo", "start karo", "launch", "खोलें", "kholen"};
    static final String[] WA = {"ਵਟਸਐਪ", "ਵ੍ਹਾਟਸਐਪ", "ਵਟਸਅਪ", "વોટ્સએપ", "વૉટ્સએપ", "વોટસએપ", "व्हॉट्सअॅप", "व्हाट्सअॅप", "হোয়াটসঅ্যাপ", "হোয়াটসআপ", "হোয়াটস্যাপ", "வாட்ஸ்அப்", "వాట్సాప్", "ವಾಟ್ಸಾಪ್", "വാട്സാപ്പ്", "ହ୍ୱାଟସଆପ", "واٹس ایپ", "whatsapp", "whats app", "व्हाट्सएप", "व्हाट्सऐप", "व्हाट्सअप", "वाट्सएप", "वॉट्सऐप", "वॉट्सएप", "व्हाट्सप",
            "व्हाट्सएप्प", "watsapp", "whatsaap", "whatsap", "watsap", "वाट्सऐप", "वाट्सअप", "व्हाट्सऐप्प", "whatapp", "watsaap", "wattsapp",
            "whatsapps", "व्हाट्सएप्स", "वॉट्सएप्प", "वाट्सप", "wp", "वहाट्सएप"};
    static final String[] CALL = {"ਕਾਲ", "ਫੋਨ", "ਫ਼ੋਨ", "કૉલ", "કોલ", "ફોન", "ফোন", "কল", "கால்", "போன்", "ఫోన్", "కాల్", "ಕಾಲ್", "ಫೋನ್", "കോൾ", "ഫോൺ", "ଫୋନ", "କଲ", "کال", "فون", "कॉल", "काल", "कोल", "कॉल्स", "call", "cal", "coll", "kol", "फोन", "phone", "fone", "fon", "phon", "dial",
            "डायल", "calling", "कॉलिंग"};
    static final String[] CALL_VERB = {"लगावऽ", "लगाव", "लगा द", "लगा दीं", "लगाईं", "करऽ", "कर द", "करीं", "कर दीं", "मिलावऽ", "मिलाव", "लगाबू", "करू", "लगाउ", "लगा दिअ", "ਕਰੋ", "ਕਰ", "ਕਰ ਦਿਓ", "ਲਗਾਓ", "ਲਾਓ", "ਮਿਲਾਓ", "lao", "la do", "kar deo", "કરો", "કર", "કરી દો", "લગાવો", "લગાડો", "જોડો", "લગાવ", "करा", "लाव", "लावा", "लाव ना", "जोड", "जोडा", "कर ना", "করো", "কর", "করে দাও", "দাও", "লাগাও", "কর তো", "করুন", "लगाओ", "लगा दो", "लगादो", "लगा", "लगाइए", "लगाइये", "लगाना", "करो", "कर दो", "करदो", "कर", "करिए",
            "करिये", "कीजिए", "कीजिये", "करें", "करना", "कर दीजिए", "मिलाओ", "मिला दो", "मिला", "मिलाइए", "मिलाना", "घुमाओ",
            "lagao", "laga do", "lagado", "laga", "lgao", "lagaiye", "lagana", "karo", "kro", "kar do", "kardo", "krdo", "kar", "kariye",
            "kijiye", "kijie", "karen", "karna", "karde", "kar de", "milao", "mila do", "milado", "mila", "milaiye", "milana", "ghumao",
            "dial", "डायल", "call", "कॉल", "please call", "make"};
    static final String[] CALL_FILLER = {"ऐप", "एप", "एप्प", "app", "aap", "application", "dialer", "डायलर", "में", "मे", "me", "mein", "main",
            "mai", "जाकर", "जाके", "जा कर", "jakar", "jaakar", "jake", "ja kar", "जा के", "खोलकर", "खोलके", "kholkar", "kholke", "se", "से",
            "number", "नंबर", "नम्बर", "nambar", "no", "mobile", "मोबाइल", "पे", "पर", "pe", "par", "per", "pr", "को", "ko", "abhi", "अभी",
            "jaldi", "जल्दी", "turant", "तुरंत", "ek", "एक", "my", "mera", "मेरा", "meri", "मेरी", "mere", "मेरे", "to", "the", "a",
            "किसी", "kisi", "सीधे", "sidhe", "direct", "video", "वीडियो", "voice", "normal", "साथ", "sath", "बात", "baat", "कराओ", "karao",
            "करवाओ", "karwao", "karvao", "करनी", "karni", "है", "hai", "make"};
    static final String[] MSG_W = {"ਮੈਸੇਜ", "ਸੁਨੇਹਾ", "મેસેજ", "સંદેશ", "মেসেজ", "বার্তা", "मैसेज", "मेसेज", "मैसेंज", "message", "messages", "msg", "massage", "messege", "masej", "संदेश", "sandesh",
            "मसेज", "text", "टेक्स्ट"};
    static final String[] SMS_W = {"sms", "एसएमएस", "एस एम एस", "s m s", "text message", "टेक्स्ट मैसेज"};
    static final String[] SEND_V = {"भेजऽ", "भेज द", "लिखऽ", "लिख द", "बोलऽ", "बोल द", "कहऽ", "कह द", "ਭੇਜੋ", "ਲਿਖੋ", "ਕਹੋ", "ਦੱਸੋ", "મોકલો", "લખો", "કહો", "पाठव", "पाठवा", "लिही", "लिहा", "सांग", "सांगा", "পাঠাও", "লেখো", "বলো", "বলে দাও", "भेजो", "भेज दो", "भेज", "भेजिए", "भेजिये", "भेजना", "bhejo", "bhej do", "bhej", "bhejiye", "bhejna", "send",
            "लिखो", "लिख दो", "लिख", "लिखिए", "likho", "likh do", "likh", "likhiye", "बोलो", "बोल दो", "बोल", "बोलिए", "bolo", "bol do",
            "bol", "boliye", "बताओ", "बता दो", "batao", "bata do", "करो", "कर दो", "कर", "karo", "kar do", "kar", "kro", "कहो", "कह दो",
            "kaho", "keh do", "kah do"};
    static final String[] TORCH = {"टॉर्च", "टार्च", "torch", "flashlight", "flash light", "फ्लैशलाइट", "फ्लैश लाइट", "टोर्च", "tourch", "torchlight"};
    static final String[] OFF = {"बंद", "band", "off", "ऑफ", "बुझाओ", "bujhao", "बुझा", "bnd"};
    static final String[] CAMERA = {"कैमरा", "कैमरे", "camera", "selfie", "सेल्फी", "kamera", "camra"};
    static final String[] ALARM = {"अलार्म", "अलार्मा", "alarm", "alaram", "अलारम", "alarm clock", "अलाराम"};
    static final String[] TIMER = {"टाइमर", "timer"};
    static final String[] MAP = {"मैप", "मैप्स", "map", "maps", "नक्शा", "नक्शे", "naksha", "रास्ता", "rasta", "raasta", "direction",
            "directions", "डायरेक्शन", "लोकेशन", "location", "navigation", "नेविगेशन"};
    static final String[] GO = {"कैसे जाएं", "कैसे जाऊं", "कैसे पहुंचे", "कैसे पहुंचें", "kaise jaye", "kaise jaaye", "kaise jau", "kaise pahuche"};
    static final String[] SEARCH = {"search", "सर्च", "खोजो", "खोज", "ढूंढो", "ढूंढ", "dhundho", "dhoondho", "khojo", "google karo",
            "गूगल करो", "गूगल पर", "google par", "google pe", "गूगल पे", "सर्च करो"};
    static final String[] SHARE = {"शेयर", "share"};
    static final String[] Q = {"क्या", "kya", "कैसे", "kaise", "क्यों", "kyon", "kyu", "kyun", "what", "how", "why", "कौन", "kaun", "which",
            "कौनसा", "कौन सा", "मतलब", "matlab", "होता", "hota", "कितना", "kitna", "किसने", "kisne", "बारे", "bare", "baare", "about"};
    static final String[] STRONG_Q = {"कैसे", "kaise", "क्यों", "kyon", "kyu", "kyun", "how", "why", "मतलब", "matlab", "kab", "कब", "when",
            "किसने", "kisne", "क्या होता", "kya hota", "kya hai", "क्या है"};
    static final String[] TIME_W = {"समय", "टाइम", "time", "बजे", "baje", "बजा", "baja"};
    static final String[] TIME_Q = {"क्या", "kya", "कितने", "kitne", "बताओ", "batao", "what", "हुआ", "hua", "हुए", "hue", "अभी", "abhi", "now", "बताइए"};
    static final String[] DATE_W = {"तारीख", "तारिख", "तिथि", "दिनांक", "date", "tarikh", "tareekh", "तारीक"};
    static final String[] DAY_W = {"दिन", "din", "वार", "day", "weekday"};
    static final String[] FILLER = {"ਜੀ", "ਜ਼ਰਾ", "ਮੇਰੇ", "ਪਲੀਜ਼", "જરા", "મારા", "પ્લીઝ", "माझ्या", "প্লিজ", "আমার", "একটু", "तनी", "तनिक", "हमार", "हमरा", "ਤੇ", "ਉੱਤੇ", "પર", "વર", "वर", "पर", "पे", "में", "par", "pe", "per", "pr", "on", "me", "mein", "करो", "कर", "करिए", "करिये", "karo", "kar",
            "kariye", "kro", "दो", "please", "प्लीज", "pls", "plz", "मित्र", "mitra", "मेरे", "लिए", "liye", "mere", "जरा", "zara", "jara",
            "थोड़ा", "थोडा", "thoda", "मुझे", "mujhe", "को", "ko", "एक", "ek", "कोई", "koi", "a", "the", "for", "से", "se", "वाला", "wala",
            "वाली", "wali", "भाई", "bhai", "यार", "yaar", "हे", "hey", "ऐप", "app", "एप", "एप्प", "एप्लीकेशन", "application", "वेबसाइट",
            "website", "साइट", "site", "page", "पेज", "जी", "ji", "है", "hai", "दीजिए", "दीजिये", "dijiye", "do", "to", "अभी", "abhi"};
    /** FILLER without relation words, so "bhai ko call karo" keeps "bhai" as the name. */
    static final String[] NAME_FILLER = without(FILLER, "भाई", "bhai", "yaar", "यार");

    private static String[] without(String[] l, String... rm) {
        List<String> out = new ArrayList<>(Arrays.asList(l));
        out.removeAll(Arrays.asList(rm));
        return out.toArray(new String[0]);
    }

    static final String[] PRONOUN = {"usko", "usse", "use", "unko", "unhe", "unhen", "unse", "inko", "isko", "ise", "inhe", "isse", "usiko",
            "usi", "wahi", "vahi", "उसको", "उसे", "उनको", "उन्हें", "उन्हे", "इसको", "इसे", "इन्हें", "उसी", "उसीको", "वही", "उससे", "उनसे",
            "him", "her", "them", "same", "उनका", "उसका", "uska", "unka"};
    static final String[] REDIAL = {"dobara", "dubara", "दोबारा", "दुबारा", "phir se", "fir se", "फिर से", "फिरसे", "again", "redial",
            "वापस", "wapas", "vapas", "back"};
    static final String[] NOT_NAME = {"main", "मैं", "मै", "hum", "हम", "tum", "तुम", "aap", "आप", "wo", "वो", "kuch", "कुछ", "sab", "सब",
            "sabko", "सबको", "kaun", "कौन", "kisko", "किसको", "kise", "किसे", "मुझको", "mujhko", "khud", "खुद"};
    static final String[] KO = {"के", "ਨੂੰ", "nu", "ને", "ला", "কে", "को", "ko", "koh", "ku", "kou"};
    static final String[] POSS = {"का", "के", "की", "ka", "ke", "ki"};
    static final String[] KI = {"ਕਿ", "કે", "যে", "कि", "ki", "that", "saying", "ke liye bolo"};
    static final String[] WA_CALL = {"call", "कॉल", "काल", "video call", "वीडियो कॉल", "voice call", "calling", "कोल"};

    // ---------------------------------------------------------------- normalisation helpers
    /** Removes nukta (फ़ → फ, ज़ → ज), chandrabindu → anusvara, zero-width marks; lowercases Latin. */
    static String fold(String s) {
        s = s.replace("\u093E\u0945", "\u0949"); // का + ॅ typed as काॅ → कॉ
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\u093C': case '\u200C': case '\u200D': continue;
                case '\u0958': b.append('\u0915'); break;
                case '\u0959': b.append('\u0916'); break;
                case '\u095A': b.append('\u0917'); break;
                case '\u095B': b.append('\u091C'); break;
                case '\u095C': b.append('\u0921'); break;
                case '\u095D': b.append('\u0922'); break;
                case '\u095E': b.append('\u092B'); break;
                case '\u095F': b.append('\u092F'); break;
                case '\u0929': b.append('\u0928'); break;
                case '\u0931': b.append('\u0930'); break;
                case '\u0901': b.append('\u0902'); break;
                default:
                    if (ch >= '\u0966' && ch <= '\u0D7F' && (ch & 0x7F) >= 0x66 && (ch & 0x7F) <= 0x6F) b.append((char) ('0' + ((ch & 0x7F) - 0x66)));
                    else if (ch >= '\u06F0' && ch <= '\u06F9') b.append((char) ('0' + (ch - '\u06F0')));
                    else if (ch >= '\u0660' && ch <= '\u0669') b.append((char) ('0' + (ch - '\u0660')));
                    else b.append(ch);
            }
        }
        return b.toString().toLowerCase(Locale.ROOT);
    }

    static String norm(String s) {
        if (s == null) return "";
        return fold(s)
                .replaceAll("[?!,।|\"“”'‘’;:()\\[\\]{}…]+", " ")
                .replace("डॉट", ".").replace(" dot ", ".")
                .replaceAll("(?<=\\D)\\.(?=\\s|$)", " ")
                .replaceAll("\\s+", " ").trim();
    }

    /** whole-word / whole-phrase containment on space-separated text */
    static boolean has(String t, String... words) {
        String p = " " + t + " ";
        for (String w : words) if (p.contains(" " + fold(w) + " ")) return true;
        return false;
    }

    /** removes whole words / phrases (longest first) */
    static String strip(String t, String[]... lists) {
        List<String> all = new ArrayList<>();
        for (String[] l : lists) for (String w : l) all.add(fold(w));
        Collections.sort(all, (x, y) -> y.length() - x.length());
        String p = " " + t + " ";
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String w : all) {
                String k = " " + w + " ";
                if (p.contains(k)) { p = p.replace(k, " "); changed = true; }
            }
        }
        return p.replaceAll("\\s+", " ").trim();
    }

    static String digits(String t) {
        StringBuilder d = new StringBuilder();
        for (char c : t.toCharArray()) if (c >= '0' && c <= '9') d.append(c);
        return d.toString();
    }

    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s-]{1,}\\d");

    static String phoneDigits(String t) {
        Matcher m = PHONE.matcher(t);
        String best = "";
        while (m.find()) { String d = digits(m.group()); if (d.length() > best.length()) best = d; }
        if (best.isEmpty()) { Matcher one = Pattern.compile("\\b\\d{3,}\\b").matcher(t); if (one.find()) best = one.group(); }
        return best;
    }

    private static List<String> toks(String t) {
        List<String> l = new ArrayList<>();
        for (String w : t.split(" ")) if (!w.isEmpty()) l.add(w);
        return l;
    }

    private static String join(List<String> l, int from, int to) {
        StringBuilder b = new StringBuilder();
        for (int i = Math.max(0, from); i < Math.min(l.size(), to); i++) { if (b.length() > 0) b.append(' '); b.append(l.get(i)); }
        return b.toString();
    }

    private static Set<String> set(String[]... lists) {
        Set<String> s = new HashSet<>();
        for (String[] l : lists) for (String w : l) s.add(fold(w));
        return s;
    }

    private static int indexOf(List<String> tk, String[] words) {
        Set<String> s = set(words);
        for (int i = 0; i < tk.size(); i++) if (s.contains(tk.get(i))) return i;
        return -1;
    }

    /** Splits "X ko …" into {before, after}; sep = -1 when no separator. */
    private static String[] split(String t, String[] seps) {
        List<String> tk = toks(t);
        int i = indexOf(tk, seps);
        if (i <= 0) return null;
        return new String[]{join(tk, 0, i), join(tk, i + 1, tk.size())};
    }

    /** Cleans a name candidate; returns LAST for pronouns, "" when nothing is left, null when it does not look like a name. */
    private static String cleanName(String before, String[]... extra) {
        if (before == null) return "";
        if (has(before, KI)) return null; // "likho ki … X ko" — the ko belongs to the message
        List<String[]> lists = new ArrayList<>(Arrays.asList(extra));
        lists.add(CALL_FILLER);
        lists.add(NAME_FILLER);
        lists.add(SEND_V);
        lists.add(WA);
        lists.add(SMS_W);
        lists.add(MSG_W);
        lists.add(CALL);
        lists.add(CALL_VERB);
        lists.add(OPEN);
        lists.add(REDIAL);
        String n = strip(before, lists.toArray(new String[0][]));
        if (n.isEmpty()) return "";
        List<String> tk = toks(n);
        Set<String> pr = set(PRONOUN);
        boolean allPron = true;
        for (String w : tk) if (!pr.contains(w)) { allPron = false; break; }
        if (allPron) return LAST;
        Set<String> bad = set(NOT_NAME);
        for (String w : tk) if (bad.contains(w)) return null;
        if (tk.size() > 4) return null;
        if (digits(n).length() == n.replace(" ", "").length()) return null;
        return n;
    }

    // ---- message text after "X ko whatsapp par likho ki …"
    private static final String[] MSG_LEAD = concat(WA, MSG_W, SMS_W, SEND_V, OPEN, new String[]{"पर", "पे", "में", "par", "pe", "pr", "per",
            "me", "mein", "यह", "ये", "ye", "yeh", "yah", "ek", "एक", "please", "pls", "zara", "जरा", "जल्दी", "jaldi", "to", "on", "via",
            "msg", "ke", "के", "through", "se", "से", "ko", "को"});
    private static final String[] MSG_TRAIL = concat(WA, MSG_W, SMS_W, OPEN, new String[]{"भेज दो", "भेजो", "भेज", "भेजिए", "भेज दीजिए", "bhej do",
            "bhejo", "bhej", "bhejiye", "bhej dijiye", "send karo", "send kar do", "send", "likh do", "likho", "likh ke bhejo", "लिख दो", "लिखो",
            "लिखकर भेजो", "bol do", "bolo", "बोल दो", "बोलो", "kar do", "karo", "kro", "कर दो", "करो", "kaho", "कहो", "keh do", "कह दो",
            "पर", "पे", "में", "par", "pe", "pr", "me", "mein", "pe bhejo", "पर भेजो", "please", "pls"});

    private static String[] concat(String[]... ls) {
        List<String> l = new ArrayList<>();
        for (String[] a : ls) l.addAll(Arrays.asList(a));
        return l.toArray(new String[0]);
    }

    static String extractMessage(String after) {
        if (after == null) return "";
        List<String> tk = toks(after);
        Set<String> lead = set(MSG_LEAD), ki = set(KI);
        // "… likho ki <message>": everything after the first ki whose prefix is only command words
        for (int i = 0; i < Math.min(tk.size(), 8); i++) {
            if (ki.contains(tk.get(i))) {
                boolean onlyCmd = true;
                for (int j = 0; j < i; j++) if (!lead.contains(tk.get(j)) && !"do".equals(tk.get(j)) && !"दो".equals(tk.get(j))) { onlyCmd = false; break; }
                if (onlyCmd) { tk = tk.subList(i + 1, tk.size()); break; }
            }
        }
        // strip leading command words
        Set<String> joinVerb = set(new String[]{"kar", "bhej", "likh", "bol", "कर", "भेज", "लिख", "बोल", "keh", "कह", "bata", "बता"});
        String prev = "";
        int s = 0;
        while (s < tk.size()) {
            String w = tk.get(s);
            boolean isDo = ("do".equals(w) || "दो".equals(w)) && joinVerb.contains(prev);
            if (lead.contains(w) || isDo) { prev = w; s++; } else break;
        }
        String m = join(tk, s, tk.size());
        // strip trailing command phrases
        List<String> trail = new ArrayList<>();
        for (String w : MSG_TRAIL) trail.add(fold(w));
        Collections.sort(trail, (x, y) -> y.length() - x.length());
        boolean changed = true;
        while (changed && !m.isEmpty()) {
            changed = false;
            for (String w : trail) {
                if (m.equals(w)) { m = ""; changed = true; break; }
                if (m.endsWith(" " + w)) { m = m.substring(0, m.length() - w.length() - 1).trim(); changed = true; break; }
            }
        }
        return m.trim();
    }

    // ---------------------------------------------------------------- main entry
    /** @return the action for this command, or null when it should go to the AI. */
    static Cmd parse(String raw) {
        String t = norm(raw);
        if (t.isEmpty()) return null;
        int words = t.split(" ").length;
        boolean question = has(t, Q);
        boolean strongQ = has(t, STRONG_Q);

        // Alarm / timer
        if (has(t, ALARM) || (has(t, "जगाना", "जगा", "उठाना", "jagana", "jaga", "uthana", "jaga dena", "जगा देना") && Pattern.compile("\\d").matcher(t).find())) {
            if (question && !has(t, "लगाओ", "लगा", "सेट", "set", "lagao", "laga", "lagado", "रखो", "rakho", "लगा दो", "laga do")) return null;
            return cmd("alarm", t, null);
        }
        if (has(t, TIMER)) {
            Matcher m = Pattern.compile("(\\d+)\\s*(मिनट|minute|minutes|min|सेकंड|second|seconds|sec|घंटे|घंटा|hour|hours|ghante|ghanta)").matcher(t);
            if (m.find()) {
                int v = Integer.parseInt(m.group(1));
                String u = m.group(2);
                int secs = u.startsWith("से") || u.startsWith("sec") ? v : (u.startsWith("घ") || u.startsWith("h") || u.startsWith("gh")) ? v * 3600 : v * 60;
                return cmd("timer", String.valueOf(secs), null);
            }
        }

        // "phone pe kholo" = PhonePe app, not a call
        if (has(t, OPEN) && has(t, "फोन पे", "फोनपे", "phone pe", "phonepe", "phone pay", "phonepay", "फोन पै")) return cmd("open_app", "phonepe", null);

        // WhatsApp (by number, by contact name, share, open, WA call)
        if (has(t, WA)) return whatsapp(raw, t, question, strongQ);

        // SMS / message by name
        Cmd sm = sms(raw, t, strongQ);
        if (sm != null) return sm;

        // Calls
        Cmd c = call(raw, t, strongQ, words);
        if (c != null) return "none".equals(c.type) ? null : c;

        // Time / date (answered locally)
        if (words <= 8 && has(t, DATE_W)) return cmd("date", null, null);
        if (words <= 8 && has(t, "आज", "aaj", "today") && has(t, DAY_W) && !has(t, "अच्छा", "accha", "कैसा", "kaisa")) return cmd("date", null, null);
        if (words <= 8 && has(t, TIME_W) && has(t, TIME_Q) && !has(t, "कब", "kab", "when")) return cmd("time", null, null);

        // Torch
        if (has(t, TORCH) || (has(t, "लाइट", "light") && has(t, "जलाओ", "jalao", "ऑन", "on", "बंद", "band", "off", "ऑफ", "बुझाओ"))) {
            if (question && !has(t, "जलाओ", "jalao", "ऑन", "on", "बंद", "band", "off", "ऑफ", "करो", "karo", "kar", "chalu", "चालू")) return null;
            return cmd("torch", has(t, OFF) ? "off" : "on", null);
        }
        // Camera
        if (has(t, CAMERA) && !question) return cmd("camera", null, null);

        // YouTube
        boolean yt = has(t, YT);
        boolean play = has(t, PLAY);
        if (yt || (play && has(t, SONG))) {
            if (question && !play && !has(t, OPEN)) return null;
            if (has(t, SEARCH) && !play) {
                String q = strip(t, YT, SEARCH, FILLER);
                return q.isEmpty() ? cmd("open_app", "youtube", null) : cmd("yt_search", restore(raw, q), null);
            }
            String q = strip(t, YT, PLAY, OPEN, FILLER, new String[]{"वीडियो", "विडियो", "video", "videos"});
            if (q.isEmpty() && !play) return cmd("open_app", "youtube", null);
            if (q.isEmpty()) q = "भजन";
            return cmd("youtube", restore(raw, q), null);
        }

        // Maps
        if (has(t, MAP) || has(t, GO)) {
            String place = strip(t, MAP, GO, OPEN, FILLER, new String[]{"दिखाओ", "dikhao", "बताओ", "batao", "show", "का", "की", "के", "ka", "ki", "ke",
                    "तक", "tak", "जाना", "jana", "है", "hai", "kaise", "कैसे", "मुझे", "find", "where", "is", "कहां", "kahan", "kaha"});
            if (question && !has(t, MAP) && place.isEmpty()) return null;
            return cmd("maps", restore(raw, place), null);
        }

        // Google search
        if (has(t, SEARCH)) {
            String q = strip(t, SEARCH, FILLER, new String[]{"google", "गूगल", "पर", "about"});
            return q.isEmpty() ? cmd("open_url", "https://www.google.com", null) : cmd("search", restore(raw, q), null);
        }

        // Share
        if (has(t, SHARE) && has(t, "करो", "karo", "kar", "कर", "do", "दो")) {
            String txt = strip(t, SHARE, FILLER, new String[]{"यह", "ये", "ye", "yeh", "this", "text", "टेक्स्ट"});
            return cmd("share", txt.isEmpty() ? "मैं मित्र ऐप इस्तेमाल कर रहा हूँ — आपका AI मित्र!" : restore(raw, txt), null);
        }

        // Open app / website
        if (has(t, OPEN)) {
            String target = strip(t, OPEN, FILLER);
            if (target.isEmpty()) return null;
            if (question && words > 4) return null;
            return cmd("open_app", target, null);
        }
        return null;
    }

    private static Cmd whatsapp(String raw, String t, boolean question, boolean strongQ) {
        boolean callI = has(t, WA_CALL) && !has(t, KI) && !has(t, "bolo", "बोलो", "likho", "लिखो", "bhejo", "भेजो", "message", "मैसेज", "msg", "kaho", "कहो");
        boolean msgIntent = has(t, MSG_W) || has(t, SEND_V) || has(t, KI);
        String num = phoneDigits(t);
        if (num.length() >= 10) {
            String rest = extractMessage(strip(t.replace(num, " ").replaceAll("\\+?\\d[\\d\\s-]{2,}\\d", " "), new String[]{"नंबर", "number", "को", "ko", "पर", "pe", "par"}));
            return cmd("whatsapp", num, callI ? "" : restore(raw, rest));
        }
        if (strongQ && !msgIntent && !callI) return null;
        String[] sp = split(t, KO);
        if (sp == null) sp = split(t, POSS);
        String name = sp == null ? null : cleanName(sp[0]);
        if (sp == null) {
            // "whatsapp sonu" / "sonu whatsapp" without "ko": only a very short remainder can be a name
            String n = cleanName(t);
            if (n != null && !n.isEmpty() && toks(n).size() <= 2 && !has(t, KI) && !has(t, SEND_V) && !has(t, MSG_W)) name = n;
        }
        if (name != null && !name.isEmpty()) {
            String nm = LAST.equals(name) ? LAST : restore(raw, name);
            if (callI) return cmd("whatsapp_call", nm, null);
            String msg = sp == null ? "" : extractMessage(sp[1]);
            if (endsPossessive(msg)) return null; // "X ko birthday ka message likho" → let the AI write it
            return cmd("whatsapp_contact", nm, restore(raw, msg));
        }
        String rest = extractMessage(t);
        if (msgIntent && !rest.isEmpty() && !endsPossessive(rest)) return cmd("whatsapp_share", restore(raw, rest), null);
        if (question && !has(t, OPEN)) return null;
        return cmd("open_app", "whatsapp", null);
    }

    private static boolean endsPossessive(String m) {
        return m != null && (m.endsWith(" ka") || m.endsWith(" ki") || m.endsWith(" ke") || m.endsWith(" का") || m.endsWith(" की") || m.endsWith(" के")
                || m.equals("ka") || m.equals("ki") || m.equals("का") || m.equals("की"));
    }

    private static Cmd sms(String raw, String t, boolean strongQ) {
        boolean sms = has(t, SMS_W) || has(t, "sms", "एसएमएस");
        boolean msg = has(t, MSG_W);
        if (!sms && !msg) return null;
        if (has(t, OPEN) && split(t, KO) == null) return cmd("open_app", "messages", null);
        if (strongQ) return null;
        boolean send = has(t, SEND_V) || has(t, KI) || has(t, "करना", "karna");
        String num = phoneDigits(t);
        String type = sms ? "sms_contact" : "message_contact";
        if (num.length() >= 10) {
            String rest = extractMessage(strip(t.replaceAll("\\+?\\d[\\d\\s-]{2,}\\d", " "), new String[]{"नंबर", "number", "को", "ko", "पर", "pe", "par"}));
            return cmd(sms ? "sms" : "message_number", num, restore(raw, rest));
        }
        String[] sp = split(t, KO);
        if (sp == null || !send && !sms) return sms && !send ? cmd("open_app", "messages", null) : null;
        String name = cleanName(sp[0]);
        if (name == null || name.isEmpty()) return null;
        String m = extractMessage(sp[1]);
        if (endsPossessive(m)) return null;
        return cmd(type, LAST.equals(name) ? LAST : restore(raw, name), restore(raw, m));
    }

    private static final String[] NUM_ASK = {"बताओ", "बता दो", "batao", "bata do", "दिखाओ", "dikhao", "dikha do", "दिखा दो", "क्या है", "kya hai",
            "चाहिए", "chahiye", "bolo", "बोलो", "do", "दो", "dijiye", "दीजिए", "kitna hai", "कितना है", "show", "tell", "what", "is", "kya", "क्या"};
    private static final String[] NUM_DIAL = {"milao", "मिलाओ", "mila do", "मिला दो", "lagao", "लगाओ", "laga do", "लगा दो", "dial", "डायल",
            "ghumao", "घुमाओ", "milaiye", "मिलाइए", "milado", "lagado", "मिला", "mila"};
    private static final String[] NUMBER_W = {"number", "नंबर", "नम्बर", "nambar", "no", "numbar", "num"};
    private static final String[] BAAT = {"baat karao", "baat karwao", "baat karvao", "बात कराओ", "बात करवाओ", "baat karni hai", "बात करनी है",
            "baat karna hai", "बात करना है", "baat kara do", "बात करा दो", "बात करवा दो", "baat karwa do"};

    private static Cmd call(String raw, String t, boolean strongQ, int words) {
        boolean callWord = has(t, CALL);
        boolean baat = has(t, BAAT);
        boolean numberW = has(t, NUMBER_W);
        boolean dialV = has(t, NUM_DIAL);
        boolean redial = has(t, REDIAL);
        boolean numberAsk = numberW && !dialV && !callWord && has(t, NUM_ASK);
        if (numberAsk) {
            String[] sp = split(t, POSS);
            if (sp == null) return null;
            String name = cleanName(sp[0]);
            if (name == null || name.isEmpty()) return null;
            return cmd("contact_number", LAST.equals(name) ? LAST : restore(raw, name), null);
        }
        if (!callWord && !baat && !(numberW && dialV)) return null;
        if (strongQ) return null;
        if (has(t, "था", "थी", "थे", "किया", "kiya", "tha", "thi", "आया", "aaya", "aya", "आई", "aayi", "kyu nahi", "उठाया", "uthaya")) return null; // statement about a past call
        boolean verb = has(t, CALL_VERB) || baat || dialV;
        // "phone app kholo" / "call app kholo" / "dialer kholo" → just open the dialer
        if (has(t, OPEN) && !has(t, KO) && !has(t, "karo", "करो", "lagao", "लगाओ", "milao", "मिलाओ", "kar do", "कर दो")) return cmd("dial_open", null, null);
        String num = phoneDigits(t);
        if (num.length() >= 3) return cmd("call_number", num, null);
        String[] sp = split(t, KO);
        if (sp == null && baat) sp = split(t, new String[]{"से", "se"});
        if (sp == null) sp = split(t, POSS);
        String name = cleanName(sp == null ? t : sp[0]);
        if (name == null) return sp == null ? null : cmd("none", null, null);
        if (!verb && sp == null) return null;                 // "mera phone kho gaya" etc.
        if (name.isEmpty()) {
            if (redial) return cmd("call_contact", LAST, null);
            if (!verb && words > 3) return null;
            return cmd("ask_name", "call", null);
        }
        return cmd("call_contact", LAST.equals(name) ? LAST : restore(raw, name), null);
    }

    // ---------------------------------------------------------------- follow-ups
    /** "पहला" → 0, "dusra" → 1 … ; -2 = last; -1 = not an ordinal answer. */
    static int ordinal(String raw) {
        String t = norm(raw);
        if (t.isEmpty() || t.split(" ").length > 5) return -1;
        String[][] o = {
                {"पहला", "पहले", "पहली", "pehla", "pahla", "pehle", "pahle", "pehli", "pahli", "first", "1", "1st", "ek", "एक", "one"},
                {"दूसरा", "दूसरे", "दूसरी", "दुसरा", "dusra", "doosra", "dusre", "doosre", "dusri", "second", "2", "2nd", "do", "दो", "two"},
                {"तीसरा", "तीसरे", "तीसरी", "teesra", "tisra", "teesre", "tisre", "third", "3", "3rd", "teen", "तीन", "three"},
                {"चौथा", "चौथे", "chautha", "chauthe", "fourth", "4", "4th", "char", "चार", "four"},
                {"पांचवां", "पांचवा", "पांचवे", "panchwa", "panchva", "fifth", "5", "5th", "paanch", "पांच", "five"},
                {"छठा", "छठे", "chhatha", "chhata", "sixth", "6", "6th", "छह", "six"}};
        for (int i = 0; i < o.length; i++) if (has(t, o[i])) return i;
        if (has(t, "आखिरी", "आखरी", "aakhri", "akhri", "last", "अंतिम", "antim")) return -2;
        return -1;
    }

    static boolean isCancel(String raw) {
        String t = norm(raw);
        if (t.split(" ").length > 5) return false;
        return has(t, "नहीं", "नही", "nahi", "nahin", "na", "no", "mat", "मत", "cancel", "रहने दो", "rehne do", "rahne do", "छोड़ो", "छोडो",
                "chhodo", "chodo", "कोई नहीं", "koi nahi", "stop", "रुको", "ruko", "बस", "bas");
    }

    private static final String[] CMD_HINT = {"ਕਰੋ", "ਖੋਲ੍ਹੋ", "ਚਲਾਓ", "ਭੇਜੋ", "કરો", "ખોલો", "ચલાવો", "મોકલો", "उघडा", "लाव", "পাঠাও", "করো", "খোলো", "চালাও", "কল", "ফোন", "ਫੋਨ", "ਕਾਲ", "ફોન", "કૉલ", "கால்", "போன்", "ఫోన్", "కాల్", "ಕಾಲ್", "ಫೋನ್", "കോൾ", "ഫോൺ", "ଫୋନ", "کال", "فون", "लगावऽ", "करऽ", "खोलऽ", "चलावऽ", "लगाव", "चलाव", "lao", "करो", "कर दो", "खोलो", "चलाओ", "लगाओ", "भेजो", "बोलो", "लिखो", "दिखाओ", "बंद", "चालू", "मिलाओ",
            "karo", "kar do", "kholo", "chalao", "lagao", "bhejo", "bolo", "likho", "dikhao", "band", "chalu", "milao", "open", "play", "call",
            "send", "set", "start", "show", "dial", "कॉल", "फोन", "phone", "message", "मैसेज", "whatsapp", "app", "ऐप", "kijiye", "कीजिए"};

    /** True when the text sounds like a phone command (used to nudge the AI to answer with an action tag). */
    static boolean looksLikeCommand(String raw) { return has(norm(raw), CMD_HINT); }

    // ---------------------------------------------------------------- case restore
    /** Finds the normalised phrase in the original text and returns it with the user's own spelling/case. */
    static String restore(String raw, String phrase) {
        if (phrase == null || phrase.isEmpty() || raw == null) return phrase == null ? "" : phrase;
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < phrase.length(); i++) {
            char c = phrase.charAt(i);
            if (c == ' ') { re.append("[\\s,.!?।|;:\"'“”‘’()…-]+"); continue; }
            String alt = nuktaAlt(c);
            if (alt != null) re.append("(?:").append(Pattern.quote(String.valueOf(c))).append("\u093C?|").append(alt).append(")");
            else if (c == '\u0902') re.append("[\u0902\u0901]");
            else if (c >= '0' && c <= '9') re.append("[").append(c).append((char) ('\u0966' + (c - '0'))).append("]");
            else re.append(Pattern.quote(String.valueOf(c)));
            re.append("[\u200C\u200D]?");
        }
        try {
            Matcher m = Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(raw);
            if (m.find()) return m.group().trim();
        } catch (Exception ignored) { }
        return phrase;
    }

    private static String nuktaAlt(char c) {
        switch (c) {
            case '\u0915': return "\u0958";
            case '\u0916': return "\u0959";
            case '\u0917': return "\u095A";
            case '\u091C': return "\u095B";
            case '\u0921': return "\u095C";
            case '\u0922': return "\u095D";
            case '\u092B': return "\u095E";
            case '\u092F': return "\u095F";
            default: return null;
        }
    }
}

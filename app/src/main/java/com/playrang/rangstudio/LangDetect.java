package com.playrang.rangstudio;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Detects which language/script the user wrote or spoke in, so Mitra can answer in it and switch the
 * speech recognizer and TTS voice to match. Pure Java (unit-tested).
 *
 * Scripts decide most languages directly. Devanagari is split into Marathi / Nepali / Hindi by marker words;
 * Bhojpuri, Maithili, Magahi etc. written in Devanagari are treated as Hindi (same recognizer and voice, and the
 * AI keeps the user's own dialect). Latin text is split into English vs. Roman Hindi (Hinglish).
 */
final class LangDetect {
    private LangDetect() {}

    /** Result: a {@link Lang} id ("hi", "bn", "ta", "te", "mr", "gu", "pa", "kn", "ml", "or", "ur", "ne", "en") and whether it is Roman script. */
    static final class Result {
        final String id; final boolean roman; final double confidence;
        Result(String id, boolean roman, double confidence) { this.id = id; this.roman = roman; this.confidence = confidence; }
        @Override public String toString() { return id + (roman ? "-roman" : "") + "@" + Math.round(confidence * 100) / 100.0; }
    }

    private static final Set<String> MARATHI = new HashSet<>(Arrays.asList(
            "आहे", "आहेत", "नाही", "काय", "मला", "तुम्ही", "तुला", "माझा", "माझी", "माझे", "माझ्या", "आणि", "करा", "सांगा", "सांग",
            "उघडा", "उघड", "लावा", "पाठवा", "पाठव", "कसा", "कशी", "कसे", "आम्ही", "तुमचा", "तुमची", "आपण", "किती",
            "वाजले", "वाजता", "झाले", "झाला", "पाहिजे", "हवे", "नको", "कुठे", "केव्हा", "कोण", "ऐका", "उद्या", "किती", "आहात"));
    private static final Set<String> MARATHI_STRONG = new HashSet<>(Arrays.asList(
            "आहे", "आहेत", "नाही", "मला", "तुम्ही", "माझा", "माझी", "माझे", "माझ्या", "आणि", "उघडा", "पाठवा", "वाजले", "पाहिजे", "आम्ही", "तुमचा", "केव्हा"));
    private static final Set<String> NEPALI = new HashSet<>(Arrays.asList(
            "छ", "छन्", "हुन्छ", "गर्नुहोस्", "गर", "भन्नुहोस्", "तपाईं", "तपाईंको", "मलाई", "हजुर", "कस्तो", "कति", "होइन", "थियो", "गर्छ", "पनि", "हो?"));
    private static final Set<String> HINDI = new HashSet<>(Arrays.asList(
            "है", "हैं", "का", "की", "के", "को", "में", "से", "और", "क्या", "करो", "कर", "दो", "मुझे", "मेरा", "मेरी", "आप", "हम", "यह", "वह",
            "नहीं", "कैसे", "चलाओ", "खोलो", "लगाओ", "बताओ", "भेजो", "बानी", "रउआ", "हमार", "कइसे", "बा", "छी", "अहाँ"));

    private static final Set<String> HINDI_LA = new HashSet<>(Arrays.asList("पहला", "अकेला", "वाला", "काला", "ताला", "मेला", "केला", "गला",
            "पीला", "नीला", "छाला", "मसाला", "फैसला", "सिलसिला", "मामला", "हमला", "बदला", "अगला", "पिछला", "निकला", "चला", "मिला", "खुला"));

    /** Common English words: if most Latin words are English, the user wrote English (not Hinglish). */
    private static final Set<String> EN = new HashSet<>(Arrays.asList(
            "the", "a", "an", "is", "are", "was", "were", "what", "how", "why", "who", "when", "where", "which", "please", "can", "could",
            "would", "will", "you", "your", "me", "my", "i", "it", "this", "that", "of", "to", "in", "on", "for", "with", "and", "or",
            "open", "call", "play", "send", "set", "turn", "show", "tell", "give", "make", "take", "search", "find", "remind", "message",
            "song", "songs", "video", "time", "date", "today", "tomorrow", "weather", "news", "do", "does", "did", "have", "has", "be",
            "about", "from", "at", "up", "down", "off", "volume", "alarm", "timer", "torch", "flashlight", "camera", "photo", "navigate",
            "route", "map", "maps", "battery", "settings", "wifi", "bluetooth", "brightness", "note", "copy", "text", "say", "hello",
            "hi", "hey", "thanks", "thank", "good", "morning", "night", "much", "many", "need", "want", "know", "explain", "meaning",
            "write", "story", "joke", "who's", "what's", "it's", "i'm", "don't", "is", "am", "then", "after", "minutes", "minute", "hour"));
    /** Roman-Hindi words that rarely appear in English. */
    private static final Set<String> HINGLISH = new HashSet<>(Arrays.asList(
            "hai", "hain", "ka", "ki", "ke", "ko", "mein", "me", "se", "aur", "kya", "kaise", "kyu", "kyun", "kyon", "karo", "kar", "kro",
            "do", "de", "mujhe", "mera", "meri", "mere", "aap", "tum", "hum", "yeh", "ye", "woh", "wo", "nahi", "nahin", "batao", "bata",
            "chalao", "kholo", "lagao", "bhejo", "bolo", "likho", "gana", "gaana", "bhajan", "kitna", "kitne", "kab", "kahan", "kaun", "abhi",
            "accha", "acha", "theek", "thik", "haan", "han", "ji", "bhai", "yaar", "zara", "jara", "wala", "wali", "dijiye", "kijiye",
            "chahiye", "baje", "aaj", "kal", "raat", "subah", "shaam", "dobara", "phir", "fir", "baat", "kuch", "sab", "bahut",
            "kaisa", "kaisi", "hoga", "hogi", "tha", "thi", "raha", "rahe", "rahi", "gaya", "gayi", "hua", "hui", "liye", "sath", "saath",
            "bhaiya", "didi", "mummy", "papa", "bajao", "sunao", "dikhao", "milao", "rakho", "band", "chalu", "jalao", "bujhao", "karna",
            "karni", "karne", "jao", "aao", "chalo", "dekho", "suno", "samjhao", "matlab", "bataiye", "batayein", "kijie", "bhejna",
            "ho", "hoon", "hu", "hun", "tera", "teri", "apna", "apni", "unka", "uska", "inka", "iska", "wahan", "yahan", "kyunki",
            "lekin", "magar", "agar", "toh", "to", "bhi", "sirf", "bas", "pe", "par", "ek", "dono", "kaam", "paisa", "paise", "rupay"));
    /** Roman Bhojpuri / Punjabi / Marathi / Bengali give-aways: answer in that dialect, Roman script. */
    private static final String[][] ROMAN_DIALECT = {
            {"bho", "bani", "baani", "raua", "rauaa", "hamar", "kaise ba", "ka ho", "tani", "karab", "jaib", "rahal", "baadu", "badu", "bate"},
            {"pa", "tusi", "tuhada", "kida", "kiddan", "menu", "mainu", "sanu", "karo ji", "hunda", "nahi ji", "paaji", "veere", "dasso"},
            {"mr", "aahe", "nahi aahe", "kay", "mala", "tumhi", "majha", "majhi", "kara", "sanga", "kasa", "kuthe", "pahije"},
            {"bn", "ami", "tumi", "apni", "kemon", "korbo", "koro", "bolo na", "achho", "achi", "ki khobor", "bhalo", "amar", "tomar"},
    };

    /** Detects the language of one message. Returns null for empty / digits-only text. */
    static Result detect(String text) {
        if (text == null) return null;
        int[] c = new int[13];
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int s = script(ch);
            if (s >= 0) { c[s]++; letters++; }
        }
        if (letters == 0) return null;
        int best = 0;
        for (int i = 1; i < c.length; i++) if (c[i] > c[best]) best = i;
        double conf = c[best] / (double) letters;
        switch (best) {
            case 0: return deva(text, conf);
            case 1: return new Result("bn", false, conf);
            case 2: return new Result("pa", false, conf);
            case 3: return new Result("gu", false, conf);
            case 4: return new Result("or", false, conf);
            case 5: return new Result("ta", false, conf);
            case 6: return new Result("te", false, conf);
            case 7: return new Result("kn", false, conf);
            case 8: return new Result("ml", false, conf);
            case 9: return new Result("ur", false, conf);
            default: return latin(text, conf);
        }
    }

    /** 0 Devanagari, 1 Bengali/Assamese, 2 Gurmukhi, 3 Gujarati, 4 Odia, 5 Tamil, 6 Telugu, 7 Kannada, 8 Malayalam, 9 Arabic/Urdu, 10 Latin; -1 other. */
    static int script(char c) {
        if (c >= 0x0900 && c <= 0x097F) return c >= 0x0966 && c <= 0x096F ? -1 : 0;
        if (c >= 0x0980 && c <= 0x09FF) return 1;
        if (c >= 0x0A00 && c <= 0x0A7F) return 2;
        if (c >= 0x0A80 && c <= 0x0AFF) return 3;
        if (c >= 0x0B00 && c <= 0x0B7F) return 4;
        if (c >= 0x0B80 && c <= 0x0BFF) return 5;
        if (c >= 0x0C00 && c <= 0x0C7F) return 6;
        if (c >= 0x0C80 && c <= 0x0CFF) return 7;
        if (c >= 0x0D00 && c <= 0x0D7F) return 8;
        if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F) || (c >= 0xFB50 && c <= 0xFDFF)) return 9;
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return 10;
        return -1;
    }

    private static String[] words(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}'’ ]+", " ").trim().split("\\s+");
    }

    private static Result deva(String text, double conf) {
        int mr = 0, mrStrong = 0, ne = 0, hi = 0;
        for (String w : words(text)) {
            String f = CommandParser.fold(w);
            if (MARATHI.contains(w) || MARATHI.contains(f)) mr++;
            if (MARATHI_STRONG.contains(w)) mrStrong++;
            if (NEPALI.contains(w)) ne++;
            if (HINDI.contains(w) || HINDI.contains(f)) hi++;
            // Marathi case endings glued to names/nouns: "सोनूला", "आईला", "बाबांना"
            if (w.length() > 3 && w.endsWith("ला") && !HINDI_LA.contains(w)) mr++;
        }
        if (ne >= 2 && ne > hi) return new Result("ne", false, conf);
        if (mrStrong >= 1 && mr >= hi) return new Result("mr", false, conf);
        if (mr >= 2 && mr > hi) return new Result("mr", false, conf);
        return new Result("hi", false, conf);
    }

    private static Result latin(String text, double conf) {
        String[] w = words(text);
        int en = 0, hg = 0;
        for (String x : w) {
            boolean e = EN.contains(x), h = HINGLISH.contains(x);
            if (e && !h) en++;
            else if (h && !e) hg++;
        }
        String low = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ") + " ";
        for (String[] d : ROMAN_DIALECT) {
            int hits = 0;
            for (int i = 1; i < d.length; i++) if (low.contains(" " + d[i] + " ")) hits++;
            if (hits >= 2 || (hits == 1 && w.length <= 3 && hg == 0)) return new Result(d[0], true, conf);
        }
        if (hg == 0 && en == 0) return new Result(w.length <= 2 ? "hi" : "en", w.length <= 2, conf * 0.5);
        if (hg >= en) return new Result("hi", true, conf);
        return new Result("en", false, conf);
    }

    /** Line added to the AI system prompt so it answers in exactly this language and script. */
    static String instruction(Result r) {
        if (r == null) return null;
        String name;
        switch (r.id) {
            case "bn": name = "Bengali (বাংলা script)"; break;
            case "pa": name = r.roman ? "Punjabi written in Roman letters" : "Punjabi (Gurmukhi script)"; break;
            case "gu": name = "Gujarati (ગુજરાતી script)"; break;
            case "or": name = "Odia (ଓଡ଼ିଆ script)"; break;
            case "ta": name = "Tamil (தமிழ் script)"; break;
            case "te": name = "Telugu (తెలుగు script)"; break;
            case "kn": name = "Kannada (ಕನ್ನಡ script)"; break;
            case "ml": name = "Malayalam (മലയാളം script)"; break;
            case "ur": name = "Urdu (اردو Nastaliq script)"; break;
            case "mr": name = r.roman ? "Marathi written in Roman letters" : "Marathi (Devanagari script)"; break;
            case "ne": name = "Nepali (Devanagari script)"; break;
            case "bho": name = "Bhojpuri written in Roman letters"; break;
            case "en": name = "simple Indian English"; break;
            default: name = r.roman ? "Hinglish (Hindi written in Roman/English letters, like the user)"
                    : "the user's own language in Devanagari (Hindi; if the user wrote Bhojpuri, Maithili, Magahi or another dialect, reply in that same dialect)";
        }
        return "यूज़र ने अभी " + name + " में लिखा/बोला है — जवाब ठीक इसी भाषा और लिपि में दो। Reply ONLY in " + name + ".";
    }

    /** Speech-recognizer / TTS locale for a detected language. */
    static String locale(String id) {
        switch (id == null ? "" : id) {
            case "bn": return "bn-IN";
            case "pa": return "pa-IN";
            case "gu": return "gu-IN";
            case "or": return "or-IN";
            case "ta": return "ta-IN";
            case "te": return "te-IN";
            case "kn": return "kn-IN";
            case "ml": return "ml-IN";
            case "ur": return "ur-IN";
            case "mr": return "mr-IN";
            case "ne": return "ne-NP";
            case "en": return "en-IN";
            default: return "hi-IN";
        }
    }

    /**
     * Language to listen in next time, in Auto mode: switch only on a confident, non-trivial detection, so one
     * stray English word ("ok") does not flip a Hindi speaker's recognizer to English.
     */
    static String nextListenLocale(String current, Result r, int words) {
        if (r == null) return current;
        if (r.roman && !"en".equals(r.id)) return "hi-IN"; // Roman Hindi / dialect: the Hindi recognizer writes it best
        if ("en".equals(r.id) && words < 3) return current == null ? "hi-IN" : current;
        if (r.confidence < 0.6) return current == null ? "hi-IN" : current;
        return locale(r.id);
    }
}

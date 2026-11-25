package com.playrang.rangstudio;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spoken Hindi / Hinglish / English times for alarms and reminders: "सुबह साढ़े 6 बजे", "shaam 7:30", "kal 9 baje",
 * "10 minute baad", "tomorrow 8 pm". Pure Java (unit-tested).
 */
final class TimeParse {
    private TimeParse() {}

    private static final Map<String, Integer> NUM = new HashMap<>();
    static {
        String[][] n = {{"एक", "1"}, {"दो", "2"}, {"तीन", "3"}, {"चार", "4"}, {"पांच", "5"}, {"पाँच", "5"}, {"छह", "6"}, {"छः", "6"}, {"छे", "6"},
                {"सात", "7"}, {"आठ", "8"}, {"नौ", "9"}, {"दस", "10"}, {"ग्यारह", "11"}, {"बारह", "12"}, {"पंद्रह", "15"}, {"बीस", "20"},
                {"पच्चीस", "25"}, {"तीस", "30"}, {"चालीस", "40"}, {"पैंतालीस", "45"}, {"पचास", "50"}, {"साठ", "60"},
                {"ek", "1"}, {"teen", "3"}, {"char", "4"}, {"chaar", "4"}, {"paanch", "5"}, {"panch", "5"}, {"chhe", "6"}, {"che", "6"},
                {"saat", "7"}, {"aath", "8"}, {"nau", "9"}, {"das", "10"}, {"gyarah", "11"}, {"barah", "12"}, {"baarah", "12"},
                {"pandrah", "15"}, {"bees", "20"}, {"tees", "30"}, {"one", "1"}, {"two", "2"}, {"three", "3"}, {"four", "4"},
                {"five", "5"}, {"six", "6"}, {"seven", "7"}, {"eight", "8"}, {"nine", "9"}, {"ten", "10"}, {"eleven", "11"},
                {"twelve", "12"}, {"fifteen", "15"}, {"twenty", "20"}, {"thirty", "30"}, {"forty", "40"}, {"fifty", "50"}};
        for (String[] p : n) NUM.put(CommandParser.fold(p[0]), Integer.parseInt(p[1]));
    }

    /** Hindi/English number words → digits ("सात बजे" → "7 बजे"). Input should already be {@link CommandParser#norm}ed. */
    static String digitsFor(String t) {
        String s = " " + t + " ";
        for (Map.Entry<String, Integer> e : NUM.entrySet()) s = s.replace(" " + e.getKey() + " ", " " + e.getValue() + " ");
        return s.trim();
    }

    private static boolean has(String t, String... w) { return CommandParser.has(t, w); }

    /** {hour 0-23, minute} from a clock time in the text, or null when it has none. */
    static int[] clock(String raw) {
        String t = digitsFor(CommandParser.norm(raw.replaceAll("(\\d)\\s*:\\s*(\\d)", "$1.$2")));
        Matcher m = Pattern.compile("(?<![\\d.])(\\d{1,2})(?:\\s*[:.]\\s*(\\d{2}))?(?![\\d])\\s*(am|pm|a\\.m|p\\.m|बजे|बजकर|baje|bje|o'clock|oclock)?").matcher(t);
        Integer h = null; int min = 0;
        while (m.find()) {
            // skip numbers followed by minute/hour units ("10 minute baad")
            String after = t.substring(m.end()).trim();
            if (after.matches("^(मिनट|minute|minutes|min|मिनिट|सेकंड|second|seconds|sec|घंटे|घंटा|hour|hours|ghante|ghanta|दिन|din|days?)(\\s.*|$)")) continue;
            h = Integer.parseInt(m.group(1));
            if (m.group(2) != null) min = Integer.parseInt(m.group(2));
            else {
                Matcher mm = Pattern.compile("^(\\d{1,2})\\s*(मिनट|minute|min|मिनिट)").matcher(after.replaceFirst("^(बजकर|bajkar)\\s*", ""));
                if (after.startsWith("बजकर") || after.startsWith("bajkar")) { if (mm.find()) min = Integer.parseInt(mm.group(1)); }
            }
            break;
        }
        if (h == null) return null;
        if (has(t, "साढ़े", "साढे", "sadhe", "saadhe", "sade")) min = 30;
        else if (has(t, "सवा", "sawa", "sava")) min = 15;
        else if (has(t, "पौने", "paune", "pone")) { min = 45; h = h - 1; if (h <= 0) h += 12; }
        boolean pm = has(t, "शाम", "sham", "shaam", "evening", "pm", "p.m", "दोपहर", "dopahar", "dopehar", "afternoon");
        boolean night = has(t, "रात", "raat", "rat", "night", "tonight");
        boolean am = has(t, "सुबह", "subah", "subha", "morning", "am", "a.m", "सवेरे", "savere");
        if (h > 23 || min > 59) return null;
        if (pm && h < 12) h += 12;
        if (night) { if (h >= 6 && h < 12) h += 12; else if (h == 12) h = 0; }
        if (am && h == 12) h = 0;
        return new int[]{h, min};
    }

    /** Minutes from now for "10 minute baad", "2 ghante mein", "in 30 minutes"; -1 when not relative. */
    static int relativeMinutes(String raw) {
        String t = digitsFor(CommandParser.norm(raw));
        Matcher m = Pattern.compile("(\\d+)\\s*(मिनट|मिनिट|minute|minutes|min|mins|घंटे|घंटा|घंटों|hour|hours|hr|hrs|ghante|ghanta)\\s*(बाद|में|मे|baad|bad|me|mein|later)?").matcher(t);
        if (!m.find()) {
            Matcher in = Pattern.compile("\\bin\\s+(\\d+)\\s*(minute|minutes|min|hour|hours)").matcher(t);
            if (!in.find()) {
                if (has(t, "आधे घंटे", "aadhe ghante", "adhe ghante", "half an hour", "half hour")) return 30;
                return -1;
            }
            int v = Integer.parseInt(in.group(1));
            return in.group(2).startsWith("h") ? v * 60 : v;
        }
        boolean rel = m.group(3) != null || has(t, "बाद", "baad", "later", "after");
        if (!rel) return -1;
        int v = Integer.parseInt(m.group(1));
        String u = m.group(2);
        return u.startsWith("घ") || u.startsWith("h") || u.startsWith("gh") ? v * 60 : v;
    }

    /** 0 today, 1 tomorrow ("कल" with a future verb, "tomorrow"), 2 day after ("परसों"); -1 when the text names no day. */
    static int dayOffset(String raw) {
        String t = CommandParser.norm(raw);
        if (has(t, "परसों", "parso", "parson", "day after tomorrow")) return 2;
        if (has(t, "कल", "kal", "tomorrow", "kal subah", "कल सुबह")) return 1;
        if (has(t, "आज", "aaj", "today", "tonight")) return 0;
        return -1;
    }

    /**
     * When a reminder should fire. @return epoch millis, or -1 when no time was given (caller asks / uses a default).
     * A clock time already past today moves to tomorrow unless a day was named.
     */
    static long when(String raw, Calendar now) {
        int rel = relativeMinutes(raw);
        if (rel > 0) return now.getTimeInMillis() + rel * 60000L;
        int[] c = clock(raw);
        int day = dayOffset(raw);
        if (c == null && day < 0) return -1;
        Calendar k = (Calendar) now.clone();
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        if (c == null) { k.set(Calendar.HOUR_OF_DAY, 9); k.set(Calendar.MINUTE, 0); }
        else { k.set(Calendar.HOUR_OF_DAY, c[0]); k.set(Calendar.MINUTE, c[1]); }
        if (day > 0) k.add(Calendar.DAY_OF_MONTH, day);
        else if (k.getTimeInMillis() <= now.getTimeInMillis()) {
            // "5 बजे" said at 6 pm: if no am/pm word, try the evening first, else tomorrow
            if (c != null && c[0] < 12 && !has(CommandParser.norm(raw), "सुबह", "subah", "morning", "am") && k.getTimeInMillis() + 12 * 3600000L > now.getTimeInMillis()) k.add(Calendar.HOUR_OF_DAY, 12);
            else k.add(Calendar.DAY_OF_MONTH, 1);
        }
        return k.getTimeInMillis();
    }

    private static final String[] REM_WORDS = {"याद दिलाना", "याद दिला देना", "याद दिला दो", "याद दिलाओ", "याद दिलाइए", "रिमाइंडर", "reminder",
            "remind me", "remind", "yaad dilana", "yaad dila dena", "yaad dila do", "yaad dilao", "yad dilana", "रिमाइंड", "लगाओ", "lagao",
            "लगा दो", "laga do", "सेट करो", "set karo", "set", "कि", "ki", "to", "मुझे", "mujhe", "me", "मेरे", "को", "ko", "please",
            "प्लीज", "बजे", "baje", "at", "on", "का", "ka", "की", "for", "about", "बाद", "baad", "में", "mein", "का रिमाइंडर", "ka reminder"};

    /** What the reminder is about: the text with reminder words and time words removed. */
    static String reminderTitle(String raw) {
        String t = CommandParser.norm(raw);
        t = t.replaceAll("\\d{1,2}\\s*[:.]\\s*\\d{2}", " ").replaceAll("\\d+\\s*(मिनट|मिनिट|minute|minutes|min|घंटे|घंटा|hour|hours|ghante|ghanta)", " ")
                .replaceAll("\\b\\d{1,2}\\b", " ");
        t = CommandParser.strip(t, REM_WORDS, new String[]{"सुबह", "subah", "शाम", "shaam", "sham", "रात", "raat", "दोपहर", "dopahar",
                "कल", "kal", "आज", "aaj", "परसों", "parso", "tomorrow", "today", "tonight", "morning", "evening", "night", "pm", "am",
                "साढ़े", "साढे", "sadhe", "saadhe", "sade", "सवा", "sawa", "पौने", "paune", "बजकर", "in", "later", "after", "आधे घंटे", "aadhe ghante"});
        t = t.replaceAll("\\s+", " ").trim();
        return CommandParser.restore(raw, t);
    }
}

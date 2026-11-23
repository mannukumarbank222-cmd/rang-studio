package com.playrang.rangstudio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Last-resort answers when every AI engine fails: Wikipedia (Hindi, then English) summary, then DuckDuckGo instant answer. */
final class Knowledge {
    private Knowledge() {}

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "क्या", "है", "हैं", "था", "थी", "थे", "हो", "होता", "होती", "कौन", "कौनसा", "कौनसी", "किसने", "किसको", "किसे", "कब", "कहाँ", "कहां",
            "कैसे", "क्यों", "कितना", "कितनी", "कितने", "का", "की", "के", "को", "में", "से", "पर", "ने", "और", "एक", "यह", "ये", "वह", "वो",
            "बताओ", "बताइए", "बताएं", "बतायें", "बता", "मुझे", "हमें", "जरा", "ज़रा", "भाई", "मित्र", "बारे", "बारेमें", "जानकारी", "दो", "दीजिए",
            "kya", "hai", "hain", "tha", "thi", "kaun", "kisne", "kab", "kahan", "kaise", "kyon", "kyu", "ka", "ki", "ke", "ko", "me", "mein",
            "se", "batao", "bataiye", "mujhe", "baare", "bare", "what", "is", "are", "was", "were", "who", "whom", "when", "where", "why", "how",
            "which", "the", "a", "an", "of", "in", "on", "to", "for", "tell", "me", "about", "please", "do", "does", "did", "you", "know"));

    private static final String[] FACTUAL = {"क्या", "कौन", "किसने", "किसको", "कब", "कहाँ", "कहां", "कितना", "कितनी", "कितने", "कौनसा",
            "राजधानी", "मतलब", "अर्थ", "इतिहास", "kya", "kaun", "kisne", "kab", "kahan", "kitna", "matlab", "what", "who", "when", "where",
            "which", "capital", "meaning", "history", "define"};

    static boolean isFactual(String q) {
        String t = " " + q.toLowerCase(Locale.ROOT).replaceAll("[?!,.।]", " ") + " ";
        for (String f : FACTUAL) if (t.contains(" " + f + " ")) return true;
        return false;
    }

    static boolean devanagari(String s) {
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c >= 0x0900 && c <= 0x097F) return true; }
        return false;
    }

    static String keywords(String q) {
        StringBuilder b = new StringBuilder();
        for (String w : q.toLowerCase(Locale.ROOT).replaceAll("[?!,.।\"'’()]", " ").split("\\s+")) {
            if (w.isEmpty() || STOP.contains(w)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w);
        }
        return b.toString().trim();
    }

    /** @return a short spoken-friendly answer with its source, or null when nothing useful was found */
    static String answer(String q) {
        if (q == null || q.trim().isEmpty()) return null;
        String k = keywords(q);
        if (k.isEmpty()) k = q.trim();
        String r = null;
        if (devanagari(q)) r = wiki("hi", k);
        if (r == null) r = wiki("en", k);
        if (r == null) r = ddg(k);
        if (r == null) return null;
        return r + " (स्रोत: " + (srcLast == null ? "Wikipedia" : srcLast) + ")";
    }

    private static volatile String srcLast;

    static String wiki(String lang, String k) {
        try {
            String js = AiClient.get("https://" + lang + ".wikipedia.org/w/api.php?action=query&list=search&format=json&utf8=1&srlimit=3&srsearch="
                    + URLEncoder.encode(k, "UTF-8"));
            JSONArray res = new JSONObject(js).getJSONObject("query").getJSONArray("search");
            if (res.length() == 0) return null;
            String title = res.getJSONObject(0).getString("title");
            String[] kw = k.split(" ");
            // prefer a result whose title is exactly one of the keywords ("ताजमहल" over "ताजमहल के मूल एवं वास्तु")
            for (int i = 0; i < res.length(); i++) {
                String t = res.getJSONObject(i).getString("title");
                for (String w : kw) if (t.equalsIgnoreCase(w)) { title = t; i = res.length(); break; }
            }
            String sum = AiClient.get("https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/"
                    + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20"));
            JSONObject j = new JSONObject(sum);
            if ("disambiguation".equals(j.optString("type"))) return null;
            String ex = j.optString("extract", "").trim();
            if (ex.length() < 20) return null;
            srcLast = "Wikipedia";
            return firstSentences(ex, 2);
        } catch (Exception e) {
            return null;
        }
    }

    static String ddg(String k) {
        try {
            JSONObject j = new JSONObject(AiClient.get("https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=" + URLEncoder.encode(k, "UTF-8")));
            String a = j.optString("Answer", "").trim();
            if (a.isEmpty()) a = j.optString("AbstractText", "").trim();
            if (a.isEmpty()) a = j.optString("Definition", "").trim();
            if (a.length() < 3) return null;
            srcLast = "DuckDuckGo";
            return firstSentences(a, 2);
        } catch (Exception e) {
            return null;
        }
    }

    static String firstSentences(String s, int n) {
        StringBuilder b = new StringBuilder();
        int count = 0, start = 0;
        for (int i = 0; i < s.length() && count < n; i++) {
            char c = s.charAt(i);
            boolean end = c == '।' || ((c == '.' || c == '?' || c == '!') && (i + 1 >= s.length() || s.charAt(i + 1) == ' '));
            if (end) { b.append(s, start, i + 1); start = i + 1; count++; }
        }
        if (count == 0) b.append(s.length() > 300 ? s.substring(0, 300) + "…" : s);
        return b.toString().trim();
    }
}

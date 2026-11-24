package com.playrang.rangstudio;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects streamed AI text and hands back whole sentences as soon as they are complete, so speech can start
 * before the full reply arrives. [[ACTION:...]] tags are never spoken, even while only half of a tag has arrived.
 */
final class SentenceBuffer {
    private final StringBuilder all = new StringBuilder();
    private int spoken; // index in `all` up to which text was handed out
    static final int MAX_CHUNK = 220;

    /** Adds a streamed piece; returns the sentences that are now complete (tags removed, may be empty). */
    List<String> push(String delta) {
        if (delta != null) all.append(delta);
        List<String> out = new ArrayList<>();
        while (true) {
            int limit = safeLimit();
            int end = sentenceEnd(spoken, limit);
            if (end < 0) {
                if (limit - spoken > MAX_CHUNK) end = softBreak(spoken, limit);
                if (end < 0) break;
            }
            String s = speakable(all.substring(spoken, end));
            spoken = end;
            if (s.length() > 0) out.add(s);
        }
        return out;
    }

    /** Whatever is left once the stream ended. */
    String flush() {
        String s = speakable(all.substring(Math.min(spoken, all.length())));
        spoken = all.length();
        return s;
    }

    /** Full text so far (with tags), for running the action tag at the end. */
    String text() { return all.toString(); }

    /** Text to show on screen right now: tags and a half-arrived tag hidden. */
    String visible() {
        String t = all.toString();
        int open = t.lastIndexOf("[[");
        if (open >= 0 && t.indexOf("]]", open) < 0) t = t.substring(0, open);
        return stripTags(t).trim();
    }

    /** End of text that is safe to speak: stops before an unclosed "[[" (or a trailing single "["). */
    private int safeLimit() {
        int n = all.length();
        int open = all.lastIndexOf("[[");
        if (open >= spoken && all.indexOf("]]", open) < 0) return open;
        if (n > spoken && all.charAt(n - 1) == '[') return n - 1;
        return n;
    }

    private int sentenceEnd(int from, int limit) {
        for (int i = from; i < limit; i++) {
            char c = all.charAt(i);
            boolean hard = c == '।' || c == '॥' || c == '?' || c == '!' || c == '\n' || c == '؟';
            boolean dot = c == '.';
            if (!hard && !dot) continue;
            // a "." needs a following space (so 3.5 and URLs are not split); others end at once
            if (dot) {
                if (i + 1 >= limit) return -1;
                char nx = all.charAt(i + 1);
                if (!Character.isWhitespace(nx)) continue;
                if (i > from && Character.isDigit(all.charAt(i - 1)) && i - from < 4) continue; // "1. " list numbers
            }
            int end = i + 1;
            while (end < limit && "।॥?!.\"'”’)".indexOf(all.charAt(end)) >= 0) end++;
            if (letters(all.substring(from, end)) < 2) { from = end; i = end - 1; continue; }
            return end;
        }
        return -1;
    }

    private int softBreak(int from, int limit) {
        limit = Math.min(limit, from + MAX_CHUNK);
        for (int i = limit - 1; i > from + 40; i--) if (all.charAt(i) == ',' || all.charAt(i) == '،') return i + 1;
        for (int i = limit - 1; i > from + 40; i--) if (all.charAt(i) == ' ') return i + 1;
        return -1;
    }

    private static int letters(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetterOrDigit(s.charAt(i))) n++;
        return n;
    }

    static String stripTags(String s) {
        return s.replaceAll("\\[\\[[^\\]]*\\]\\]", "").replaceAll("(?s)```(json)?\\s*```", "");
    }

    static String speakable(String s) {
        return stripTags(s).replace("**", "").replace("*", "").replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\s+", " ").trim();
    }
}

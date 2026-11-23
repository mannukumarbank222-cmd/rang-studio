package com.playrang.rangstudio;

import java.util.ArrayList;
import java.util.List;

/** Recent conversation (last ~10 turns), shared by the chat screen and the wake-word voice screen. */
final class Convo {
    private Convo() {}
    static final int MAX_MESSAGES = 20;
    private static final List<String[]> H = new ArrayList<>();

    static synchronized void add(String role, String text) {
        H.add(new String[]{role, text});
        while (H.size() > MAX_MESSAGES) H.remove(0);
    }
    static synchronized void removeLast() { if (!H.isEmpty()) H.remove(H.size() - 1); }
    static synchronized List<String[]> snapshot() { return new ArrayList<>(H); }
    static synchronized void clear() { H.clear(); }

    private static final java.util.Set<String> STOP = new java.util.HashSet<>(java.util.Arrays.asList(
            "बस", "bas", "बस करो", "bas karo", "बंद करो", "band karo", "बंद कर दो", "band kar do", "बात बंद करो", "bye", "bye bye", "बाय", "बाय बाय",
            "stop", "स्टॉप", "अलविदा", "alvida", "goodbye", "good bye", "टाटा", "tata", "बस हो गया", "bas ho gaya", "चुप हो जाओ", "रुक जाओ",
            "बातचीत बंद करो", "बातचीत बंद", "बस धन्यवाद", "बस शुक्रिया", "thank you bye", "ok bye", "ओके बाय"));

    /** "बस", "बंद करो", "bye" … (the whole utterance) ends conversation mode. "टॉर्च बंद करो" does not. */
    static boolean isStop(String raw) {
        String t = CommandParser.norm(raw).trim();
        for (String pre : new String[]{"मित्र ", "mitra ", "अब ", "ab ", "ठीक है ", "theek hai ", "ok ", "ओके ", "चलो ", "chalo ", "अच्छा ", "accha "})
            if (t.startsWith(pre)) t = t.substring(pre.length()).trim();
        return STOP.contains(t) || STOP.contains(raw.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[.!,।?]", "").trim());
    }
}

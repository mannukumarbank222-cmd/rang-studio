package com.playrang.rangstudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LangDetectTest {
    private static void is(String text, String id, boolean roman) {
        LangDetect.Result r = LangDetect.detect(text);
        assertNotNull(text, r);
        assertEquals(text, id, r.id);
        assertEquals(text + " roman", roman, r.roman);
    }

    @Test public void scripts() {
        is("सोनू को कॉल करो", "hi", false);
        is("আমাকে একটা গান শোনাও", "bn", false);
        is("எனக்கு ஒரு பாட்டு போடு", "ta", false);
        is("నాకు ఒక పాట వినిపించు", "te", false);
        is("ਮੈਨੂੰ ਇੱਕ ਗੀਤ ਸੁਣਾਓ", "pa", false);
        is("મને એક ગીત સંભળાવો", "gu", false);
        is("ನನಗೆ ಒಂದು ಹಾಡು ಹಾಕಿ", "kn", false);
        is("എനിക്ക് ഒരു പാട്ട് വെക്കൂ", "ml", false);
        is("ମୋତେ ଗୋଟିଏ ଗୀତ ଶୁଣାଅ", "or", false);
        is("مجھے ایک گانا سناؤ", "ur", false);
    }

    @Test public void devanagariLanguages() {
        is("मला एक गाणं ऐकव, मला ते आवडते आहे", "mr", false);
        is("तुम्ही काय करत आहात", "mr", false);
        is("तपाईंलाई कस्तो छ? मलाई भन्नुहोस्", "ne", false);
        // Bhojpuri / Maithili written in Devanagari → Hindi recognizer + voice; the AI keeps the dialect
        is("का हो, रउआ कइसे बानी", "hi", false);
        is("अहाँ कतय छी", "hi", false);
        is("मुझे खाना खाना है", "hi", false);
        is("25 गुणा 4 कितना होता है", "hi", false);
    }

    @Test public void latin() {
        is("what is the capital of France", "en", false);
        is("please set an alarm for 6 am", "en", false);
        is("mujhe ek gana sunao", "hi", true);
        is("kya haal hai bhai", "hi", true);
        is("Sonu ko call karo", "hi", true);
        is("ka ho raua kaise bani", "bho", true);
        is("tusi kiddan ho paaji", "pa", true);
    }

    @Test public void emptyAndDigits() {
        assertNull(LangDetect.detect(""));
        assertNull(LangDetect.detect("12345"));
        assertNull(LangDetect.detect(null));
    }

    @Test public void instructionAndLocale() {
        assertTrue(LangDetect.instruction(LangDetect.detect("எனக்கு ஒரு பாட்டு")).contains("Tamil"));
        assertTrue(LangDetect.instruction(LangDetect.detect("kya haal hai")).contains("Hinglish"));
        assertTrue(LangDetect.instruction(LangDetect.detect("what is AI")).contains("English"));
        assertEquals("ta-IN", LangDetect.locale("ta"));
        assertEquals("hi-IN", LangDetect.locale("bho"));
        assertEquals("hi-IN", LangDetect.locale("hi"));
    }

    @Test public void listenLocaleSwitchesOnlyWhenSure() {
        // Tamil typed/spoken → listen in Tamil next time
        assertEquals("ta-IN", LangDetect.nextListenLocale("hi-IN", LangDetect.detect("எனக்கு ஒரு பாட்டு போடு"), 3));
        // one English word must not flip a Hindi speaker to English
        assertEquals("hi-IN", LangDetect.nextListenLocale("hi-IN", LangDetect.detect("ok"), 1));
        // Roman Hindi always goes back to the Hindi recognizer
        assertEquals("hi-IN", LangDetect.nextListenLocale("en-IN", LangDetect.detect("mujhe gana sunao"), 3));
        assertEquals("en-IN", LangDetect.nextListenLocale("hi-IN", LangDetect.detect("what is the weather today"), 5));
    }

    @Test public void langAutoVsOverride() {
        Lang auto = Lang.get("auto"), ta = Lang.get("ta");
        assertTrue(Lang.aiLine(auto, "আমি ভালো আছি").contains("Bengali"));
        assertTrue(Lang.aiLine(ta, "kya haal hai").contains("Tamil")); // a language chosen in Settings wins
        assertEquals("hi-IN", Lang.srLocale(auto, null));
        assertEquals("bn-IN", Lang.srLocale(auto, "bn-IN"));
        assertEquals("ta-IN", Lang.srLocale(ta, "bn-IN"));
        assertEquals("en-IN", Lang.ttsForText("The capital of France is Paris.", auto));
        assertEquals("hi-IN", Lang.ttsForText("Haan bhai, main theek hoon.", auto));
        assertFalse(Lang.ttsForText("नमस्ते", auto).startsWith("en"));
    }
}

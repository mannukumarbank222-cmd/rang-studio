package com.playrang.rangstudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class AppMatcherTest {
    private static final List<AppMatcher.App> APPS = Arrays.asList(
            new AppMatcher.App("WhatsApp", "com.whatsapp"),
            new AppMatcher.App("WhatsApp Business", "com.whatsapp.w4b"),
            new AppMatcher.App("Instagram", "com.instagram.android"),
            new AppMatcher.App("PhonePe", "com.phonepe.app"),
            new AppMatcher.App("Google Pay", "com.google.android.apps.nbu.paisa.user"),
            new AppMatcher.App("Flipkart", "com.flipkart.android"),
            new AppMatcher.App("Meesho", "com.meesho.supply"),
            new AppMatcher.App("Truecaller", "com.truecaller"),
            new AppMatcher.App("Calculator", "com.miui.calculator"),
            new AppMatcher.App("Ludo King", "com.ludo.king"),
            new AppMatcher.App("Settings", "com.android.settings"),
            new AppMatcher.App("Snapchat", "com.snapchat.android"),
            new AppMatcher.App("Swiggy", "in.swiggy.android"),
            new AppMatcher.App("IRCTC Rail Connect", "cris.org.in.prs.ima"));

    private static String best(String q) {
        int i = AppMatcher.best(q, APPS);
        return i < 0 ? null : APPS.get(i).label;
    }

    @Test public void exactAndCase() {
        assertEquals("Instagram", best("instagram"));
        assertEquals("WhatsApp", best("whatsapp"));
        assertEquals("Truecaller", best("TRUECALLER"));
    }

    @Test public void hindiNames() {
        assertEquals("Flipkart", best("फ्लिपकार्ट"));
        assertEquals("Meesho", best("मीशो"));
        assertEquals("Truecaller", best("ट्रूकॉलर"));
        assertEquals("Swiggy", best("स्विगी"));
        assertEquals("Ludo King", best("लूडो"));
    }

    @Test public void misspellingsAndSpacing() {
        assertEquals("WhatsApp", best("watsap"));
        assertEquals("PhonePe", best("phone pe"));
        assertEquals("PhonePe", best("phonepay"));
        assertEquals("Instagram", best("instagran"));
        assertEquals("Flipkart", best("flipcart"));
        assertEquals("Snapchat", best("snap chat"));
    }

    @Test public void partialAndWords() {
        assertEquals("Ludo King", best("ludo"));
        assertEquals("IRCTC Rail Connect", best("irctc"));
        assertEquals("Google Pay", best("google pay"));
        assertEquals("WhatsApp Business", best("whatsapp business"));
    }

    @Test public void noMatch() {
        assertEquals(null, best("zomato"));
        assertEquals(null, best("xy"));
        assertTrue(AppMatcher.bestScore("netflix", APPS) < AppMatcher.MIN_SCORE);
    }
}

package com.playrang.rangstudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class WakeWordTest {
    private static WakeWord.W w(String s, double c, double a, double b) { return new WakeWord.W(s, c, a, b); }
    private static List<WakeWord.W> l(WakeWord.W... x) { return Arrays.asList(x); }
    private static final double T = WakeWord.threshold(1);

    @Test public void splitAccepts() {
        assertEquals("", WakeWord.split("मित्र"));
        assertEquals("", WakeWord.split("हे मित्र"));
        assertEquals("सोनू को कॉल करो", WakeWord.split("मित्र, सोनू को कॉल करो"));
        assertEquals("what's the time", WakeWord.split("Hey Mitra, what's the time"));
        assertEquals("टॉर्च जलाओ", WakeWord.split("मित्र जी टॉर्च जलाओ"));
    }

    @Test public void splitRejects() {
        assertNull(WakeWord.split("मित्रों आज मौसम अच्छा है"));
        assertNull(WakeWord.split("पितृपक्ष कब है"));
        assertNull(WakeWord.split("यह क्षेत्र बड़ा है"));
        assertNull(WakeWord.split("दस मीटर दूर"));
        assertNull(WakeWord.split("मेरा मित्र आया"));
        assertNull(WakeWord.split(""));
    }

    @Test public void evaluateAcceptsWithCommand() {
        assertEquals("सोनू को कॉल करो", WakeWord.evaluate(l(w("मित्र", 0.9, 0.1, 0.5)),
                l(w("मित्र", 0.9, 0.1, 0.5), w("सोनू", 0.9, 0.6, 0.9), w("को", 0.9, 0.9, 1.0), w("कॉल", 0.9, 1.0, 1.3), w("करो", 0.9, 1.3, 1.6)), T));
        assertEquals("", WakeWord.evaluate(l(w("हे", 0.9, 0.0, 0.2), w("मित्र", 0.9, 0.2, 0.6)), l(w("हे", 0.9, 0.0, 0.2), w("मित्र", 0.9, 0.2, 0.6)), T));
    }

    @Test public void evaluateRejects() {
        // swallowed by a longer word
        assertNull(WakeWord.evaluate(l(w("मित्र", 0.9, 0.3, 0.7)), l(w("पितृपक्ष", 0.9, 0.0, 1.0)), T));
        // look-alike word
        assertNull(WakeWord.evaluate(l(w("मित्र", 0.9, 0.0, 0.5)), l(w("मीटर", 0.9, 0.0, 0.5)), T));
        assertNull(WakeWord.evaluate(l(w("मित्र", 0.9, 0.0, 0.5)), l(w("क्षेत्र", 0.9, 0.0, 0.5)), T));
        // not at phrase start
        assertNull(WakeWord.evaluate(l(w("मित्र", 0.9, 0.35, 0.8)), l(w("आज", 0.9, 0.0, 0.3), w("मित्र", 0.9, 0.35, 0.8)), T));
        // low confidence
        assertNull(WakeWord.evaluate(l(w("मित्र", 0.4, 0.0, 0.5)), l(w("मित्र", 0.9, 0.0, 0.5)), T));
        // decoy in grammar pass
        assertFalse(WakeWord.hasCandidate(l(w("मित्रों", 0.99, 0, 0.5)), T));
        assertTrue(WakeWord.hasCandidate(l(w("मित्र", 0.99, 0, 0.5)), T));
    }
}

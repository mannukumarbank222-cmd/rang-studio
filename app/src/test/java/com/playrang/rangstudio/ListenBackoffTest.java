package com.playrang.rangstudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ListenBackoffTest {
    @Test public void waitsOneThenThreeThenStops() {
        ListenBackoff b = new ListenBackoff();
        assertFalse(b.stopped());
        assertEquals(1000L, b.onEmpty());
        assertEquals(3000L, b.onEmpty());
        assertEquals(ListenBackoff.STOP, b.onEmpty());
        assertTrue(b.stopped());
        assertEquals(ListenBackoff.STOP, b.onEmpty()); // stays stopped
    }

    @Test public void resetStartsAgain() {
        ListenBackoff b = new ListenBackoff();
        b.onEmpty();
        b.onEmpty();
        b.reset();
        assertEquals(0, b.misses());
        assertFalse(b.stopped());
        assertEquals(1000L, b.onEmpty());
    }

    @Test public void stopsAfterThreeEmptyTries() {
        ListenBackoff b = new ListenBackoff();
        int restarts = 0;
        while (b.onEmpty() != ListenBackoff.STOP) restarts++;
        assertEquals(2, restarts);
        assertEquals(3, b.misses());
    }
}

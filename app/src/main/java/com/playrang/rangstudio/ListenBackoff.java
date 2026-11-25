package com.playrang.rangstudio;

/**
 * Back-off for continuous listening loops. When the recognizer hears nothing (no match / speech timeout) we do not
 * restart it right away: wait 1 s, then 3 s, and after the third empty try stop and ask the user to tap.
 * Pure Java so it can be unit tested.
 */
final class ListenBackoff {
    static final long STOP = -1;
    private static final long[] DELAYS = {1000L, 3000L};

    private int misses;

    /** Records one empty try. @return how long to wait before listening again, or {@link #STOP} to stop */
    synchronized long onEmpty() {
        misses++;
        if (misses > DELAYS.length) return STOP;
        return DELAYS[misses - 1];
    }

    /** Something was heard, or the user tapped the mic: start counting again. */
    synchronized void reset() { misses = 0; }

    synchronized int misses() { return misses; }

    synchronized boolean stopped() { return misses > DELAYS.length; }
}

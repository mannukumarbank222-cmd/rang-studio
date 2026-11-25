package com.playrang.rangstudio;

/**
 * False-trigger protection for the wake word. Pure Java (unit-tested; times are passed in).
 * <ul>
 *   <li>Cool-down: no second wake within {@link #COOLDOWN_MS} of the last one.</li>
 *   <li>Adaptive threshold: when two wakes in a row ended with the user saying nothing (a likely false trigger from
 *   TV / conversation), the threshold goes up by {@link #BUMP} for {@link #BUMP_MS}; a real use resets it.</li>
 * </ul>
 */
final class WakeGuard {
    static final long COOLDOWN_MS = 4000, BUMP_MS = 30 * 60 * 1000L;
    static final double BUMP = 0.08;

    private long lastWake = -COOLDOWN_MS * 10, bumpUntil;
    private int falseInRow;

    /** @return true when a wake at time now may fire (and records it). */
    synchronized boolean allow(long now) {
        if (now - lastWake < COOLDOWN_MS) return false;
        lastWake = now;
        return true;
    }

    /** Threshold to use now for the given base threshold. */
    synchronized double threshold(double base, long now) {
        return now < bumpUntil ? Math.min(0.97, base + BUMP) : base;
    }

    /** Called when the voice screen opened by a wake closes: did the user actually say something? */
    synchronized void report(boolean userSpoke, long now) {
        if (userSpoke) { falseInRow = 0; bumpUntil = 0; return; }
        falseInRow++;
        if (falseInRow >= 2) bumpUntil = now + BUMP_MS;
    }

    synchronized boolean bumped(long now) { return now < bumpUntil; }

    /** Process-wide instance used by WakeService and the voice screens. */
    static final WakeGuard GLOBAL = new WakeGuard();
}

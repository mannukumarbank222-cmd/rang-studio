package com.playrang.rangstudio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.junit.Test;

public class CalcTimeTest {
    @Test public void calc() {
        assertEquals(100.0, Calc.eval("25 गुणा 4 कितना होता है"), 1e-9);
        assertEquals(30.0, Calc.eval("200 का 15 प्रतिशत कितना है"), 1e-9);
        assertEquals(30.0, Calc.eval("15% of 200"), 1e-9);
        assertEquals(12.5, Calc.eval("100 divided by 8"), 1e-9);
        assertEquals(16.0, Calc.eval("12 plus 7 minus 3"), 1e-9);
        assertEquals(14.0, Calc.eval("2 + 3 x 4"), 1e-9);
        assertEquals("25 × 4 = 100", Calc.answer("25 गुणा 4"));
        assertNull(Calc.eval("सोनू को कॉल करो"));
        assertNull(Calc.eval("9876543210 pe call karo"));
        assertNull(Calc.eval("10 minute ka timer"));
        assertNull(Calc.eval("5 / 0"));
        assertNull(Calc.eval("25"));
    }

    @Test public void clock() {
        assertArrayEquals(new int[]{6, 30}, TimeParse.clock("सुबह साढ़े 6 बजे"));
        assertArrayEquals(new int[]{19, 0}, TimeParse.clock("shaam 7 baje"));
        assertArrayEquals(new int[]{22, 0}, TimeParse.clock("raat 10 baje"));
        assertArrayEquals(new int[]{6, 30}, TimeParse.clock("06:30"));
        assertArrayEquals(new int[]{17, 0}, TimeParse.clock("5 pm"));
        assertArrayEquals(new int[]{7, 0}, TimeParse.clock("सात बजे"));
        assertArrayEquals(new int[]{8, 45}, TimeParse.clock("पौने 9 बजे"));
        assertNull(TimeParse.clock("10 minute baad"));
    }

    @Test public void reminders() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.NOVEMBER, 25, 18, 0, 0);
        now.set(Calendar.MILLISECOND, 0);
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(TimeParse.when("कल सुबह 9 बजे याद दिलाना कि दवा लेनी है", now));
        assertEquals(26, k.get(Calendar.DAY_OF_MONTH));
        assertEquals(9, k.get(Calendar.HOUR_OF_DAY));
        assertEquals("दवा लेनी है", TimeParse.reminderTitle("कल सुबह 9 बजे याद दिलाना कि दवा लेनी है"));
        assertEquals(now.getTimeInMillis() + 10 * 60000L, TimeParse.when("10 minute baad yaad dilana chai banani hai", now));
        assertEquals("chai banani hai", TimeParse.reminderTitle("10 minute baad yaad dilana chai banani hai"));
        assertEquals(now.getTimeInMillis() + 30 * 60000L, TimeParse.when("आधे घंटे बाद याद दिलाना", now));
        k.setTimeInMillis(TimeParse.when("remind me at 5 pm to call papa", now)); // 5 pm already passed → tomorrow
        assertEquals(26, k.get(Calendar.DAY_OF_MONTH));
        assertEquals(17, k.get(Calendar.HOUR_OF_DAY));
        assertEquals("call papa", TimeParse.reminderTitle("remind me at 5 pm to call papa"));
        assertEquals(-1, TimeParse.when("याद दिलाना कि दूध लाना है", now));
    }

    @Test public void wakeGuard() {
        WakeGuard g = new WakeGuard();
        assertTrue(g.allow(100000));
        assertFalse(g.allow(102000));            // cool-down
        assertTrue(g.allow(100000 + WakeGuard.COOLDOWN_MS + 1));
        assertEquals(0.75, g.threshold(0.75, 0), 1e-9);
        g.report(false, 200000);
        assertEquals(0.75, g.threshold(0.75, 200001), 1e-9); // one silent wake: no change yet
        g.report(false, 210000);
        assertEquals(0.83, g.threshold(0.75, 210001), 1e-9); // two in a row: stricter for 30 min
        assertEquals(0.75, g.threshold(0.75, 210000 + WakeGuard.BUMP_MS + 1), 1e-9);
        g.report(true, 220000);
        assertFalse(g.bumped(220001));
    }

    @Test public void wakeRejectsDifferentWordInSamePlace() {
        double t = WakeWord.threshold(1);
        // the full decoder heard "मीत" (not a wake word) exactly where the grammar matched → reject
        assertNull(WakeWord.evaluate(java.util.Arrays.asList(new WakeWord.W("मित्र", 0.8, 0.0, 0.5)),
                java.util.Arrays.asList(new WakeWord.W("मीत", 0.9, 0.0, 0.5)), t));
        // very confident grammar match still passes
        assertEquals("", WakeWord.evaluate(java.util.Arrays.asList(new WakeWord.W("मित्र", 0.97, 0.0, 0.5)),
                java.util.Arrays.asList(new WakeWord.W("मीत", 0.9, 0.0, 0.5)), t));
    }
}

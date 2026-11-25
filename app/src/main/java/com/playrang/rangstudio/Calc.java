package com.playrang.rangstudio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Instant on-device arithmetic for spoken sums ("25 गुणा 4 कितना", "100 divided by 8", "200 का 15 प्रतिशत",
 * "12 plus 7 minus 3"). Anything more complex goes to the AI. Pure Java (unit-tested).
 */
final class Calc {
    private Calc() {}

    private static final String[][] OPS = {
            {"गुणा", "*"}, {"गुना", "*"}, {"guna", "*"}, {"gunaa", "*"}, {"into", "*"}, {"times", "*"}, {"multiplied by", "*"}, {"multiply", "*"},
            {"x", "*"}, {"×", "*"}, {"भाग", "/"}, {"bhag", "/"}, {"divided by", "/"}, {"divide by", "/"}, {"divide", "/"}, {"by", "/"}, {"÷", "/"},
            {"प्लस", "+"}, {"plus", "+"}, {"जोड़", "+"}, {"जोड", "+"}, {"jod", "+"}, {"aur", "+"}, {"और", "+"}, {"add", "+"},
            {"माइनस", "-"}, {"minus", "-"}, {"घटा", "-"}, {"ghata", "-"}, {"कम", "-"}, {"less", "-"},
    };
    private static final String[] TRAIL = {"कितना होता है", "कितने होते हैं", "कितना होगा", "कितना है", "कितना", "कितने", "kitna hota hai",
            "kitna hoga", "kitna hai", "kitna", "kitne", "बराबर", "barabar", "equals", "equal to", "is equal to", "what is", "whats", "what's",
            "calculate", "हिसाब", "hisab", "बताओ", "batao", "बताइए", "=", "?", "होता है", "hota hai", "है", "hai", "please", "का जवाब", "ka jawab"};

    /** @return the answer sentence in Hindi, or null when the text is not a plain sum. */
    static String answer(String raw) {
        Double v = eval(raw);
        if (v == null) return null;
        return expr(raw).replace("*", " × ").replace("/", " ÷ ").replace("+", " + ").replace("-", " − ") + " = " + fmt(v);
    }

    /** Value of the spoken sum, or null when it is not one. */
    static Double eval(String raw) {
        String e = expr(raw);
        if (e == null) return null;
        try {
            P p = new P(e);
            double v = p.sum();
            p.ws();
            if (p.i != p.s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Normalised expression ("25*4"), or null when the text has words other than numbers, operators and question fillers. */
    static String expr(String raw) {
        if (raw == null) return null;
        String t = " " + CommandParser.fold(raw).replace(",", "").replaceAll("[?=।!]", " ").replaceAll("\\s+", " ").trim() + " ";
        for (int pass = 0; pass < 2; pass++) for (String tr : TRAIL) t = t.replace(" " + CommandParser.fold(tr) + " ", " ");
        // percent: "200 का 15 प्रतिशत" / "15% of 200" / "15 percent of 200"
        t = t.replaceAll("(\\d+(?:\\.\\d+)?)\\s*(?:का|के|ka|ke)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|प्रतिशत|परसेंट|percent|pratishat)", " $1*$2/100 ");
        t = t.replaceAll("(\\d+(?:\\.\\d+)?)\\s*(?:%|प्रतिशत|परसेंट|percent|pratishat)\\s*(?:of|का|ka)?\\s*(\\d+(?:\\.\\d+)?)", " $1*$2/100 ");
        for (String[] o : OPS) t = t.replace(" " + CommandParser.fold(o[0]) + " ", " " + o[1] + " ");
        t = t.replace("×", "*").replace("÷", "/").replaceAll("(?<=\\d)\\s*x\\s*(?=\\d)", "*");
        t = t.replaceAll("\\s+", "");
        if (!t.matches("[0-9.+\\-*/()]+")) return null;
        int nums = t.split("[+\\-*/()]+").length;
        boolean op = t.matches(".*\\d[+\\-*/(].*\\d.*");
        if (!op || nums < 2 || t.length() > 60) return null;
        if (t.matches(".*\\d{6,}.*")) return null; // "98765-43210" is a phone number, not a sum
        return t;
    }

    static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e15) return String.valueOf((long) Math.rint(v));
        return new BigDecimal(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Tiny recursive-descent parser: sum := prod (('+'|'-') prod)*, prod := unary (('*'|'/') unary)*. */
    private static final class P {
        final String s; int i;
        P(String s) { this.s = s; }
        void ws() { while (i < s.length() && s.charAt(i) == ' ') i++; }
        double sum() {
            double v = prod();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '+') { i++; v += prod(); } else if (c == '-') { i++; v -= prod(); } else break;
            }
            return v;
        }
        double prod() {
            double v = unary();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '*') { i++; v *= unary(); }
                else if (c == '/') { i++; double d = unary(); if (d == 0) throw new ArithmeticException(); v /= d; }
                else break;
            }
            return v;
        }
        double unary() {
            if (i < s.length() && s.charAt(i) == '-') { i++; return -unary(); }
            if (i < s.length() && s.charAt(i) == '(') { i++; double v = sum(); if (i >= s.length() || s.charAt(i) != ')') throw new IllegalStateException(); i++; return v; }
            int st = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (st == i) throw new IllegalStateException();
            return Double.parseDouble(s.substring(st, i));
        }
    }
}

package com.playrang.rangstudio;

/** Plain-Java Base64 (standard alphabet, with padding). Works on minSdk 24 and in local unit tests. */
final class B64 {
    private B64() {}

    private static final char[] ENC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] DEC = new int[128];
    static {
        java.util.Arrays.fill(DEC, -1);
        for (int i = 0; i < ENC.length; i++) DEC[ENC[i]] = i;
        DEC['-'] = 62; // tolerate URL-safe input
        DEC['_'] = 63;
    }

    static String encode(byte[] b) { return encode(b, 0, b.length); }

    static String encode(byte[] b, int off, int len) {
        char[] out = new char[(len + 2) / 3 * 4];
        int o = 0, end = off + len, i = off;
        while (i + 2 < end) {
            int v = ((b[i] & 0xff) << 16) | ((b[i + 1] & 0xff) << 8) | (b[i + 2] & 0xff);
            out[o++] = ENC[(v >>> 18) & 63]; out[o++] = ENC[(v >>> 12) & 63]; out[o++] = ENC[(v >>> 6) & 63]; out[o++] = ENC[v & 63];
            i += 3;
        }
        int rest = end - i;
        if (rest == 1) {
            int v = (b[i] & 0xff) << 16;
            out[o++] = ENC[(v >>> 18) & 63]; out[o++] = ENC[(v >>> 12) & 63]; out[o++] = '='; out[o++] = '=';
        } else if (rest == 2) {
            int v = ((b[i] & 0xff) << 16) | ((b[i + 1] & 0xff) << 8);
            out[o++] = ENC[(v >>> 18) & 63]; out[o++] = ENC[(v >>> 12) & 63]; out[o++] = ENC[(v >>> 6) & 63]; out[o++] = '=';
        }
        return new String(out, 0, o);
    }

    /** Decodes, skipping whitespace and padding; invalid characters are ignored. */
    static byte[] decode(String s) {
        int n = s.length();
        byte[] out = new byte[n * 3 / 4 + 3];
        int o = 0, acc = 0, bits = 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '=') break;
            int v = c < 128 ? DEC[c] : -1;
            if (v < 0) continue;
            acc = ((acc << 6) | v) & 0x3FFFF;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[o++] = (byte) ((acc >>> bits) & 0xff);
            }
        }
        return o == out.length ? out : java.util.Arrays.copyOf(out, o);
    }
}

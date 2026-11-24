package com.playrang.rangstudio;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A tiny RFC 6455 WebSocket client (wss:// and ws://), plain Java so the APK stays small (no OkHttp/Kotlin).
 * One reader thread delivers every text or binary message to the listener as a UTF-8 string.
 */
final class WsClient {
    static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    static final int OP_CONT = 0, OP_TEXT = 1, OP_BIN = 2, OP_CLOSE = 8, OP_PING = 9, OP_PONG = 10;
    private static final int MAX_MESSAGE = 32 * 1024 * 1024;
    private static final boolean ANDROID = String.valueOf(System.getProperty("java.vm.vendor")).toLowerCase(java.util.Locale.ROOT).contains("android")
            || String.valueOf(System.getProperty("java.vendor")).toLowerCase(java.util.Locale.ROOT).contains("android");

    interface Listener {
        void onMessage(String text);
        /** Called once when the connection ends for any reason (code -1 = network error). */
        void onClosed(int code, String reason);
    }

    private final SecureRandom rnd = new SecureRandom();
    private final Object writeLock = new Object();
    private Socket sock;
    private OutputStream out;
    private volatile boolean open, closedReported;
    private Listener listener;

    boolean isOpen() { return open; }

    /** Blocking: opens the socket and does the HTTP upgrade, then starts the reader thread. Throws on failure. */
    void connect(String url, Listener l) throws IOException {
        listener = l;
        URI u = URI.create(url);
        boolean tls = "wss".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme());
        String host = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : (tls ? 443 : 80);
        String path = (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath())
                + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        Socket s;
        if (tls) {
            Socket plain = new Socket();
            plain.connect(new InetSocketAddress(host, port), 12000);
            // layer TLS over the connected socket, with the host name so SNI is sent
            SSLSocket named = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, port, true);
            named.setSoTimeout(15000);
            try {
                javax.net.ssl.SSLParameters sp = named.getSSLParameters();
                sp.setEndpointIdentificationAlgorithm("HTTPS");
                named.setSSLParameters(sp);
            } catch (Throwable ignored) { }
            named.startHandshake();
            if (ANDROID && !HttpsURLConnection.getDefaultHostnameVerifier().verify(host, named.getSession())) {
                named.close();
                throw new IOException("TLS host name mismatch");
            }
            s = named;
        } else {
            s = new Socket();
            s.connect(new InetSocketAddress(host, port), 12000);
            s.setSoTimeout(15000);
        }
        s.setTcpNoDelay(true);
        sock = s;
        out = s.getOutputStream();
        InputStream in = s.getInputStream();

        byte[] k = new byte[16];
        rnd.nextBytes(k);
        String key = B64.encode(k);
        String req = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + (port == 443 || port == 80 ? "" : ":" + port) + "\r\n"
                + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "User-Agent: Mitra-Android/3.0\r\n\r\n";
        out.write(req.getBytes(UTF8));
        out.flush();

        String status = readLine(in);
        String accept = null;
        int contentLength = 0;
        String line;
        while ((line = readLine(in)).length() > 0) {
            int c = line.indexOf(':');
            if (c <= 0) continue;
            String name = line.substring(0, c).trim().toLowerCase(java.util.Locale.ROOT);
            String val = line.substring(c + 1).trim();
            if (name.equals("sec-websocket-accept")) accept = val;
            if (name.equals("content-length")) try { contentLength = Integer.parseInt(val); } catch (Exception ignored) { }
        }
        if (!status.contains(" 101")) {
            String body = "";
            if (contentLength > 0 && contentLength < 65536) {
                byte[] b = new byte[contentLength];
                int r = 0;
                try { while (r < contentLength) { int n = in.read(b, r, contentLength - r); if (n < 0) break; r += n; } } catch (IOException ignored) { }
                body = new String(b, 0, r, UTF8);
            }
            s.close();
            throw new HandshakeException(status, body);
        }
        if (accept == null || !accept.equals(acceptFor(key))) { s.close(); throw new IOException("bad Sec-WebSocket-Accept"); }
        s.setSoTimeout(0);
        open = true;
        Thread t = new Thread(() -> readLoop(in), "mitra-ws-reader");
        t.setDaemon(true);
        t.start();
    }

    static final class HandshakeException extends IOException {
        final String status, body;
        HandshakeException(String status, String body) { super("WebSocket upgrade failed: " + status + " " + body); this.status = status; this.body = body; }
    }

    static String acceptFor(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return B64.encode(md.digest((key + GUID).getBytes(UTF8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 16384) throw new IOException("header too long");
        }
        if (c < 0 && b.size() == 0) throw new EOFException("connection closed during handshake");
        return new String(b.toByteArray(), UTF8);
    }

    boolean sendText(String s) { return send(OP_TEXT, s.getBytes(UTF8)); }

    private boolean send(int op, byte[] payload) {
        if (!open && op != OP_CLOSE) return false;
        byte[] mask = new byte[4];
        rnd.nextBytes(mask);
        byte[] frame = encodeFrame(op, payload, mask);
        synchronized (writeLock) {
            try {
                out.write(frame);
                out.flush();
                return true;
            } catch (IOException e) {
                fail(-1, e.getMessage() == null ? "write failed" : e.getMessage());
                return false;
            }
        }
    }

    /** Sends a normal close and shuts the socket shortly after. */
    void close() {
        if (!open) { closeSocket(); return; }
        byte[] p = closePayload(1000, "bye");
        send(OP_CLOSE, p);
        open = false;
        closeSocket();
        report(1000, "closed by client");
    }

    private void closeSocket() { try { if (sock != null) sock.close(); } catch (IOException ignored) { } }

    private void fail(int code, String reason) {
        open = false;
        closeSocket();
        report(code, reason);
    }

    private synchronized void report(int code, String reason) {
        if (closedReported) return;
        closedReported = true;
        if (listener != null) try { listener.onClosed(code, reason == null ? "" : reason); } catch (Throwable ignored) { }
    }

    private void readLoop(InputStream in) {
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        try {
            while (open) {
                Frame f = readFrame(in, MAX_MESSAGE);
                switch (f.opcode) {
                    case OP_PING: send(OP_PONG, f.payload); break;
                    case OP_PONG: break;
                    case OP_CLOSE: {
                        int code = 1005;
                        String reason = "";
                        if (f.payload.length >= 2) {
                            code = ((f.payload[0] & 0xff) << 8) | (f.payload[1] & 0xff);
                            reason = new String(f.payload, 2, f.payload.length - 2, UTF8);
                        }
                        if (open) { send(OP_CLOSE, closePayload(code == 1005 ? 1000 : code, "")); }
                        open = false;
                        closeSocket();
                        report(code, reason);
                        return;
                    }
                    case OP_TEXT: case OP_BIN: case OP_CONT: {
                        msg.write(f.payload, 0, f.payload.length);
                        if (msg.size() > MAX_MESSAGE) throw new IOException("message too large");
                        if (f.fin) {
                            String text = new String(msg.toByteArray(), UTF8);
                            msg.reset();
                            if (listener != null) try { listener.onMessage(text); } catch (Throwable ignored) { }
                        }
                        break;
                    }
                    default: break;
                }
            }
        } catch (IOException e) {
            fail(-1, e.getMessage() == null ? "network error" : e.getMessage());
        }
    }

    // ------------------------------------------------------------------ frame codec (pure, unit-tested)
    static final class Frame {
        final boolean fin; final int opcode; final byte[] payload;
        Frame(boolean fin, int opcode, byte[] payload) { this.fin = fin; this.opcode = opcode; this.payload = payload; }
    }

    static byte[] closePayload(int code, String reason) {
        byte[] r = reason == null ? new byte[0] : reason.getBytes(UTF8);
        byte[] p = new byte[2 + r.length];
        p[0] = (byte) (code >> 8);
        p[1] = (byte) code;
        System.arraycopy(r, 0, p, 2, r.length);
        return p;
    }

    /** One final frame. mask == null for an unmasked (server-style) frame. */
    static byte[] encodeFrame(int opcode, byte[] payload, byte[] mask) {
        int len = payload.length;
        int head = 2 + (len < 126 ? 0 : len <= 0xFFFF ? 2 : 8) + (mask == null ? 0 : 4);
        byte[] f = new byte[head + len];
        f[0] = (byte) (0x80 | (opcode & 0x0F));
        int i = 1;
        int maskBit = mask == null ? 0 : 0x80;
        if (len < 126) {
            f[i++] = (byte) (maskBit | len);
        } else if (len <= 0xFFFF) {
            f[i++] = (byte) (maskBit | 126);
            f[i++] = (byte) (len >> 8);
            f[i++] = (byte) len;
        } else {
            f[i++] = (byte) (maskBit | 127);
            long l = len;
            for (int s = 56; s >= 0; s -= 8) f[i++] = (byte) (l >> s);
        }
        if (mask != null) {
            System.arraycopy(mask, 0, f, i, 4);
            i += 4;
            for (int j = 0; j < len; j++) f[i + j] = (byte) (payload[j] ^ mask[j & 3]);
        } else {
            System.arraycopy(payload, 0, f, i, len);
        }
        return f;
    }

    static Frame readFrame(InputStream in, int maxLen) throws IOException {
        int b0 = readByte(in), b1 = readByte(in);
        boolean fin = (b0 & 0x80) != 0;
        int op = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) len = ((long) readByte(in) << 8) | readByte(in);
        else if (len == 127) { len = 0; for (int k = 0; k < 8; k++) len = (len << 8) | readByte(in); }
        if (len < 0 || len > maxLen) throw new IOException("frame too large: " + len);
        byte[] mask = null;
        if (masked) { mask = new byte[4]; readFully(in, mask); }
        byte[] p = new byte[(int) len];
        readFully(in, p);
        if (mask != null) for (int j = 0; j < p.length; j++) p[j] ^= mask[j & 3];
        return new Frame(fin, op, p);
    }

    private static int readByte(InputStream in) throws IOException {
        int c = in.read();
        if (c < 0) throw new EOFException("connection closed");
        return c;
    }

    private static void readFully(InputStream in, byte[] b) throws IOException {
        int r = 0;
        while (r < b.length) {
            int n = in.read(b, r, b.length - r);
            if (n < 0) throw new EOFException("connection closed");
            r += n;
        }
    }
}

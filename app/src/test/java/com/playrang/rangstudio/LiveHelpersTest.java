package com.playrang.rangstudio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** Pure helpers behind Live mode: Base64, WebSocket frames, streamed sentences, model fallback, tool mapping. */
public class LiveHelpersTest {

    // ---------------------------------------------------------------- Base64
    @Test public void base64Vectors() {
        assertEquals("", B64.encode(new byte[0]));
        assertEquals("Zg==", B64.encode("f".getBytes()));
        assertEquals("Zm8=", B64.encode("fo".getBytes()));
        assertEquals("Zm9v", B64.encode("foo".getBytes()));
        assertEquals("Zm9vYmFy", B64.encode("foobar".getBytes()));
        assertEquals("foobar", new String(B64.decode("Zm9vYmFy")));
        assertEquals("fo", new String(B64.decode("Zm8=")));
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) all[i] = (byte) i;
        assertArrayEquals(all, B64.decode(B64.encode(all)));
        assertEquals("Zm9v", B64.encode("xfooy".getBytes(), 1, 3));
    }

    @Test public void websocketAcceptKey() {
        // example from RFC 6455 section 1.3
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WsClient.acceptFor("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    // ---------------------------------------------------------------- WebSocket frames
    private static WsClient.Frame roundTrip(int op, byte[] payload, byte[] mask) throws Exception {
        byte[] f = WsClient.encodeFrame(op, payload, mask);
        return WsClient.readFrame(new ByteArrayInputStream(f), 1 << 24);
    }

    @Test public void framesRoundTrip() throws Exception {
        byte[] mask = {1, 2, 3, 4};
        for (int n : new int[]{0, 5, 125, 126, 300, 65535, 65536, 200000}) {
            byte[] p = new byte[n];
            for (int i = 0; i < n; i++) p[i] = (byte) (i * 7);
            WsClient.Frame a = roundTrip(WsClient.OP_TEXT, p, mask);
            assertTrue(a.fin);
            assertEquals(WsClient.OP_TEXT, a.opcode);
            assertArrayEquals(p, a.payload);
            WsClient.Frame b = roundTrip(WsClient.OP_BIN, p, null);
            assertArrayEquals(p, b.payload);
        }
    }

    @Test public void maskedFrameHeader() {
        byte[] f = WsClient.encodeFrame(WsClient.OP_TEXT, "Hello".getBytes(), new byte[]{0x37, (byte) 0xfa, 0x21, 0x3d});
        // RFC 6455 5.7: a masked "Hello"
        assertArrayEquals(new byte[]{(byte) 0x81, (byte) 0x85, 0x37, (byte) 0xfa, 0x21, 0x3d, 0x7f, (byte) 0x9f, 0x4d, 0x51, 0x58}, f);
    }

    @Test public void closePayload() {
        byte[] p = WsClient.closePayload(1007, "x");
        assertEquals(3, p.length);
        assertEquals(1007, ((p[0] & 0xff) << 8) | (p[1] & 0xff));
    }

    // ---------------------------------------------------------------- streamed sentences
    @Test public void sentencesComeOutAsTheyComplete() {
        SentenceBuffer b = new SentenceBuffer();
        assertTrue(b.push("नमस्ते! मैं ठी").size() == 1);
        List<String> two = b.push("क हूँ। आप कैसे");
        assertEquals(1, two.size());
        assertEquals("मैं ठीक हूँ।", two.get(0));
        assertTrue(b.push(" हैं").isEmpty());
        assertEquals("आप कैसे हैं", b.flush());
    }

    @Test public void decimalsAndListNumbersAreNotSplit() {
        SentenceBuffer b = new SentenceBuffer();
        assertTrue(b.push("कीमत 3.5 लाख है").isEmpty());
        List<String> s = b.push(". बस इतना");
        assertEquals(1, s.size());
        assertEquals("कीमत 3.5 लाख है.", s.get(0));
    }

    @Test public void actionTagsAreNeverSpoken() {
        SentenceBuffer b = new SentenceBuffer();
        List<String> a = b.push("ठीक है, सोनू को कॉल कर रहा हूँ। [[ACT");
        assertEquals(1, a.size());
        assertEquals("ठीक है, सोनू को कॉल कर रहा हूँ।", a.get(0));
        assertTrue(b.push("ION:call_contact|सोनू").isEmpty());
        assertEquals("ठीक है, सोनू को कॉल कर रहा हूँ।", b.visible());
        assertTrue(b.push("]]").isEmpty());
        assertEquals("", b.flush());
        assertTrue(b.text().contains("[[ACTION:call_contact|सोनू]]"));
    }

    @Test public void longTextWithoutStopBreaksAtComma() {
        SentenceBuffer b = new SentenceBuffer();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 30; i++) s.append("बहुत अच्छा, ");
        List<String> out = b.push(s.toString());
        assertFalse(out.isEmpty());
        for (String o : out) assertTrue(o.length() <= SentenceBuffer.MAX_CHUNK + 5);
    }

    // ---------------------------------------------------------------- Live model fallback
    @Test public void attemptsStartWithNewestModelAndSearch() {
        List<LiveModels.Attempt> a = LiveModels.attempts(0);
        assertEquals("gemini-3.8-live", a.get(0).model);
        assertTrue(a.get(0).search);
        assertFalse(a.get(1).search);
        assertTrue(a.get(0).blockingBehavior());
        assertFalse(a.get(2).blockingBehavior());
        assertTrue(a.get(a.size() - 1).minimal);
        // a remembered working attempt goes first
        LiveModels.Attempt third = a.get(3);
        List<LiveModels.Attempt> b = LiveModels.attempts(3);
        assertEquals(third.toString(), b.get(0).toString());
        assertEquals(a.size(), b.size());
        assertEquals(3, LiveModels.indexOf(third));
    }

    @Test public void closeReasons() {
        assertTrue(LiveModels.isKeyProblem(1007, "API key not valid. Please pass a valid API key."));
        assertFalse(LiveModels.isKeyProblem(1008, "models/gemini-x is not found for API version v1beta"));
        assertTrue(LiveModels.isQuota(1011, "You exceeded your current quota"));
        assertFalse(LiveModels.isQuota(1000, ""));
    }

    @Test public void clockAndLevel() {
        assertArrayEquals(new int[]{6, 30}, LiveModels.parseClock("06:30"));
        assertArrayEquals(new int[]{19, 0}, LiveModels.parseClock("19"));
        assertNull(LiveModels.parseClock("कल"));
        assertNull(LiveModels.parseClock("25:00"));
        assertEquals(0f, LiveModels.level(new byte[100], 100), 0.0001f);
        byte[] loud = new byte[100];
        for (int i = 0; i < 100; i += 2) { loud[i] = (byte) 0xff; loud[i + 1] = 0x7f; }
        assertEquals(1f, LiveModels.level(loud, 100), 0.0001f);
    }

    // ---------------------------------------------------------------- tool mapping
    private static Map<String, String> m(String... kv) {
        Map<String, String> r = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) r.put(kv[i], kv[i + 1]);
        return r;
    }

    @Test public void toolsMapToActions() {
        LiveTools.Act a = LiveTools.map("call_contact", m("name", "सोनू कुमार"));
        assertEquals("call_contact", a.type);
        assertEquals("सोनू कुमार", a.a);
        assertTrue(a.leaves);
        assertEquals("call_number", LiveTools.map("call_contact", m("name", "9876543210")).type);
        LiveTools.Act w = LiveTools.map("whatsapp_message", m("name", "मम्मी", "message", "खाना खा लिया"));
        assertEquals("whatsapp_contact", w.type);
        assertEquals("खाना खा लिया", w.b);
        assertEquals("whatsapp", LiveTools.map("whatsapp_message", m("name", "+91 98765 43210", "message", "hi")).type);
        assertEquals("7.05", LiveTools.map("set_alarm", m("hour", "7", "minute", "5")).a);
        assertEquals("6.30", LiveTools.map("set_alarm", m("time", "06:30")).a);
        assertEquals("300", LiveTools.map("set_timer", m("seconds", "300.0")).a);
        assertEquals("120", LiveTools.map("set_timer", m("minutes", "2")).a);
        assertEquals("off", LiveTools.map("torch", m("on", "false")).a);
        assertFalse(LiveTools.map("torch", m("on", "true")).leaves);
        assertEquals("youtube", LiveTools.map("play_youtube", m("query", "हनुमान चालीसा")).type);
        assertEquals("maps", LiveTools.map("open_map", m("place", "पटना जंक्शन")).type);
        assertNull(LiveTools.map("current_time", m()));
        assertNull(LiveTools.map("nope", m()));
    }
}

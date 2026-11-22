package com.playrang.rangstudio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Talks to the AI service. All methods are blocking: call them off the main thread. */
final class AiClient {

    static final String SYSTEM_PROMPT =
            "तुम 'मित्र' (Mitra) हो — एक दोस्ताना AI मित्र, जिसे PlayRang Technologies ने बनाया है। "
            + "तुम भारत के लोगों से उनकी भाषा में बात करते हो: अगर यूज़र हिंदी (देवनागरी) में लिखे तो हिंदी में, "
            + "Hinglish (Roman Hindi) में लिखे तो Hinglish में, English में लिखे तो सरल English में जवाब दो। "
            + "जवाब छोटे, साफ़, गर्मजोशी भरे और काम के हों (आम तौर पर 1-4 वाक्य), क्योंकि जवाब बोलकर भी सुनाया जाता है। "
            + "Markdown, टेबल या लंबी लिस्ट मत बनाओ। ज़िंदगी, पढ़ाई, सेहत, पैसा, रिश्ते—किसी भी बात पर दोस्त की तरह सलाह दो; "
            + "गंभीर मेडिकल/कानूनी मामलों में विशेषज्ञ से मिलने की सलाह भी दो।\n"
            + "तुम फ़ोन पर ये काम करा सकते हो। अगर यूज़र कोई काम कराना चाहे, तो जवाब के आख़िर में ठीक एक टैग लिखो:\n"
            + "[[ACTION:youtube|खोज शब्द]] — YouTube पर वीडियो/गाना चलाना\n"
            + "[[ACTION:open_url|https://...]] — वेबसाइट खोलना\n"
            + "[[ACTION:search|खोज शब्द]] — Google पर खोजना\n"
            + "[[ACTION:open_app|ऐप का नाम]] — फ़ोन का कोई ऐप खोलना\n"
            + "[[ACTION:dial|नंबर]] — नंबर डायल करना\n"
            + "[[ACTION:whatsapp|नंबर|संदेश]] — WhatsApp संदेश\n"
            + "[[ACTION:alarm|HH:MM]] — अलार्म (24 घंटे वाला समय)\n"
            + "[[ACTION:maps|जगह]] — नक्शे पर जगह/रास्ता\n"
            + "[[ACTION:camera]] — कैमरा, [[ACTION:torch|on]] या [[ACTION:torch|off]] — टॉर्च\n"
            + "[[ACTION:share|टेक्स्ट]] — टेक्स्ट शेयर करना\n"
            + "टैग सिर्फ़ तब लगाओ जब यूज़र सच में कोई काम कराना चाहे। आम सवालों के लिए टैग मत लगाओ। "
            + "तुम दूसरे ऐप्स के अंदर बटन नहीं दबा सकते — सिर्फ़ उन्हें खोल सकते हो।";

    static final class Result {
        String text;        // reply text (may contain action tag)
        int waitSeconds;    // >0 when rate-limited
        String error;       // non-null on failure
    }

    /** history: list of {role, content} with role "user" or "assistant"; last item is the new user message. */
    static Result ask(List<String[]> history, String geminiKey, String geminiModel) {
        if (geminiKey != null && geminiKey.trim().length() > 10) {
            Result r = gemini(history, geminiKey.trim(), geminiModel);
            if (r.error == null || r.waitSeconds > 0) return r;
            // Gemini failed (bad key / model); fall back to the free engine.
            Result p = pollinations(history);
            if (p.error != null) p.error = r.error;
            return p;
        }
        return pollinations(history);
    }

    private static final Pattern RETRY = Pattern.compile("(?i)retry after\\s*(\\d+)\\s*s");
    private static final Pattern ANY_SECONDS = Pattern.compile("(\\d+)\\s*(?:s\\b|sec|second)");

    static Result pollinations(List<String[]> history) {
        Result res = new Result();
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT + "\nआज की तारीख़: " + LocalInfo.todayText()));
            for (String[] m : history) msgs.put(new JSONObject().put("role", m[0]).put("content", m[1]));
            JSONObject body = new JSONObject().put("model", "openai").put("messages", msgs).put("private", true);
            String[] out = post("https://text.pollinations.ai/openai", body.toString());
            int code = Integer.parseInt(out[0]);
            String txt = out[1];
            if (txt == null) txt = "";
            String low = txt.toLowerCase();
            if (code == 402 || code == 429 || low.contains("free limit") || low.contains("free_limit") || low.contains("rate limit")) {
                res.waitSeconds = parseWait(txt);
                return res;
            }
            if (code >= 200 && code < 300) {
                String t = txt.trim();
                if (t.startsWith("{")) {
                    JSONObject j = new JSONObject(t);
                    JSONArray ch = j.optJSONArray("choices");
                    if (ch != null && ch.length() > 0) {
                        String c = ch.getJSONObject(0).getJSONObject("message").optString("content", "");
                        if (c.trim().length() > 0) { res.text = c.trim(); return res; }
                    }
                    res.error = "AI से खाली जवाब आया।";
                } else if (t.length() > 0) {
                    res.text = t; // plain text reply
                } else {
                    res.error = "AI से खाली जवाब आया।";
                }
                return res;
            }
            res.error = "AI सर्वर से जवाब नहीं मिला (कोड " + code + ")।";
        } catch (Exception e) {
            res.error = "इंटरनेट कनेक्शन जाँचिए, AI से जुड़ नहीं पाया।";
        }
        return res;
    }

    static int parseWait(String txt) {
        Matcher m = RETRY.matcher(txt);
        if (m.find()) return clampWait(Integer.parseInt(m.group(1)));
        m = ANY_SECONDS.matcher(txt);
        int best = -1;
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            if (v > 0 && v <= 120 && v != 45) { best = v; break; }
            if (best < 0) best = v;
        }
        return clampWait(best > 0 ? best : 45);
    }

    private static int clampWait(int s) { return Math.max(3, Math.min(90, s + 1)); }

    static Result gemini(List<String[]> history, String key, String model) {
        Result res = new Result();
        if (model == null || model.trim().isEmpty()) model = "gemini-flash-latest";
        try {
            JSONObject body = new JSONObject();
            body.put("systemInstruction", new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", SYSTEM_PROMPT + "\nआज की तारीख़: " + LocalInfo.todayText()))));
            JSONArray contents = new JSONArray();
            for (String[] m : history) {
                contents.put(new JSONObject().put("role", "assistant".equals(m[0]) ? "model" : "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", m[1]))));
            }
            body.put("contents", contents);
            String[] out = post("https://generativelanguage.googleapis.com/v1beta/models/"
                    + URLEncoder.encode(model.trim(), "UTF-8") + ":generateContent?key=" + URLEncoder.encode(key, "UTF-8"),
                    body.toString());
            int code = Integer.parseInt(out[0]);
            if (code == 404 && !"gemini-2.5-flash".equals(model.trim())) return gemini(history, key, "gemini-2.5-flash");
            if (code == 429) { res.waitSeconds = parseWait(out[1] == null ? "" : out[1]); if (res.waitSeconds > 60) res.waitSeconds = 30; return res; }
            if (code < 200 || code >= 300) {
                res.error = (code == 400 || code == 403) ? "Gemini API key सही नहीं लग रही — सेटिंग्स में जाँचिए।"
                        : "Gemini से जवाब नहीं मिला (कोड " + code + ")।";
                return res;
            }
            JSONObject j = new JSONObject(out[1]);
            JSONArray cands = j.optJSONArray("candidates");
            StringBuilder sb = new StringBuilder();
            if (cands != null && cands.length() > 0) {
                JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                JSONArray parts = content == null ? null : content.optJSONArray("parts");
                if (parts != null) for (int i = 0; i < parts.length(); i++) sb.append(parts.getJSONObject(i).optString("text", ""));
            }
            if (sb.toString().trim().isEmpty()) res.error = "Gemini से खाली जवाब आया।";
            else res.text = sb.toString().trim();
        } catch (Exception e) {
            res.error = "इंटरनेट कनेक्शन जाँचिए, Gemini से जुड़ नहीं पाया।";
        }
        return res;
    }

    /** Returns {statusCode, body}. */
    static String[] post(String url, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Mitra-Android/2.0");
        OutputStream os = c.getOutputStream();
        os.write(json.getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = is == null ? "" : readAll(is);
        c.disconnect();
        return new String[]{String.valueOf(code), body};
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        c.setRequestProperty("Accept-Language", "hi-IN,hi;q=0.9,en;q=0.8");
        c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI");
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = is == null ? "" : readAll(is);
        c.disconnect();
        return body;
    }

    static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), "UTF-8");
    }
}

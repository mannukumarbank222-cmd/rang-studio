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
            "तुम 'मित्र' (Mitra) हो — एक दोस्ताना AI मित्र, जिसे PlayRang Technologies ने बनाया है।\n"
            + "भाषा: यूज़र जिस भाषा/बोली और लिपि में लिखे, ठीक उसी में जवाब दो। हिंदी (देवनागरी), Hinglish (Roman हिंदी), English, "
            + "भोजपुरी (भोजपुरी शब्दों में — 'बानी', 'रउआ', 'का हो', 'कइसे'), मैथिली, मगही, हरियाणवी, राजस्थानी, "
            + "ਪੰਜਾਬੀ (ਗੁਰਮੁਖੀ), ગુજરાતી, मराठी, বাংলা, ଓଡ଼ିଆ, অসমীয়া, தமிழ், తెలుగు, ಕನ್ನಡ, മലയാളം, اردو, नेपाली, कोंकणी, "
            + "कश्मीरी, सिंधी, डोगरी, मणिपुरी, संताली — सब समझो। Roman लिपि में लिखी Bhojpuri/Punjabi/Marathi आदि को पहचानो और उसी बोली में Roman लिपि में जवाब दो। "
            + "बोली को हिंदी में मत बदलो। "
            + "जवाब छोटे, साफ़, गर्मजोशी भरे और काम के हों (आम तौर पर 1-4 वाक्य), क्योंकि जवाब बोलकर भी सुनाया जाता है। "
            + "Markdown, टेबल या लंबी लिस्ट मत बनाओ। ज़िंदगी, पढ़ाई, सेहत, पैसा, रिश्ते—किसी भी बात पर दोस्त की तरह सलाह दो; "
            + "गंभीर मेडिकल/कानूनी मामलों में विशेषज्ञ से मिलने की सलाह भी दो।\n"
            + "फ़ोन के काम: अगर यूज़र कोई काम कराना चाहे (किसी भी भाषा में), तो एक छोटा वाक्य लिखो और जवाब के आख़िर में ठीक एक टैग लगाओ:\n"
            + "[[ACTION:call_contact|नाम]] — कॉन्टैक्ट को नाम से कॉल (जैसे [[ACTION:call_contact|सोनू कुमार]], [[ACTION:call_contact|mummy]])\n"
            + "[[ACTION:call_number|नंबर]] — किसी नंबर पर कॉल\n"
            + "[[ACTION:whatsapp_contact|नाम|संदेश]] — कॉन्टैक्ट को WhatsApp संदेश (संदेश यूज़र के शब्दों में)\n"
            + "[[ACTION:whatsapp_call|नाम]] — कॉन्टैक्ट की WhatsApp चैट खोलना ताकि वह WhatsApp कॉल कर सके\n"
            + "[[ACTION:whatsapp|नंबर|संदेश]] — किसी नंबर को WhatsApp संदेश\n"
            + "[[ACTION:sms_contact|नाम|संदेश]] — कॉन्टैक्ट को SMS\n"
            + "[[ACTION:contact_number|नाम]] — कॉन्टैक्ट का नंबर दिखाना\n"
            + "[[ACTION:youtube|खोज शब्द]] — YouTube पर वीडियो/गाना चलाना\n"
            + "[[ACTION:open_app|ऐप का नाम]] — कोई ऐप खोलना (phone/dialer, whatsapp, youtube, camera, gallery, settings, facebook, instagram, "
            + "phonepe, paytm, gpay, flipkart, amazon, chrome, play store, calculator, clock …)\n"
            + "[[ACTION:website|https://...]] — वेबसाइट खोलना\n"
            + "[[ACTION:search|खोज शब्द]] — Google पर खोजना\n"
            + "[[ACTION:alarm|HH:MM]] — अलार्म (24 घंटे वाला समय, जैसे 06:30 या 19:00)\n"
            + "[[ACTION:timer|सेकंड]] — टाइमर (सिर्फ़ सेकंड की संख्या, जैसे 300)\n"
            + "[[ACTION:torch|on]] या [[ACTION:torch|off]] — टॉर्च\n"
            + "[[ACTION:maps|जगह]] — नक्शे पर जगह/रास्ता\n"
            + "[[ACTION:camera]] — कैमरा\n"
            + "[[ACTION:share|टेक्स्ट]] — टेक्स्ट शेयर करना\n"
            + "ज़रूरी नियम: फ़ोन के कॉन्टैक्ट्स ऐप खुद ढूँढ लेता है, इसलिए कभी मत कहो कि 'मुझे नंबर नहीं पता', 'मैं कॉल ऐप नहीं जानता' या 'मैं कॉल नहीं कर सकता'। "
            + "किसी को कॉल/मैसेज करने को कहा जाए तो हमेशा नाम के साथ call_contact / whatsapp_contact / sms_contact टैग लगाओ "
            + "(नाम यूज़र के ही शब्दों में, 'को/ko/ਨੂੰ/ને/ला/কে' जैसे शब्द हटाकर)। "
            + "टैग सिर्फ़ तब लगाओ जब यूज़र सच में कोई काम कराना चाहे; आम सवालों के लिए टैग मत लगाओ। टैग का फ़ॉर्मैट बिल्कुल ऊपर जैसा रखो। "
            + "तुम दूसरे ऐप्स के अंदर बटन नहीं दबा सकते — WhatsApp/SMS में Send यूज़र खुद दबाता है।\n"
            + "हर सवाल का जवाब देना ज़रूरी है: इतिहास, विज्ञान, भूगोल, खेल, फ़िल्में, धर्म, गणित, देश-दुनिया — किसी भी आम सवाल से कभी मना मत करो, "
            + "'मुझे नहीं पता' कहकर मत छोड़ो; पक्का न पता हो तो अपना सबसे अच्छा अंदाज़ा साफ़ बताओ। जवाब बोलकर सुनाने लायक छोटे रखो। "
            + "पिछली बातचीत याद रखो ताकि 'और बताओ', 'उसका', 'वो कब हुआ' जैसे सवाल समझ सको।";

    static final class Result {
        String text;        // reply text (may contain action tag)
        int waitSeconds;    // >0 when rate-limited
        String error;       // non-null on failure
        String source;      // which engine answered (gemini, pollinations, llm7, wikipedia, none)
    }

    /** history: list of {role, content} with role "user" or "assistant"; last item is the new user message. */
    static Result ask(List<String[]> history, String geminiKey, String geminiModel) { return ask(history, geminiKey, geminiModel, null); }

    private static String system(String langLine) {
        return SYSTEM_PROMPT + (langLine == null ? "" : "\n" + langLine) + "\nआज की तारीख़: " + LocalInfo.todayText();
    }

    static Result ask(List<String[]> history, String geminiKey, String geminiModel, String langLine) {
        return ask(history, geminiKey, geminiModel, langLine, 50);
    }

    /** Last time a request went to the free Pollinations endpoint (it allows ~1 reply / 45 s per IP). */
    private static volatile long lastPolli;
    private static final long POLLI_GAP_MS = 45000;

    private static boolean ok(Result r) { return r != null && r.error == null && r.waitSeconds <= 0 && r.text != null && r.text.trim().length() > 0; }

    /**
     * Never returns a blank: tries Gemini (if a key is set), Pollinations POST, Pollinations GET, LLM7, then
     * Wikipedia / DuckDuckGo, waiting for the free limit when that is short enough, and finally a polite message.
     * @param maxWaitSec how long we may wait for the free AI's rate limit before using the fallbacks' answer
     */
    static Result ask(List<String[]> history, String geminiKey, String geminiModel, String langLine, int maxWaitSec) {
        sys = system(langLine);
        String q = lastUser(history);
        if (geminiKey != null && geminiKey.trim().length() > 10) {
            Result r = gemini(history, geminiKey.trim(), geminiModel);
            if (ok(r)) { r.source = "gemini"; return r; }
        }
        int polliWait = 0;
        long since = System.currentTimeMillis() - lastPolli;
        if (since >= POLLI_GAP_MS) {
            lastPolli = System.currentTimeMillis();
            Result r = pollinations(history);
            if (ok(r)) { r.source = "pollinations"; return r; }
            if (r.waitSeconds > 0) polliWait = r.waitSeconds;
            else {
                Result g = pollinationsGet(history);
                if (ok(g)) { g.source = "pollinations-get"; return g; }
            }
        } else {
            polliWait = (int) Math.ceil((POLLI_GAP_MS - since) / 1000.0);
        }
        Result l = llm7(history, "gpt-oss:20b");
        if (!ok(l) && l.error != null && l.error.startsWith("model")) l = llm7(history, "mistral-Nemo-Instruct-2407");
        if (ok(l)) { l.source = "llm7"; return l; }

        String know = null;
        try { know = Knowledge.answer(q); } catch (Throwable ignored) { }
        if (know != null && (Knowledge.isFactual(q) || polliWait <= 0 || polliWait > maxWaitSec)) return text(know, "wikipedia");

        if (polliWait > 0 && polliWait <= maxWaitSec) {
            try { Thread.sleep(polliWait * 1000L); } catch (InterruptedException e) { return text(sorry(), "none"); }
            lastPolli = System.currentTimeMillis();
            Result r = pollinations(history);
            if (ok(r)) { r.source = "pollinations"; return r; }
            Result g = pollinationsGet(history);
            if (ok(g)) { g.source = "pollinations-get"; return g; }
        }
        if (know != null) return text(know, "wikipedia");
        return text(sorry(), "none");
    }

    static String sorry() {
        return "माफ़ कीजिए, अभी इंटरनेट या AI सेवा से जवाब नहीं मिल पाया। इंटरनेट जाँचकर थोड़ी देर में फिर पूछिए — मैं ज़रूर बताऊँगा। 🙏";
    }

    private static Result text(String t, String src) { Result r = new Result(); r.text = t; r.source = src; return r; }

    static String lastUser(List<String[]> history) {
        for (int i = history.size() - 1; i >= 0; i--) if ("user".equals(history.get(i)[0])) {
            String t = history.get(i)[1];
            int n = t.indexOf("\n\n(ऐप का नोट:");
            return n > 0 ? t.substring(0, n) : t;
        }
        return "";
    }

    /** Pollinations "classic" GET endpoint with a short system prompt and the last few turns in the URL. */
    static Result pollinationsGet(List<String[]> history) {
        Result res = new Result();
        try {
            StringBuilder p = new StringBuilder();
            int from = Math.max(0, history.size() - 4);
            boolean multi = from < history.size() - 1;
            for (int i = from; i < history.size(); i++) {
                String[] m = history.get(i);
                String c = i == history.size() - 1 ? lastUser(history) : m[1];
                if (c.length() > 600) c = c.substring(0, 600);
                if (multi) p.append("assistant".equals(m[0]) ? "Mitra: " : "User: ");
                p.append(c).append("\n");
            }
            String shortSys = "You are Mitra (मित्र), a friendly Indian voice assistant by PlayRang Technologies. Always answer the question; "
                    + "never refuse ordinary questions. Reply in the same language and script as the user, in 1-3 short spoken-friendly sentences, no markdown.";
            String url = "https://text.pollinations.ai/" + URLEncoder.encode(p.toString(), "UTF-8").replace("+", "%20")
                    + "?model=openai&private=true&system=" + URLEncoder.encode(shortSys, "UTF-8").replace("+", "%20");
            String[] out = request("GET", url, null, 10000, 30000);
            int code = Integer.parseInt(out[0]);
            String t = out[1] == null ? "" : out[1].trim();
            String low = t.toLowerCase();
            if (code == 402 || code == 429 || low.contains("free limit") || low.contains("rate limit")) { res.waitSeconds = parseWait(t); return res; }
            if (code >= 200 && code < 300 && t.length() > 0 && !t.startsWith("{\"error") && !t.startsWith("<")) res.text = t;
            else res.error = "GET " + code;
        } catch (Exception e) {
            res.error = "net";
        }
        return res;
    }

    /** LLM7 (OpenAI-compatible, key-free with a per-IP free allowance). */
    static Result llm7(List<String[]> history, String model) {
        Result res = new Result();
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", sys));
            for (String[] m : history) msgs.put(new JSONObject().put("role", m[0]).put("content", m[1]));
            JSONObject body = new JSONObject().put("model", model).put("messages", msgs);
            String[] out = request("POST", "https://api.llm7.io/v1/chat/completions", body.toString(), 10000, 30000);
            int code = Integer.parseInt(out[0]);
            String t = out[1] == null ? "" : out[1].trim();
            if (code == 400 && t.contains("unavailable")) { res.error = "model"; return res; }
            if (code < 200 || code >= 300 || !t.startsWith("{")) { res.error = "llm7 " + code; return res; }
            JSONArray ch = new JSONObject(t).optJSONArray("choices");
            String c = ch != null && ch.length() > 0 ? ch.getJSONObject(0).getJSONObject("message").optString("content", "") : "";
            if (c.trim().isEmpty()) res.error = "empty"; else res.text = c.trim();
        } catch (Exception e) {
            res.error = "net";
        }
        return res;
    }

    private static volatile String sys = SYSTEM_PROMPT;

    private static final Pattern RETRY = Pattern.compile("(?i)retry after\\s*(\\d+)\\s*s");
    private static final Pattern ANY_SECONDS = Pattern.compile("(\\d+)\\s*(?:s\\b|sec|second)");

    static Result pollinations(List<String[]> history) {
        Result res = new Result();
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", sys));
            for (String[] m : history) msgs.put(new JSONObject().put("role", m[0]).put("content", m[1]));
            JSONObject body = new JSONObject().put("model", "openai").put("messages", msgs).put("private", true);
            String[] out = request("POST", "https://text.pollinations.ai/openai", body.toString(), 10000, 35000);
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
                    new JSONArray().put(new JSONObject().put("text", sys))));
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
    static String[] request(String method, String url, String json, int connectMs, int readMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setRequestProperty("User-Agent", "Mitra-Android/2.2");
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (json != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            OutputStream os = c.getOutputStream();
            os.write(json.getBytes("UTF-8"));
            os.close();
        }
        try {
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = is == null ? "" : readAll(is);
            return new String[]{String.valueOf(code), body};
        } finally {
            c.disconnect();
        }
    }

    static String[] post(String url, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(12000);
        c.setReadTimeout(35000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Mitra-Android/2.2");
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

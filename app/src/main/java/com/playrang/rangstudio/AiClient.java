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
            + "[[ACTION:volume|up]] / [[ACTION:volume|down]] / [[ACTION:volume|mute]] / [[ACTION:volume|max]] — फ़ोन की आवाज़\n"
            + "[[ACTION:ringer|silent]] / [[ACTION:ringer|vibrate]] / [[ACTION:ringer|normal]] — फ़ोन साइलेंट/वाइब्रेट\n"
            + "[[ACTION:settings|wifi]] (या bluetooth, display, location, airplane, data, hotspot, battery, sound, apps) — फ़ोन की वह सेटिंग खोलना\n"
            + "[[ACTION:reminder|किस बात का रिमाइंडर|कब, जैसे कल सुबह 9 बजे]] — कैलेंडर में रिमाइंडर\n"
            + "[[ACTION:copy|टेक्स्ट]] — कोई बात नोट करके क्लिपबोर्ड पर कॉपी करना\n"
            + "[[ACTION:battery]] — बैटरी कितनी है\n"
            + "[[ACTION:upi]] — UPI ऐप (PhonePe/GPay/Paytm) खोलना; पेमेंट यूज़र खुद करता है\n"
            + "[[ACTION:navigate|जगह]] — उस जगह तक रास्ता (नेविगेशन) शुरू करना\n"
            + "[[ACTION:camera|front]] — सेल्फ़ी कैमरा\n"
            + "एक से ज़्यादा काम कहे जाएँ ('X को कॉल करो और फिर YouTube खोलो') तो उसी क्रम में कई टैग लगाओ (ज़्यादा से ज़्यादा 5)।\n"
            + "हिसाब/गणित के सवाल (जैसे 15% GST, EMI, जोड़-घटाना) खुद सही-सही हल करके जवाब बताओ।\n"
            + "ज़रूरी नियम: फ़ोन के कॉन्टैक्ट्स ऐप खुद ढूँढ लेता है, इसलिए कभी मत कहो कि 'मुझे नंबर नहीं पता', 'मैं कॉल ऐप नहीं जानता' या 'मैं कॉल नहीं कर सकता'। "
            + "किसी को कॉल/मैसेज करने को कहा जाए तो हमेशा नाम के साथ call_contact / whatsapp_contact / sms_contact टैग लगाओ "
            + "(नाम यूज़र के ही शब्दों में, 'को/ko/ਨੂੰ/ને/ला/কে' जैसे शब्द हटाकर)। "
            + "टैग सिर्फ़ तब लगाओ जब यूज़र सच में कोई काम कराना चाहे; आम सवालों के लिए टैग मत लगाओ। टैग का फ़ॉर्मैट बिल्कुल ऊपर जैसा रखो। "
            + "पैसे भेजने/पेमेंट के लिए सिर्फ़ upi ऐप खोलो — खुद कभी पेमेंट, OTP या PIN मत माँगो। "
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
        return ask(history, geminiKey, geminiModel, langLine, 20);
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
        final String sy = system(langLine);
        String q = lastUser(history);
        if (geminiKey != null && geminiKey.trim().length() > 10) {
            Result r = gemini(history, geminiKey.trim(), geminiModel, sy);
            if (ok(r)) { r.source = "gemini"; return r; }
        }
        // Free engines race: Pollinations and LLM7 start together and the first good answer wins (was one after another).
        int polliWait = 0;
        long since = System.currentTimeMillis() - lastPolli;
        boolean polliOk = since >= POLLI_GAP_MS;
        if (!polliOk) polliWait = (int) Math.ceil((POLLI_GAP_MS - since) / 1000.0);
        else lastPolli = System.currentTimeMillis();
        final java.util.concurrent.LinkedBlockingQueue<Result> done = new java.util.concurrent.LinkedBlockingQueue<>();
        int racers = 0;
        if (polliOk) {
            racers++;
            Thread t = new Thread(() -> {
                Result r = pollinations(history, sy);
                if (ok(r)) r.source = "pollinations";
                else if (r.waitSeconds <= 0) { Result g = pollinationsGet(history, sy); if (ok(g)) { g.source = "pollinations-get"; r = g; } }
                done.offer(r);
            }, "mitra-ai-polli");
            t.setDaemon(true);
            t.start();
        }
        racers++;
        Thread t2 = new Thread(() -> {
            Result l = llm7(history, "gpt-oss:20b", sy);
            if (!ok(l) && l.error != null && l.error.startsWith("model")) l = llm7(history, "mistral-Nemo-Instruct-2407", sy);
            if (ok(l)) l.source = "llm7";
            done.offer(l);
        }, "mitra-ai-llm7");
        t2.setDaemon(true);
        t2.start();
        long deadline = System.currentTimeMillis() + 17000;
        for (int got = 0; got < racers; got++) {
            Result r;
            try { r = done.poll(Math.max(1, deadline - System.currentTimeMillis()), java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { return text(sorry(), "none"); }
            if (r == null) break;
            if (ok(r)) return r;
            if (r.waitSeconds > 0) polliWait = r.waitSeconds;
        }

        String know = null;
        try { know = Knowledge.answer(q); } catch (Throwable ignored) { }
        if (know != null && (Knowledge.isFactual(q) || polliWait <= 0 || polliWait > maxWaitSec)) return text(know, "wikipedia");

        if (polliWait > 0 && polliWait <= maxWaitSec) {
            try { Thread.sleep(polliWait * 1000L); } catch (InterruptedException e) { return text(sorry(), "none"); }
            lastPolli = System.currentTimeMillis();
            Result r = pollinations(history, sy);
            if (ok(r)) { r.source = "pollinations"; return r; }
            Result g = pollinationsGet(history, sy);
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
    static Result pollinationsGet(List<String[]> history, String sy) {
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
                    + "never refuse ordinary questions. Reply in the same language and script as the user, in 1-3 short spoken-friendly sentences, no markdown."
                    + langHint(sy);
            String url = "https://text.pollinations.ai/" + URLEncoder.encode(p.toString(), "UTF-8").replace("+", "%20")
                    + "?model=openai&private=true&system=" + URLEncoder.encode(shortSys, "UTF-8").replace("+", "%20");
            String[] out = request("GET", url, null, 5000, 14000);
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
    static Result llm7(List<String[]> history, String model, String sys) {
        Result res = new Result();
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", sys));
            for (String[] m : history) msgs.put(new JSONObject().put("role", m[0]).put("content", m[1]));
            JSONObject body = new JSONObject().put("model", model).put("messages", msgs);
            String[] out = request("POST", "https://api.llm7.io/v1/chat/completions", body.toString(), 5000, 15000);
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

    /** The "Reply ONLY in …" sentence of the language line, for the short GET prompt. */
    private static String langHint(String sy) {
        if (sy == null) return "";
        int i = sy.indexOf("Reply ONLY in ");
        if (i < 0) i = sy.indexOf("Always reply in ");
        if (i < 0) return "";
        int e = sy.indexOf('.', i);
        return " " + sy.substring(i, e < 0 ? sy.length() : e + 1);
    }

    private static final Pattern RETRY = Pattern.compile("(?i)retry after\\s*(\\d+)\\s*s");
    private static final Pattern ANY_SECONDS = Pattern.compile("(\\d+)\\s*(?:s\\b|sec|second)");

    static Result pollinations(List<String[]> history, String sys) {
        Result res = new Result();
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", sys));
            for (String[] m : history) msgs.put(new JSONObject().put("role", m[0]).put("content", m[1]));
            JSONObject body = new JSONObject().put("model", "openai").put("messages", msgs).put("private", true);
            String[] out = request("POST", "https://text.pollinations.ai/openai", body.toString(), 5000, 15000);
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

    static Result gemini(List<String[]> history, String key, String model, String sys) {
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
            if (code == 404 && !"gemini-2.5-flash".equals(model.trim())) return gemini(history, key, "gemini-2.5-flash", sys);
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

    // ------------------------------------------------------------------ streaming (Gemini SSE) — speech starts with the first sentence
    interface Sink { void onText(String delta); }

    private static volatile boolean noThinkingCfg;

    /**
     * Like {@link #ask} but, when a Gemini key is set, streams the reply through sink as it is written.
     * If streaming fails before any text arrived, falls back to the free chain (sink is then not called).
     */
    static Result askStream(List<String[]> history, String geminiKey, String geminiModel, String langLine, int maxWaitSec, Sink sink) {
        if (geminiKey != null && geminiKey.trim().length() > 10) {
            final String sys = system(langLine);
            final boolean[] any = {false};
            Result r = geminiStream(history, geminiKey.trim(), geminiModel, sys, d -> { any[0] = true; sink.onText(d); });
            if (ok(r) || any[0]) { r.source = "gemini-stream"; if (r.text == null) r.text = ""; r.error = null; return r; }
            if (r.waitSeconds > 0 && r.waitSeconds <= Math.min(maxWaitSec, 8)) {
                try { Thread.sleep(r.waitSeconds * 1000L); } catch (InterruptedException e) { return text(sorry(), "none"); }
                r = geminiStream(history, geminiKey.trim(), geminiModel, sys, d -> { any[0] = true; sink.onText(d); });
                if (ok(r) || any[0]) { r.source = "gemini-stream"; if (r.text == null) r.text = ""; r.error = null; return r; }
            }
            return ask(history, null, null, langLine, maxWaitSec); // free chain, no second Gemini try
        }
        return ask(history, null, null, langLine, maxWaitSec);
    }

    static Result geminiStream(List<String[]> history, String key, String model, String sys, Sink sink) {
        Result res = new Result();
        if (model == null || model.trim().isEmpty()) model = "gemini-flash-latest";
        HttpURLConnection c = null;
        try {
            JSONObject body = new JSONObject();
            body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", sys))));
            JSONArray contents = new JSONArray();
            for (String[] m : history) contents.put(new JSONObject().put("role", "assistant".equals(m[0]) ? "model" : "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", m[1]))));
            body.put("contents", contents);
            if (!noThinkingCfg) body.put("generationConfig", new JSONObject().put("thinkingConfig", new JSONObject().put("thinkingLevel", "low")));
            c = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models/" + URLEncoder.encode(model.trim(), "UTF-8")
                    + ":streamGenerateContent?alt=sse&key=" + URLEncoder.encode(key, "UTF-8")).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(5000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "text/event-stream");
            c.setRequestProperty("User-Agent", "Mitra-Android/4.0");
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            int code = c.getResponseCode();
            if (code >= 400) {
                InputStream es = c.getErrorStream();
                String eb = es == null ? "" : readAll(es);
                if (code == 400 && !noThinkingCfg && eb.toLowerCase(java.util.Locale.ROOT).contains("think")) {
                    noThinkingCfg = true;
                    c.disconnect();
                    return geminiStream(history, key, model, sys, sink);
                }
                if (code == 404 && !"gemini-flash-latest".equals(model.trim())) { c.disconnect(); return geminiStream(history, key, "gemini-flash-latest", sys, sink); }
                if (code == 429) { res.waitSeconds = parseWait(eb); if (res.waitSeconds > 60) res.waitSeconds = 30; return res; }
                res.error = (code == 400 || code == 403) ? "Gemini API key सही नहीं लग रही — सेटिंग्स में जाँचिए।" : "Gemini से जवाब नहीं मिला (कोड " + code + ")।";
                return res;
            }
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder all = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String d = line.substring(5).trim();
                if (d.isEmpty() || d.equals("[DONE]")) continue;
                try {
                    JSONObject j = new JSONObject(d);
                    JSONArray cands = j.optJSONArray("candidates");
                    if (cands == null || cands.length() == 0) continue;
                    JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                    JSONArray parts = content == null ? null : content.optJSONArray("parts");
                    if (parts == null) continue;
                    StringBuilder delta = new StringBuilder();
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject p = parts.getJSONObject(i);
                        if (p.optBoolean("thought", false)) continue;
                        delta.append(p.optString("text", ""));
                    }
                    if (delta.length() > 0) { all.append(delta); sink.onText(delta.toString()); }
                } catch (Exception ignored) { }
            }
            br.close(); // fully read + closed: the connection goes back to the keep-alive pool
            c = null;
            if (all.toString().trim().isEmpty()) res.error = "Gemini से खाली जवाब आया।";
            else res.text = all.toString().trim();
        } catch (Exception e) {
            res.error = "इंटरनेट कनेक्शन जाँचिए, Gemini से जुड़ नहीं पाया।";
        } finally {
            if (c != null) try { c.disconnect(); } catch (Exception ignored) { }
        }
        return res;
    }

    /** Returns {statusCode, body}. */
    static String[] request(String method, String url, String json, int connectMs, int readMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setRequestProperty("User-Agent", "Mitra-Android/4.0");
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
            String body = is == null ? "" : readAll(is); // read fully + close = connection reused (keep-alive)
            return new String[]{String.valueOf(code), body};
        } catch (Exception e) {
            c.disconnect();
            throw e;
        }
    }

    static String[] post(String url, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(5000);
        c.setReadTimeout(15000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Mitra-Android/4.0");
        OutputStream os = c.getOutputStream();
        os.write(json.getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = is == null ? "" : readAll(is);
        return new String[]{String.valueOf(code), body};
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        c.setRequestProperty("Accept-Language", "hi-IN,hi;q=0.9,en;q=0.8");
        c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI");
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        return is == null ? "" : readAll(is);
    }

    /**
     * Streams a page and stops at the first regex match (e.g. the first YouTube video id), so we do not wait for a
     * 1 MB page. @return the group, or null.
     */
    static String firstMatch(String url, Pattern p, int group, int maxBytes, int timeoutMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
            c.setRequestProperty("Accept-Language", "hi-IN,hi;q=0.9,en;q=0.8");
            c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI");
            if (c.getResponseCode() >= 400) return null;
            InputStream is = c.getInputStream();
            byte[] buf = new byte[16384];
            StringBuilder sb = new StringBuilder();
            int n, total = 0, scanned = 0;
            while ((n = is.read(buf)) > 0 && total < maxBytes) {
                total += n;
                sb.append(new String(buf, 0, n, "ISO-8859-1")); // ids are ASCII; byte-for-char keeps offsets simple
                Matcher m = p.matcher(sb);
                if (m.find(Math.max(0, scanned - 200))) return m.group(group);
                scanned = sb.length();
                if (sb.length() > 400000) { sb.delete(0, sb.length() - 2000); scanned = 0; }
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) try { c.disconnect(); } catch (Exception ignored) { }
        }
    }

    private static volatile long warmedAt;

    /**
     * Opens TLS connections to the AI hosts ahead of the first question (DNS + TLS handshake ≈ 0.3-1 s on mobile data);
     * the pooled keep-alive connection is then reused by the real request. Call off the main thread.
     */
    static void warmUp(boolean gemini) {
        if (System.currentTimeMillis() - warmedAt < 120000) return;
        warmedAt = System.currentTimeMillis();
        String[] hosts = gemini ? new String[]{"https://generativelanguage.googleapis.com/"}
                : new String[]{"https://text.pollinations.ai/", "https://api.llm7.io/"};
        for (String h : hosts) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(h).openConnection();
                c.setRequestMethod("HEAD");
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setRequestProperty("User-Agent", "Mitra-Android/4.0");
                c.getResponseCode();
                InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
                if (is != null) readAll(is);
            } catch (Exception ignored) { }
        }
    }

    static {
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "6");
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

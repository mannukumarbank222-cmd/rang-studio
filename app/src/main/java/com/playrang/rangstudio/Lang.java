package com.playrang.rangstudio;

/**
 * Languages Mitra can talk in. Each entry: id, label shown in the picker, speech-recognition locale,
 * TTS locale, the language name given to the AI, and a few localized UI strings.
 * Dialects without their own speech engine (Bhojpuri, Maithili, Magahi, Haryanvi …) use hi-IN recognition/voice.
 */
final class Lang {
    final String id, label, sr, tts, aiName, greeting, hint, listening, online, typing;

    private Lang(String id, String label, String sr, String tts, String aiName, String greeting, String hint, String listening,
                 String online, String typing) {
        this.id = id; this.label = label; this.sr = sr; this.tts = tts; this.aiName = aiName; this.greeting = greeting;
        this.hint = hint; this.listening = listening; this.online = online; this.typing = typing;
    }

    private static final String HI_G = "नमस्ते, मैं मित्र हूँ, आपका मित्र। आप मुझसे कोई भी सवाल पूछ सकते हैं और कुछ भी काम करा सकते हैं।";

    static final Lang[] ALL = {
            new Lang("auto", "अपने-आप (Auto) — जिस भाषा में बोलें", "hi-IN", "hi-IN", null, HI_G,
                    "कुछ भी पूछिए या बोलिए…", "🎙️ सुन रहा हूँ… बोलिए", "आपका AI मित्र • ऑनलाइन", "लिख रहा है…"),
            new Lang("hi", "हिंदी (Hindi)", "hi-IN", "hi-IN", "Hindi (Devanagari script)", HI_G,
                    "कुछ भी पूछिए या बोलिए…", "🎙️ सुन रहा हूँ… बोलिए", "आपका AI मित्र • ऑनलाइन", "लिख रहा है…"),
            new Lang("bho", "भोजपुरी (Bhojpuri)", "hi-IN", "hi-IN", "Bhojpuri (Devanagari script, real Bhojpuri words like 'बानी', 'रउआ', 'का हो')",
                    "प्रणाम! हम मित्र हईं, रउआ के आपन मित्र। हमसे कुछुओ पूछीं, कवनो काम कहीं — हम कर देब।",
                    "कुछुओ पूछीं भा बोलीं…", "🎙️ सुनत बानी… बोलीं", "रउआ के AI मित्र • ऑनलाइन", "लिखत बानी…"),
            new Lang("mai", "मैथिली (Maithili)", "hi-IN", "hi-IN", "Maithili (Devanagari script)",
                    "प्रणाम! हम मित्र छी, अहाँक मित्र। हमरासँ किछुओ पुछू, कोनो काज कहू।",
                    "किछुओ पुछू वा बाजू…", "🎙️ सुनि रहल छी… बाजू", "अहाँक AI मित्र • ऑनलाइन", "लिखि रहल छी…"),
            new Lang("mag", "मगही (Magahi)", "hi-IN", "hi-IN", "Magahi (Devanagari script)",
                    "परनाम! हम मित्र ही, तोहर मित्र। हमरा से कुछो पूछऽ, कोनो काम कहऽ।",
                    "कुछो पूछऽ या बोलऽ…", "🎙️ सुन रहलियो ह… बोलऽ", "तोहर AI मित्र • ऑनलाइन", "लिख रहलियो ह…"),
            new Lang("hne", "हरियाणवी / राजस्थानी", "hi-IN", "hi-IN", "Haryanvi or Rajasthani (whichever the user uses, Devanagari script)",
                    "राम राम सा! म्हैं मित्र सूं, थारो मित्र। कीं भी पूछो, कोई भी काम बताओ।",
                    "कीं भी पूछो या बोलो…", "🎙️ सुण रह्यो सूं… बोलो", "थारो AI मित्र • ऑनलाइन", "लिख रह्यो सूं…"),
            new Lang("pa", "ਪੰਜਾਬੀ (Punjabi)", "pa-IN", "pa-IN", "Punjabi (Gurmukhi script)",
                    "ਸਤ ਸ੍ਰੀ ਅਕਾਲ! ਮੈਂ ਮਿੱਤਰ ਹਾਂ, ਤੁਹਾਡਾ ਮਿੱਤਰ। ਮੈਨੂੰ ਕੁਝ ਵੀ ਪੁੱਛੋ ਜਾਂ ਕੋਈ ਵੀ ਕੰਮ ਕਹੋ।",
                    "ਕੁਝ ਵੀ ਪੁੱਛੋ ਜਾਂ ਬੋਲੋ…", "🎙️ ਸੁਣ ਰਿਹਾ ਹਾਂ… ਬੋਲੋ", "ਤੁਹਾਡਾ AI ਮਿੱਤਰ • ਔਨਲਾਈਨ", "ਲਿਖ ਰਿਹਾ ਹੈ…"),
            new Lang("gu", "ગુજરાતી (Gujarati)", "gu-IN", "gu-IN", "Gujarati (Gujarati script)",
                    "નમસ્તે! હું મિત્ર છું, તમારો મિત્ર. મને કંઈ પણ પૂછો અથવા કોઈ પણ કામ કહો.",
                    "કંઈ પણ પૂછો અથવા બોલો…", "🎙️ સાંભળું છું… બોલો", "તમારો AI મિત્ર • ઓનલાઇન", "લખી રહ્યો છે…"),
            new Lang("mr", "मराठी (Marathi)", "mr-IN", "mr-IN", "Marathi (Devanagari script)",
                    "नमस्कार! मी मित्र आहे, तुमचा मित्र. मला काहीही विचारा किंवा कोणतेही काम सांगा.",
                    "काहीही विचारा किंवा बोला…", "🎙️ ऐकतो आहे… बोला", "तुमचा AI मित्र • ऑनलाइन", "लिहित आहे…"),
            new Lang("bn", "বাংলা (Bengali)", "bn-IN", "bn-IN", "Bengali (Bengali script)",
                    "নমস্কার! আমি মিত্র, আপনার বন্ধু। আমাকে যা খুশি জিজ্ঞেস করুন বা যেকোনো কাজ বলুন।",
                    "যা খুশি জিজ্ঞেস করুন বা বলুন…", "🎙️ শুনছি… বলুন", "আপনার AI মিত্র • অনলাইন", "লিখছে…"),
            new Lang("or", "ଓଡ଼ିଆ (Odia)", "or-IN", "or-IN", "Odia (Odia script)",
                    "ନମସ୍କାର! ମୁଁ ମିତ୍ର, ଆପଣଙ୍କ ବନ୍ଧୁ। ମୋତେ କିଛି ବି ପଚାରନ୍ତୁ କିମ୍ବା କୌଣସି କାମ କୁହନ୍ତୁ।",
                    "କିଛି ବି ପଚାରନ୍ତୁ…", "🎙️ ଶୁଣୁଛି… କୁହନ୍ତୁ", "ଆପଣଙ୍କ AI ମିତ୍ର • ଅନଲାଇନ", "ଲେଖୁଛି…"),
            new Lang("as", "অসমীয়া (Assamese)", "as-IN", "as-IN", "Assamese (Assamese/Bengali script)",
                    "নমস্কাৰ! মই মিত্ৰ, আপোনাৰ বন্ধু। মোক যিকোনো কথা সোধক বা যিকোনো কাম কওক।",
                    "যিকোনো কথা সোধক…", "🎙️ শুনি আছোঁ… কওক", "আপোনাৰ AI মিত্ৰ • অনলাইন", "লিখি আছে…"),
            new Lang("ta", "தமிழ் (Tamil)", "ta-IN", "ta-IN", "Tamil (Tamil script)",
                    "வணக்கம்! நான் மித்ரா, உங்கள் நண்பன். என்னிடம் எதையும் கேளுங்கள், எந்த வேலையும் சொல்லுங்கள்.",
                    "எதையும் கேளுங்கள் அல்லது பேசுங்கள்…", "🎙️ கேட்கிறேன்… பேசுங்கள்", "உங்கள் AI நண்பன் • ஆன்லைன்", "எழுதுகிறது…"),
            new Lang("te", "తెలుగు (Telugu)", "te-IN", "te-IN", "Telugu (Telugu script)",
                    "నమస్తే! నేను మిత్ర, మీ స్నేహితుడిని. నన్ను ఏదైనా అడగండి, ఏ పనైనా చెప్పండి.",
                    "ఏదైనా అడగండి లేదా మాట్లాడండి…", "🎙️ వింటున్నాను… మాట్లాడండి", "మీ AI మిత్ర • ఆన్‌లైన్", "రాస్తోంది…"),
            new Lang("kn", "ಕನ್ನಡ (Kannada)", "kn-IN", "kn-IN", "Kannada (Kannada script)",
                    "ನಮಸ್ಕಾರ! ನಾನು ಮಿತ್ರ, ನಿಮ್ಮ ಸ್ನೇಹಿತ. ನನ್ನನ್ನು ಏನು ಬೇಕಾದರೂ ಕೇಳಿ, ಯಾವ ಕೆಲಸವಾದರೂ ಹೇಳಿ.",
                    "ಏನಾದರೂ ಕೇಳಿ ಅಥವಾ ಮಾತನಾಡಿ…", "🎙️ ಕೇಳುತ್ತಿದ್ದೇನೆ… ಮಾತನಾಡಿ", "ನಿಮ್ಮ AI ಮಿತ್ರ • ಆನ್‌ಲೈನ್", "ಬರೆಯುತ್ತಿದೆ…"),
            new Lang("ml", "മലയാളം (Malayalam)", "ml-IN", "ml-IN", "Malayalam (Malayalam script)",
                    "നമസ്കാരം! ഞാൻ മിത്ര, നിങ്ങളുടെ കൂട്ടുകാരൻ. എന്നോട് എന്തും ചോദിക്കൂ, ഏത് ജോലിയും പറയൂ.",
                    "എന്തും ചോദിക്കൂ അല്ലെങ്കിൽ പറയൂ…", "🎙️ കേൾക്കുന്നു… പറയൂ", "നിങ്ങളുടെ AI മിത്ര • ഓൺലൈൻ", "എഴുതുന്നു…"),
            new Lang("ur", "اردو (Urdu)", "ur-IN", "ur-IN", "Urdu (Urdu/Nastaliq script)",
                    "آداب! میں متر ہوں، آپ کا دوست۔ مجھ سے کچھ بھی پوچھیے یا کوئی بھی کام کہیے۔",
                    "کچھ بھی پوچھیے یا بولیے…", "🎙️ سن رہا ہوں… بولیے", "آپ کا AI دوست • آن لائن", "لکھ رہا ہے…"),
            new Lang("ne", "नेपाली (Nepali)", "ne-NP", "ne-NP", "Nepali (Devanagari script)",
                    "नमस्ते! म मित्र हुँ, तपाईंको साथी। मलाई जे पनि सोध्नुहोस् वा कुनै पनि काम भन्नुहोस्।",
                    "जे पनि सोध्नुहोस् वा बोल्नुहोस्…", "🎙️ सुन्दैछु… बोल्नुहोस्", "तपाईंको AI मित्र • अनलाइन", "लेख्दैछ…"),
            new Lang("kok", "कोंकणी (Konkani)", "mr-IN", "mr-IN", "Konkani (Devanagari script)",
                    "नमस्कार! हांव मित्र, तुमचो इश्ट. म्हाका कितेंय विचारात वा खंयचेंय काम सांगात.",
                    "कितेंय विचारात…", "🎙️ आयकता… उलयात", "तुमचो AI मित्र • ऑनलाइन", "बरयता…"),
            new Lang("ks", "कॉशुर / کٲشُر (Kashmiri)", "ur-IN", "ur-IN", "Kashmiri (Perso-Arabic script unless the user writes Devanagari)",
                    HI_G, "कुछ भी पूछिए या बोलिए…", "🎙️ सुन रहा हूँ… बोलिए", "आपका AI मित्र • ऑनलाइन", "लिख रहा है…"),
            new Lang("sd", "सिंधी / سنڌي (Sindhi)", "hi-IN", "hi-IN", "Sindhi (use the script the user writes in)",
                    HI_G, "कुछ भी पूछिए या बोलिए…", "🎙️ सुन रहा हूँ… बोलिए", "आपका AI मित्र • ऑनलाइन", "लिख रहा है…"),
            new Lang("doi", "डोगरी (Dogri)", "hi-IN", "hi-IN", "Dogri (Devanagari script)",
                    HI_G, "कुछ भी पूछिए या बोलिए…", "🎙️ सुन रहा हूँ… बोलिए", "आपका AI मित्र • ऑनलाइन", "लिख रहा है…"),
            new Lang("mni", "মণিপুরী / ꯃꯤꯇꯩꯂꯣꯟ (Manipuri)", "bn-IN", "bn-IN", "Manipuri/Meitei (Bengali script unless the user writes Meitei Mayek)",
                    HI_G, "कुछ भी पूछिए या बोलिए…", "🎙️ सुन रहा हूँ… बोलिए", "आपका AI मित्र • ऑनलाइन", "लिख रहा है…"),
            new Lang("sat", "ᱥᱟᱱᱛᱟᱲᱤ (Santali)", "hi-IN", "hi-IN", "Santali (Ol Chiki or the script the user writes in)",
                    HI_G, "कुछ भी पूछिए या बोलिए…", "🎙️ सुन रहा हूँ… बोलिए", "आपका AI मित्र • ऑनलाइन", "लिख रहा है…"),
            new Lang("en", "English", "en-IN", "en-IN", "simple Indian English",
                    "Namaste, I'm Mitra, your friend. Ask me anything or tell me to do something on your phone.",
                    "Ask or say anything…", "🎙️ Listening… speak now", "Your AI friend • Online", "Typing…"),
    };

    static Lang get(String id) {
        if (id != null) for (Lang l : ALL) if (l.id.equals(id)) return l;
        return ALL[0];
    }

    /** Extra system-prompt line for the chosen language (null for Auto). */
    String aiInstruction() {
        if (aiName == null) return null;
        return "यूज़र ने सेटिंग्स में भाषा चुनी है: " + aiName + "। हमेशा इसी भाषा और लिपि में जवाब दो (जब तक यूज़र खुद दूसरी भाषा में जवाब न माँगे)। "
                + "Always reply in " + aiName + ".";
    }

    /** TTS locale for a reply, picked from the script it is written in (so Auto mode speaks Tamil text in a Tamil voice). */
    static String ttsForText(String text, Lang chosen) {
        int[] count = new int[12];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0900 && c <= 0x097F) count[0]++;
            else if (c >= 0x0980 && c <= 0x09FF) count[1]++;
            else if (c >= 0x0A00 && c <= 0x0A7F) count[2]++;
            else if (c >= 0x0A80 && c <= 0x0AFF) count[3]++;
            else if (c >= 0x0B00 && c <= 0x0B7F) count[4]++;
            else if (c >= 0x0B80 && c <= 0x0BFF) count[5]++;
            else if (c >= 0x0C00 && c <= 0x0C7F) count[6]++;
            else if (c >= 0x0C80 && c <= 0x0CFF) count[7]++;
            else if (c >= 0x0D00 && c <= 0x0D7F) count[8]++;
            else if (c >= 0x0600 && c <= 0x06FF) count[9]++;
            else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) count[10]++;
        }
        int best = 0;
        for (int i = 1; i < 11; i++) if (count[i] > count[best]) best = i;
        if (count[best] == 0) return chosen.tts;
        switch (best) {
            case 0: return chosen.tts.startsWith("mr") || chosen.tts.startsWith("ne") ? chosen.tts : "hi-IN";
            case 1: return "as".equals(chosen.id) ? "as-IN" : "bn-IN";
            case 2: return "pa-IN";
            case 3: return "gu-IN";
            case 4: return "or-IN";
            case 5: return "ta-IN";
            case 6: return "te-IN";
            case 7: return "kn-IN";
            case 8: return "ml-IN";
            case 9: return "ur-IN";
            default: // Latin: Hinglish reads fine with the Hindi voice; English mode uses the English voice
                return "en".equals(chosen.id) ? "en-IN" : (count[0] > 0 ? "hi-IN" : chosen.id.equals("auto") || chosen.tts.equals("hi-IN") ? "hi-IN" : "en-IN");
        }
    }
}

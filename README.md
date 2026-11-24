# मित्र (Mitra) – आपका AI मित्र
Hindi/Hinglish AI assistant app for Android by **PlayRang Technologies** (package `com.playrang.rangstudio`, **v3.0** — replaces the old Rang Studio video editor).

## Features
- **🎙️ Live बात (new in v3.0)** — ChatGPT-voice-style full-screen conversation with a big animated orb: just talk, Mitra answers in a natural voice, and you can interrupt it any time (barge-in). Uses the **Gemini Live API** (`gemini-3.8-live`, falling back to `gemini-3.1-flash-live-preview` / `gemini-2.5-flash-native-audio-preview-12-2025`) over a WebSocket with 16 kHz mic audio up and 24 kHz speech down, echo cancellation + noise suppression, live transcripts, Google Search grounding for current info, and **function calling** for phone tasks (call / WhatsApp / SMS by contact name, YouTube, open app, Google search, alarm, timer, maps, torch, camera, share, time). Needs the user's own free Gemini API key (Settings → AI इंजन; get one at aistudio.google.com). Without a key the same screen runs a fast turn-based loop (speech recognition → AI → speech → listens again). Mic mute + close buttons; choice of 5 Live voices.
- Saying **“मित्र”** (Always listen) opens Live mode when a Gemini key is set, otherwise the small voice screen.
- **Faster chat**: with a Gemini key replies stream (`streamGenerateContent`, SSE) and are spoken sentence by sentence while still being written; shorter timeouts on the free fallbacks.
- बोलकर (🎤, hi-IN speech recognition) या लिखकर कोई भी सवाल पूछें — जवाब चैट में दिखता है और हिंदी आवाज़ (TextToSpeech) में सुनाई देता है (🔊/🔇 toggle).
- AI: free Pollinations endpoint by default (≈1 reply / 45 s; the app shows a countdown and retries automatically). Optional Google Gemini API key in Settings for faster replies.
- Phone actions (local Hindi/Hinglish/English commands + AI `[[ACTION:...]]` tags): YouTube पर वीडियो चलाना, ऐप/वेबसाइट खोलना, Google search, नंबर डायल, WhatsApp संदेश, अलार्म/टाइमर, नक्शा, कैमरा, टॉर्च, शेयर, समय/तारीख.
- Fully native Android UI (no WebView, no remote website), pure Java, no extra libraries for Live (own tiny WebSocket client). No AccessibilityService; Mitra only opens apps with standard intents. No API key is stored in the code or repo.

## Build
Every push to `main` runs `.github/workflows/build-apk.yml`, which builds a **signed release APK** (`Mitra.apk`) and publishes it as a GitHub Release tagged `mitra-v3.0-build-N`.
Signing uses repository secrets `KEYSTORE_B64` (base64 PKCS12) and `KEYSTORE_PASS` (alias `mitra`). The keystore is never committed — keep a safe backup; every future update must be signed with the same key.

> Users who have the old Rang Studio (v1, debug-signed) installed must uninstall it before installing Mitra, because the signing key changed.

Privacy policy: https://mannukumarbank222-cmd.github.io/rang-studio/privacy-policy-mitra.html (source in `docs/`).

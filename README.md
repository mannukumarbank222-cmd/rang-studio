# मित्र (Mitra) – आपका AI मित्र
Hindi/Hinglish AI assistant app for Android by **PlayRang Technologies** (package `com.playrang.rangstudio`, v2.0 — replaces the old Rang Studio video editor).

## Features
- बोलकर (🎤, hi-IN speech recognition) या लिखकर कोई भी सवाल पूछें — जवाब चैट में दिखता है और हिंदी आवाज़ (TextToSpeech) में सुनाई देता है (🔊/🔇 toggle).
- AI: free Pollinations endpoint by default (≈1 reply / 45 s; the app shows a countdown and retries automatically). Optional Google Gemini API key in Settings for faster replies.
- Phone actions (local Hindi/Hinglish/English commands + AI `[[ACTION:...]]` tags): YouTube पर वीडियो चलाना, ऐप/वेबसाइट खोलना, Google search, नंबर डायल, WhatsApp संदेश, अलार्म/टाइमर, नक्शा, कैमरा, टॉर्च, शेयर, समय/तारीख.
- Fully native Android UI (no WebView, no remote website). No AccessibilityService; Mitra only opens apps with standard intents.

## Build
Every push to `main` runs `.github/workflows/build-apk.yml`, which builds a **signed release APK** (`Mitra.apk`) and publishes it as a GitHub Release tagged `mitra-v2.0-build-N`.
Signing uses repository secrets `KEYSTORE_B64` (base64 PKCS12) and `KEYSTORE_PASS` (alias `mitra`). The keystore is never committed — keep a safe backup; every future update must be signed with the same key.

> Users who have the old Rang Studio (v1, debug-signed) installed must uninstall it before installing Mitra, because the signing key changed.

Privacy policy: https://mannukumarbank222-cmd.github.io/rang-studio/privacy-policy-mitra.html (source in `docs/`).

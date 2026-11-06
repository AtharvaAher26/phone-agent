# Phone Agent — Setup Guide

## 1. Open the project
1. Install **Android Studio** (developer.android.com/studio) if you haven't.
2. Android Studio → **Open** → select this `android/` folder.
3. Let Gradle sync finish (first sync downloads dependencies — needs internet, takes a few minutes). If Studio says the Gradle wrapper is missing, accept its offer to create it.

## 2. Run it on your phone
1. On the phone: Settings → About phone → tap **Build number** 7 times (unlocks Developer options).
2. Developer options → enable **USB debugging**.
3. Connect USB, in Android Studio pick your phone as the run target, press ▶ Run.
4. The app opens. Grant the microphone permission when asked.

## 3. Add your brain key (free)
1. Go to **console.groq.com** → API Keys → Create API Key. Copy it.
2. In the app tap **Set Groq API key**, paste, Save. (The key lives only on this phone — never in code, never on GitHub.)

## 4. First test script (in order)
1. Tap the mic → "What is the capital of Australia?" → it should speak the answer.
2. "Turn on the flashlight" → light on. "Turn off the flashlight."
3. "Set an alarm for 7 tomorrow morning" → alarm appears in Clock.
4. "Open YouTube" → YouTube opens.
5. "Call [a contact's name]" → it asks to Confirm first (that's the safety gate working), then dials.
6. "WhatsApp [name] I will be late" → Confirm → WhatsApp opens with the message typed; tap send.
7. Try Hindi: switch Language to Hindi → "माचिस..." no — "Torch चालू करो" is Hinglish; better: "फ्लैशलाइट चालू करो" may vary — STT quality varies; English first, then experiment.

## 5. Take the assistant's seat (Day 5 of the sprint)
Settings → Apps → Default apps → **Digital assistant app** → choose **Phone Agent**.
Then long-press Home (or swipe from a bottom corner) — your agent opens.
If your brand's settings hide it: search Settings for "default assistant" or "device assistance app".
Xiaomi: also set Phone Agent's battery mode to "No restrictions" or MIUI will kill it.

## Troubleshooting
- **"Brain error"** → key missing/wrong, or no internet. Re-paste the key.
- **"Network problem — speech recognition needs internet"** → expected on most phones without data; connect Wi-Fi.
- **App not listed as digital assistant** → all three services must be in the manifest (they are); try reinstalling the app, then check again.
- **Contact not found** → the name must exist in the phone's contacts with a phone number; say the name as saved.

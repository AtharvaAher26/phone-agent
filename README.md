# Phone Agent

**A voice AI assistant for the Android phones that lost theirs.**

When Google retired Assistant in favour of Gemini (Sep 2026), older and budget Androids were left with no working assistant. Phone Agent is a Jarvis-style voice agent built for exactly those phones: speak a command — in **English, Hindi, or Marathi** — and it calls, messages, sets alarms, navigates, and answers, using an LLM brain with a fixed toolbox instead of pattern-matched commands.

> Status: **v1 — in active development** (built in a 6-day sprint, Nov 6–11, 2026). Sideload-only for now.

## What it can do (v1 — 10 tools)

| Tool | Example |
|---|---|
| `make_call` | "Call Papa" |
| `send_whatsapp` | "WhatsApp Mom, I'll be late" |
| `send_sms` | "Text Rahul, meeting moved to 5" |
| `set_alarm` | "Set an alarm for 6 tomorrow" |
| `set_reminder` | "Remind me to submit the form in 2 hours" |
| `open_app` | "Open YouTube" |
| `flashlight` | "Turn on the flashlight" |
| `open_wifi_settings` | "Open Wi-Fi settings" |
| `play_media` | "Play Arijit Singh songs" |
| `navigate` | "Navigate to the railway station" |
| ask anything | "What is the capital of Australia?" |

Sensitive actions (call / SMS / WhatsApp) pass through a **confirmation gate** — the agent shows and speaks the exact action and waits for your approval. Nothing irreversible happens silently.

## How it works — a 5-part pipeline

```
Voice → [EARS] Speech-to-Text → [BRAIN] LLM tool-calling → Confirm gate
      → [HANDS] Android intents/APIs → [VOICE] Text-to-Speech result
                    (+ [CONTACTS] spoken name → phone number)
```

1. **Ears** (`SpeechInput.kt`) — Android SpeechRecognizer, `en-IN / hi-IN / mr-IN`.
2. **Brain** (`Brain.kt`) — an LLM (Groq API, free tier) receives the sentence plus a fixed 10-tool toolbox and replies with one structured tool call, or a spoken answer, or a clarifying question. Tool calling, not free chat — the output must be machine-executable.
3. **Contacts** (`ContactResolver.kt`) — maps spoken names to numbers; multiple matches → the agent asks, never guesses.
4. **Confirm gate** (`MainActivity.kt`) — human approval before any call or message.
5. **Hands** (`ActionExecutor.kt`) — Android intents wherever they exist (Clock, Maps, YouTube, wa.me), direct APIs where they don't (flashlight torch, SmsManager).
6. **Voice** (`Speaker.kt`) — TextToSpeech reports every result and every failure, in your language.

**The assistant seat:** the app registers a `VoiceInteractionService` (+ session and recognition services), so it can be set as the phone's **default digital assistant** and summoned with long-press Home — a replacement assistant, not just an app.

## Setup

See [SETUP.md](SETUP.md) — open in Android Studio, run on a phone (Android 8.0+), paste your own free Groq API key in the app's settings. The key lives only on the phone, never in code.

## Honest limitations (v1)

- Needs internet (speech recognition and the LLM are cloud calls on most phones).
- No wake word — summon by long-press Home or open the app.
- WhatsApp opens the chat with your message pre-filled (wa.me); one tap sends.
- It does not read or drive other apps' screens — that's the v2 Accessibility layer.

## Roadmap

- **v2:** Accessibility-service screen driving for apps without intent APIs; offline STT fallback (Vosk).
- **v3:** General multi-step, any-app task agent.

## Tech

Kotlin · Android Views · SpeechRecognizer / TextToSpeech · Groq (OpenAI-compatible) tool calling · OkHttp · minSdk 26

## License

MIT — see [LICENSE](LICENSE).

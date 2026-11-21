# Contributing to Phone Agent

Phone Agent is a Jarvis-style voice assistant for Android phones that
lost theirs when Google retired Assistant for Gemini (Sep 2026). It
takes a spoken command in English, Hindi, or Marathi, calls an LLM
with a fixed 10-tool toolbox, and executes the result — with a human
confirmation gate before anything irreversible (calls, SMS, WhatsApp).

## Setup

- **Android Studio** (developer.android.com/studio), with an Android
  8.0+ phone for testing (minSdk 26).
- Open the **repo root** in Android Studio and let Gradle sync finish
  (first sync downloads dependencies — needs internet, takes a few
  minutes).
- Kotlin · Android Views · SpeechRecognizer / TextToSpeech · Groq
  (OpenAI-compatible) tool calling · OkHttp.

## Running it

See [SETUP.md](SETUP.md) for the full guide. The short version:

1. On the phone, enable Developer options (tap Build number 7 times)
   and USB debugging; connect and press ▶ Run in Android Studio.
2. Grant the microphone permission when asked.
3. Add your brain key: create a free key at console.groq.com, then in
   the app tap **Set Groq API key** and paste it. **The key lives only
   on the phone — never in code, never in a commit, never on GitHub.**
4. Run the first test script in SETUP.md §4 (ask a question, toggle
   the flashlight, set an alarm, open YouTube, then a gated call).
5. To take the assistant's seat: Settings → Apps → Default apps →
   Digital assistant app → Phone Agent; then long-press Home.

## Ground rules

These are load-bearing — a change that breaks one is a regression:

- **The confirm gate is sacred.** Calls, SMS, and WhatsApp always
  show and speak the exact action and wait for approval. Nothing
  irreversible happens silently. Never add a tool that bypasses it.
- **Tool calling, not free chat.** The LLM gets a fixed toolbox and
  must reply with one structured tool call, a spoken answer, or a
  clarifying question. Don't let the brain free-form its way into
  actions.
- **Never guess a contact.** A spoken name maps to a number via
  `ContactResolver`; multiple matches → the agent asks, never picks.
- **Speak every result and every failure**, in the user's language.
  A silent failure is a bug.
- **No wake word in v1** — summon by long-press Home or open the app.
  That's a roadmap item, not a missing piece to bolt on quietly.
- **No keys in the repo.** The Groq key is entered on the phone only.
  If you add any provider, keep its key out of code the same way.

## Tests

v1 has no automated test suite yet — verification is the manual test
script in SETUP.md §4, run on a real phone. If you add a unit-testable
piece (e.g. contact resolution, tool-call parsing), add a test with
it and say how to run it in your PR.

## Pull requests

- One focused change per PR, with a clear title and a short
  description of what changed and why.
- Say which steps of the SETUP.md §4 test script you ran on a real
  phone, and in which language(s).
- Keep the README's status and "Honest limitations" sections honest:
  if a feature lands, say so; if something is deferred, say so.
- Don't commit secrets, API keys, or anything from a real phone
  (contacts, messages, call history).

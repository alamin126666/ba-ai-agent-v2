# BA AI AGENT v2.0 — Claude Code style, on your phone

A mobile AI coding agent like Claude Code, built in **Kotlin + Jetpack Compose**.
Chat + Agent Mode + Terminal + Skills + Design, all on the phone, no VPS.

- **App:** BA AI AGENT 2.0.0, package `com.ba.aiagent`
- **Language:** Kotlin only. No Flutter, no Dart.
- **Build:** `./gradlew testDebugUnitTest lint assembleRelease`
  (needs JDK 17 + Android SDK 35; GitHub Actions builds the Release APK)

## Features

- **Provider Hub** — OpenRouter, Meta Muse, OpenAI, Anthropic Claude,
  Google Gemini, Groq, DeepSeek, Mistral, xAI Grok, Together AI.
  Each provider has its own key (EncryptedSharedPreferences only),
  Test Connection, and a live model list with Free/Paid badges.
- **Meta Muse** — via Meta Model API (`https://api.meta.ai/v1`),
  models `muse-spark-1.3` and `muse-wonder-1.0`.
  Get a key at https://dev.meta.ai → API keys → Create API key.
  The key starts with `LLM_` and is shown only once — copy it right away.
  Add a payment method in Billing first, otherwise calls will not run.
  The personal Muse agent itself has no token; this runs the Muse *models*
  by API, without the personal agent's memory or history.
- **Agent Mode** — Chat / Agent / Plan. Tool loop, max 25 steps.
  Tools: list/read/write/create/edit (patch)/delete/search files,
  run_command, git_status. Every edit shows a diff (old red / new green)
  and needs Approve. Dangerous commands are blocked.
- **Terminal** — BA Terminal, our own terminal built into the app
  (no other app, no VPS, no root): one persistent local shell on
  the phone. The agent and the Terminal tab share the session, so
  `cd` sticks between commands. Dangerous commands are blocked.
- **Skills** — built-in `/plan /fix /review /refactor /explain /test
  /commit /new /docs /ui`, auto-loaded when relevant, plus a Skill Store:
  install a SKILL.md from GitHub or create your own custom skill.
- **Design** — DESIGN.md themes (Linear, Stripe, Vercel, Notion, Apple,
  Supabase, Claude, Figma, GitHub, Spotify) + Prototype mode:
  prompt → self-contained HTML preview in the app, save as `prototype.html`.
- **Files** — internal workspace + pick a real project folder (SAF).
  `..` paths are always rejected.

## Rules

- NO demo mode, NO mock replies, NO hard-coded API key anywhere.
  The app does nothing useful without your own key.
- Never paste an API key in chat. Enter it in the app:
  Settings → Provider Hub → your provider → Save Key.

## Run

```bash
./gradlew installDebug        # install on a connected phone / emulator
./gradlew testDebugUnitTest lint assembleRelease
```

Release APK: `app/build/outputs/apk/release/app-release.apk`
(also built by GitHub Actions on push to `main`).

## Project structure

```
app/src/main/java/com/ba/aiagent/
  MainActivity.kt, BaAiAgentApp.kt
  data/api/       Providers.kt, AiApiClient.kt   # all providers, SSE streaming
  data/local/     AppDatabase.kt (Room), SecureKeyStore.kt,
                  SettingsStore.kt (DataStore), WorkspaceStore.kt,
                  SkillStore.kt, DesignStore.kt
  domain/agent/   AgentEngine.kt (25-step tool loop), AgentTools.kt
  domain/model/   Models.kt
  ui/             theme/, navigation/, home/, chat/, settings/,
                  models/, files/, terminal/, skills/, design/, components/
app/src/test/java/com/ba/aiagent/  AgentUnitTests.kt (7 tests)
```

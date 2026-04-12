# FakeOCAT

**Languages:** [中文](README.md) | [日本語](README.ja.md) | English (current)

FakeOCAT is an Android language-learning chat app created as a personal, hobby-driven recreation of [OCAT](https://ocat.app/).

The motivation is simple: I did not want to pay an extra software subscription, so I built an app with similar functionality myself. The core design of this project is BYOK (Bring Your Own Key), which means you must provide your own API key to use it.

✨ Core Features

· Multi-model support: built-in integrations for 13 AI providers, with free switching.  
· Language-learning assistance: pronunciation support to improve speaking and listening experience.  
· Content management: complete chat history and key-content bookmarking features.

⚠️ Disclaimer & Feedback

Although the app includes support code for 13 providers, I currently only have a Gemini API key due to personal constraints. Therefore, only Gemini has been fully tested and is guaranteed to work in this project. The other 12 provider integrations are unverified implementations. If you encounter bugs when using other APIs, issues and PRs are very welcome.

## Features

- Multi-provider API integration (OpenAI, Anthropic, Gemini, Grok, DeepSeek, Doubao, Qwen, Hunyuan, ERNIE, Zhipu, Kimi, MiniMax, etc.)
- Three learning modes: `HowToSay`, `WhatMeans`, `FreeChat`
- Markdown message rendering with clickable pronunciation chips
- Text-to-Speech (TTS)
- Decoupled history/bookmark storage (clearing history does not remove bookmarks)
- Theme and app-language switching

## Requirements

- Android Studio (latest stable recommended)
- JDK 17+
- Android SDK (managed by Android Studio)

## Quick Start

1. Clone the repository and open it in Android Studio.
2. Wait for Gradle sync.
3. Run the `app` module on a device or emulator.
4. In Settings, choose a provider and set your API key.

Optional CLI build:

```powershell
cd C:\Users\noway\AndroidStudioProjects\FakeOCAT
.\gradlew.bat assembleDebug
```

## Tests

```powershell
cd C:\Users\noway\AndroidStudioProjects\FakeOCAT
.\gradlew.bat testDebugUnitTest
```

## Project Structure

- `app/src/main/java/com/example/fakeocat/ui/`: screens and navigation
- `app/src/main/java/com/example/fakeocat/ui/viewmodel/`: state and orchestration
- `app/src/main/java/com/example/fakeocat/network/`: LLM/TTS networking
- `app/src/main/java/com/example/fakeocat/data/`: preferences and database
- `docs/`: design and integration documents

## Open Source Notes

- Do not commit secrets (API keys, signing files)
- `.gitignore` is configured to exclude local/generated artifacts

## Multilingual Maintenance

- Canonical document: `README.md` (Chinese)
- Additional language docs: `README.ja.md`, `README.en.md`
- Suggested workflow: update Chinese first, then sync Japanese and English
- If translation lags behind, treat the Chinese README as source of truth and submit a PR update

## License

This project is licensed under the [MIT License](LICENSE).


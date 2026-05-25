# FakeOCAT

**Languages:** [中文](README.md) | [日本語](README.ja.md) | English (current)

FakeOCAT is an AI-powered oral English tutor Android app, inspired by [OCAT](https://ocat.app/) and developed as a personal hobby project. The core philosophy is BYOK (Bring Your Own Key) — users only need to supply their own API key to unlock all features, with no subscription fees required.

---

## 📱 Features

- **🌐 22 UI Languages** — The app interface supports Simplified Chinese, English, 日本語, 한국어, Français, Deutsch, Español, Português, Italiano, Nederlands, Svenska, Polski, Čeština, Русский, العربية, हिन्दी, Bahasa Indonesia, עברית, Ελληνικά, Türkçe, Tiếng Việt, and ไทย. Switching takes effect instantly.
- **🤖 12 AI Providers** — Built-in API adapters for OpenAI, Anthropic (Claude), Google (Gemini), Grok (xAI), DeepSeek, Tongyi Qianwen, Tencent Hunyuan, Wenxin Yiyan, Zhipu (GLM), Kimi (Moonshot), MiniMax, and Baichuan. Switch freely at any time.
- **📖 Three Learning Modes** — "How to Say in X" (CN→Foreign translation), "What Does It Mean" (Foreign→CN explanation), and "Free Chat" (open-ended conversation), covering both input and output practice.
- **🔊 TTS Text-to-Speech** — AI replies can be played back as speech. Tap any sentence to hear it pronounced, enhancing listening and speaking skills.
- **🔖 Bookmarks** — One-tap bookmarking of important conversation snippets. Bookmarks and chat history are stored independently — clearing history never removes bookmarked content.
- **📜 Chat History** — Full conversation history with timestamp-based browsing and session context restoration.
- **🎨 Material3 Theme** — Built with Jetpack Compose Material3, supporting light/dark theme switching.

---

## 🛠 Tech Stack

| Category | Technology |
|----------|-------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material3 |
| Navigation | Navigation Compose |
| Networking | OkHttp + SSE (streaming responses) |
| Persistence | DataStore (preferences) + SQLite (Room-style) |
| Async | Kotlin Coroutines + Flow |
| Testing | JUnit + MockK + Turbine + MockWebServer |
| Build | Gradle (Kotlin DSL) + Version Catalog |

---

## 📸 Screenshots



---

## 🚀 Build Guide

### Requirements

- **Android Studio** — Latest stable recommended (Ladybug or newer)
- **JDK 17+** — The project has `gradle.properties` configured to point to Eclipse Adoptium JDK 25; adjust or remove this path for your local environment
- **Android SDK** — Managed automatically by Android Studio

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/your-username/FakeOCAT.git

# 2. Open the project directory in Android Studio and wait for Gradle sync

# 3. Connect a device or launch an emulator, then click Run

# 4. On first launch, go to Settings → select an AI provider → enter your API key
```

### Command-Line Build

```bash
# Debug build
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

---

## 📁 Project Structure

```
FakeOCAT/
├── app/src/main/java/com/example/fakeocat/
│   ├── ui/                        # Compose UI layer
│   │   ├── screens/               # Screens: Chat / Bookmarks / History / Settings
│   │   ├── components/            # Reusable components: MarkdownMessage etc.
│   │   ├── viewmodel/             # ViewModels + business orchestration
│   │   └── theme/                 # Material3 theme configuration
│   ├── network/                   # Network layer: LLM client / TTS / SSE streaming parsers
│   └── data/                      # Data layer: DataStore preferences / SQLite database
├── app/src/main/res/              # Resources (includes strings.xml for 22 languages)
├── app/src/test/                  # Unit tests
├── app/src/androidTest/           # Instrumented tests (Android device tests)
└── docs/                          # Design and integration documents
```

---

## ⚠️ Disclaimer

Although the app includes integration code for 12 providers, due to personal constraints only **Gemini** has been fully tested and is guaranteed to work. The other 11 provider adapters are unverified implementations written without access to actual API keys. If you encounter issues with other providers, issues and pull requests are welcome.

---

## 📄 License

This project is open-sourced under the [MIT License](LICENSE).

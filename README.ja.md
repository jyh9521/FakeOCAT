# FakeOCAT

**言語 / Languages:** [中文](README.md) | 日本語（現在） | [English](README.en.md)

FakeOCAT は、[OCAT](https://ocat.app/) にインスパイアされた AI 英会話チューター Android アプリです。個人の趣味として開発されました。基本理念は「BYOK（Bring Your Own Key）」—— ユーザーは自分の API キーを用意するだけで、すべての機能を無料で利用でき、サブスクリプション料金は一切不要です。

---

## 📱 機能

- **🌐 22 言語 UI** — アプリのインターフェースは、簡体字中国語、English、日本語、한국어、Français、Deutsch、Español、Português、Italiano、Nederlands、Svenska、Polski、Čeština、Русский、العربية、हिन्दी、Bahasa Indonesia、עברית、Ελληνικά、Türkçe、Tiếng Việt、ไทย に対応。切り替えは即座に反映されます。
- **🤖 12 社の AI プロバイダー** — OpenAI、Anthropic (Claude)、Google (Gemini)、Grok (xAI)、DeepSeek、通義千問、Tencent Hunyuan、文心一言、智譜 (GLM)、Kimi (Moonshot)、MiniMax、Baichuan の API アダプターを内蔵。いつでも自由に切り替え可能。
- **📖 3 つの学習モード** — 「X語でどう言う？」（中→外翻訳）、「どういう意味？」（外→中説明）、「フリーチャット」（自由対話）で、インプットとアウトプットの両方をカバー。
- **🔊 TTS 音声読み上げ** — AI の返信を音声再生可能。任意の文をタップして発音を聞くことで、リスニングとスピーキングを強化。
- **🔖 ブックマーク** — 重要な会話の抜粋をワンタップで保存。ブックマークとチャット履歴は独立して保存され、履歴を消去してもブックマークは保持されます。
- **📜 チャット履歴** — タイムスタンプ付きの完全な会話履歴と、セッションコンテキストの復元に対応。
- **🎨 Material3 テーマ** — Jetpack Compose Material3 で構築され、ライト/ダークテーマの切り替えに対応。

---

## 🛠 技術スタック

| カテゴリ | 技術 |
|----------|------|
| 言語 | Kotlin |
| UI フレームワーク | Jetpack Compose + Material3 |
| ナビゲーション | Navigation Compose |
| ネットワーク | OkHttp + SSE（ストリーミング応答） |
| 永続化 | DataStore（設定） + SQLite（Room スタイル） |
| 非同期処理 | Kotlin Coroutines + Flow |
| テスト | JUnit + MockK + Turbine + MockWebServer |
| ビルド | Gradle (Kotlin DSL) + Version Catalog |

---

## 📸 スクリーンショット



---

## 🚀 ビルドガイド

### 必要環境

- **Android Studio** — 最新安定版推奨（Ladybug 以降）
- **JDK 17+** — プロジェクトの `gradle.properties` には Eclipse Adoptium JDK 25 へのパスが設定されています。ローカル環境に合わせて調整または削除してください
- **Android SDK** — Android Studio が自動管理

### クイックスタート

```bash
# 1. リポジトリをクローン
git clone https://github.com/your-username/FakeOCAT.git

# 2. Android Studio でプロジェクトディレクトリを開き、Gradle 同期を待つ

# 3. デバイスを接続またはエミュレーターを起動し、実行をクリック

# 4. 初回起動後、「設定」→ AI プロバイダーを選択 → API キーを入力
```

### コマンドラインビルド

```bash
# デバッグビルド
./gradlew assembleDebug

# ユニットテスト実行
./gradlew testDebugUnitTest
```

---

## 📁 プロジェクト構成

```
FakeOCAT/
├── app/src/main/java/com/example/fakeocat/
│   ├── ui/                        # Compose UI 層
│   │   ├── screens/               # 画面：Chat / Bookmarks / History / Settings
│   │   ├── components/            # 再利用可能コンポーネント：MarkdownMessage など
│   │   ├── viewmodel/             # ViewModel + ビジネスロジック
│   │   └── theme/                 # Material3 テーマ設定
│   ├── network/                   # ネットワーク層：LLM クライアント / TTS / SSE ストリーム解析
│   └── data/                      # データ層：DataStore 設定 / SQLite データベース
├── app/src/main/res/              # リソース（22 言語の strings.xml を含む）
├── app/src/test/                  # ユニットテスト
├── app/src/androidTest/           # インストルメント化テスト（Android デバイステスト）
└── docs/                          # 設計・統合ドキュメント
```

---

## ⚠️ 免責事項

アプリには 12 社のプロバイダー向けの統合コードが含まれていますが、個人開発の制約により、現在 **Gemini** のみが十分にテストされ、動作が保証されています。その他 11 社のプロバイダーアダプターは、実際の API キーにアクセスできない状態で実装された未検証のコードです。他のプロバイダーで問題が発生した場合は、Issue や Pull Request を歓迎します。

---

## 📄 ライセンス

本プロジェクトは [MIT License](LICENSE) で公開されています。

# FakeOCAT

**言語 / Languages:** [中文](README.md) | 日本語（現在） | [English](README.en.md)

FakeOCAT は、個人的な興味から [OCAT](https://ocat.app/) を再現して作った Android 向け語学学習チャットアプリです。

開発の動機はシンプルで、追加のサブスク費用を払いたくなかったため、近い機能を持つアプリを自作しました。本プロジェクトの中核は「BYOK（自前 API キー）」で、利用には各自の API キーが必要です。

✨ コア機能

・マルチモデル対応：13 社の AI サービス API を内蔵し、自由に切り替え可能。  
・語学学習サポート：発音補助を提供し、会話・リスニング体験を向上。  
・コンテンツ管理：チャット履歴と重要内容のブックマーク機能を提供。

⚠️ 免責事項とフィードバック

アプリ内では 13 社のサービスをサポートしていますが、個人開発の都合により、現在は Gemini の API しか手元にありません。そのため、本プロジェクトは Gemini のみ十分に検証済みで、動作を保証しています。ほか 12 社分のコードは未検証の実装です。ほかの API 利用時に不具合を見つけた場合は、Issue や PR での報告を歓迎します。

## 主な機能

- 複数プロバイダー API 連携（OpenAI、Anthropic、Gemini、Grok、DeepSeek、豆包、Qwen、混元、文心、智譜、Kimi、MiniMax など）
- 3つの学習モード：`HowToSay`、`WhatMeans`、`FreeChat`
- Markdown メッセージ表示とクリック可能な発音ボタン
- TTS（読み上げ）
- 履歴とブックマークの分離保存（履歴削除でブックマークは消えない）
- テーマ切替・アプリ言語切替

## 必要環境

- Android Studio（安定版推奨）
- JDK 17+
- Android SDK（Android Studio 管理）

## セットアップ

1. リポジトリを取得し、Android Studio で開く
2. Gradle 同期を待つ
3. 実機またはエミュレーターで `app` を実行
4. 設定画面でプロバイダー選択と API Key を入力

任意の CLI ビルド：

```powershell
cd C:\Users\noway\AndroidStudioProjects\FakeOCAT
.\gradlew.bat assembleDebug
```

## テスト

```powershell
cd C:\Users\noway\AndroidStudioProjects\FakeOCAT
.\gradlew.bat testDebugUnitTest
```

## ディレクトリ

- `app/src/main/java/com/example/fakeocat/ui/`：画面・ナビゲーション
- `app/src/main/java/com/example/fakeocat/ui/viewmodel/`：状態管理と業務ロジック
- `app/src/main/java/com/example/fakeocat/network/`：LLM/TTS 通信層
- `app/src/main/java/com/example/fakeocat/data/`：設定・DB 層
- `docs/`：設計/統合ドキュメント

## OSS 公開メモ

- API Key や署名ファイルなどの機密情報はコミットしない
- `.gitignore` でローカル/生成物は除外済み

## 多言語ドキュメント運用

- 既定ドキュメント：`README.md`（中国語）
- 他言語ドキュメント：`README.ja.md`、`README.en.md`
- 推奨更新フロー：先に中国語版を更新し、その後に日本語版と英語版を同期
- 翻訳差分がある場合は、中国語版を正として PR で修正してください

## License

本プロジェクトは [MIT License](LICENSE) で公開しています。


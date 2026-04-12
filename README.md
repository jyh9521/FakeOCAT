# FakeOCAT

**语言 / Languages:** 中文（当前） | [日本語](README.ja.md) | [English](README.en.md)

FakeOCAT 是一款出于个人兴趣复刻自 [OCAT](https://ocat.app/) 的 Android 语言学习聊天应用。

开发初衷很简单：我不想再额外支付软件订阅费，所以自己动手做了一个功能相近的 APP。本项目核心主打“自带密钥 (BYOK)”，用户需自备 API 密钥方可使用。

✨ 核心特性

·多模型支持：内置 13 家 AI 服务商接口，支持自由切换。
·语言学习辅助：提供对话发音支持，提升口语与听力体验。
·内容管理：完善的聊天历史记录与重点内容收藏功能。

⚠️ 免责声明与反馈

虽然应用内集成了 13 家服务商的支持，但受限于个人条件，我目前只有 Gemini 的 API。因此，本项目目前仅对 Gemini 进行了完整测试并保证可用。其他 12 家服务商的代码属于“盲写”且未经测试，如果大家在调用其他 API 时遇到 bug，非常欢迎提交反馈或 PR！

## 功能

- 多服务商 API 接入（OpenAI、Anthropic、Gemini、Grok、DeepSeek、豆包、千问、混元、文心、智谱、Kimi、MiniMax 等）
- 三种学习模式：`HowToSay`、`WhatMeans`、`FreeChat`
- Markdown 消息渲染与可点击发音按钮
- 文本转语音（TTS）朗读
- 收藏与历史分离存储（清空历史不影响收藏）
- 主题切换与应用语言切换

## 环境要求

- Android Studio（建议最新稳定版）
- JDK 17+
- Android SDK（由 Android Studio 管理）

## 快速开始

1. 克隆项目并用 Android Studio 打开。
2. 等待 Gradle 同步完成。
3. 运行 `app` 到设备/模拟器。
4. 首次进入应用后，在设置页选择服务商并填写 API Key。

可选命令行构建：

```powershell
cd C:\Users\noway\AndroidStudioProjects\FakeOCAT
.\gradlew.bat assembleDebug
```

## 测试

```powershell
cd C:\Users\noway\AndroidStudioProjects\FakeOCAT
.\gradlew.bat testDebugUnitTest
```

## 目录结构

- `app/src/main/java/com/example/fakeocat/ui/`：界面与导航
- `app/src/main/java/com/example/fakeocat/ui/viewmodel/`：状态与业务编排
- `app/src/main/java/com/example/fakeocat/network/`：LLM/TTS 网络层
- `app/src/main/java/com/example/fakeocat/data/`：本地配置与数据库
- `docs/`：设计与集成文档

## 开源说明

- 提交前请确认敏感信息（API Key、签名文件）未入库
- 项目已提供 `.gitignore`，默认忽略本地与构建产物

## 多语言维护

- 默认文档：`README.md`（中文）
- 其他语言文档：`README.ja.md`、`README.en.md`
- 更新流程建议：先更新中文，再同步日文与英文，避免内容不一致
- 如果发现翻译滞后，请以中文版为准并提交修正 PR

## License

本项目采用 [MIT License](LICENSE)。


# FakeOCAT

**语言 / Languages:** 中文（当前） | [日本語](README.ja.md) | [English](README.en.md)

FakeOCAT 是一款 AI 口语私教 Android 应用，受 [OCAT](https://ocat.app/) 启发，基于个人兴趣独立开发。核心理念是"自带密钥 (BYOK)"——用户需自备 API 密钥即可免费使用全部功能，无需支付任何订阅费用。

---

## 📱 功能特色

- **🌐 22 种界面语言** — 应用 UI 覆盖简体中文、English、日本語、한국어、Français、Deutsch、Español、Português、Italiano、Nederlands、Svenska、Polski、Čeština、Русский、العربية、हिन्दी、Bahasa Indonesia、עברית、Ελληνικά、Türkçe、Tiếng Việt、ไทย，一键切换即时生效。
- **🤖 12 家 AI 服务商** — 内置 OpenAI、Anthropic (Claude)、Google (Gemini)、Grok (xAI)、DeepSeek、通义千问、腾讯混元、文心一言、智谱 (GLM)、Kimi (月之暗面)、MiniMax、百川等 API 适配，自由选择切换。
- **📖 三种学习模式** — "用 X 语怎么说"（中→外翻译）、"什么意思"（外→中解释）、"自由聊天"（开放式对话），覆盖输入输出双向练习。
- **🔊 TTS 语音朗读** — 支持 AI 回复的文本转语音播放，点击任意句子即可发音，提升听力与口语体验。
- **🔖 书签收藏** — 一键收藏重要对话片段，收藏与历史记录独立存储，清空聊天历史不影响已收藏内容。
- **📜 对话历史** — 完整保留历史会话记录，支持按时间回顾与恢复会话上下文。
- **🎨 Material3 主题** — 基于 Jetpack Compose Material3 构建，支持亮色/暗色主题切换。

---

## 🛠 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material3 |
| 导航 | Navigation Compose |
| 网络 | OkHttp + SSE（流式响应） |
| 持久化 | DataStore（偏好设置） + SQLite（Room 风格） |
| 异步 | Kotlin Coroutines + Flow |
| 测试 | JUnit + MockK + Turbine + MockWebServer |
| 构建 | Gradle (Kotlin DSL) + Version Catalog |

---

## 📸 截图



---

## 🚀 构建指南

### 环境要求

- **Android Studio** — 推荐最新稳定版（Ladybug 或更新）
- **JDK 17+** — 项目已在 `gradle.properties` 中配置指向 Eclipse Adoptium JDK 25 的路径，您可根据本地环境修改或移除该配置
- **Android SDK** — 由 Android Studio 自动管理

### 快速开始

```bash
# 1. 克隆项目
git clone https://github.com/your-username/FakeOCAT.git

# 2. 用 Android Studio 打开项目目录，等待 Gradle 同步完成

# 3. 连接设备或启动模拟器，点击运行

# 4. 首次启动后，进入「设置」→ 选择 AI 服务商 → 填入 API 密钥
```

### 命令行构建

```bash
# Debug 构建
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest
```

---

## 📁 项目结构

```
FakeOCAT/
├── app/src/main/java/com/example/fakeocat/
│   ├── ui/                        # Compose UI 层
│   │   ├── screens/               # 页面：Chat / Bookmarks / History / Settings
│   │   ├── components/            # 可复用组件：MarkdownMessage 等
│   │   ├── viewmodel/             # ViewModel + 业务编排
│   │   └── theme/                 # Material3 主题配置
│   ├── network/                   # 网络层：LLM 客户端 / TTS / SSE 流式解析
│   └── data/                      # 数据层：DataStore 偏好 / SQLite 数据库
├── app/src/main/res/              # 资源文件（含 22 种语言的 strings.xml）
├── app/src/test/                  # 单元测试
├── app/src/androidTest/           # 仪表化测试（Android 设备测试）
└── docs/                          # 设计与集成文档
```

---

## ⚠️ 免责声明

虽然应用内集成了 12 家服务商的代码，但受限于个人条件，目前仅 **Gemini** 经过了完整测试并确保可用。其他 11 家服务商的适配代码为"盲写"实现，未经实际 API 验证。如在使用其他服务商时遇到问题，欢迎提交 Issue 或 Pull Request。

---

## 📄 许可

本项目采用 [MIT License](LICENSE) 开源。

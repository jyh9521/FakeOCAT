# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0] - 2026-05-23

### Added
- 完整实现三种学习模式：用 X 语怎么说（HowToSay）、什么意思（WhatMeans）、自由聊天（FreeChat）。
- 集成 12 家 AI 服务商 API：OpenAI、Anthropic (Claude)、Google (Gemini)、Grok (xAI)、DeepSeek、通义千问、腾讯混元、文心一言、智谱 (GLM)、Kimi、MiniMax、百川。
- 支持 22 种界面语言，一键切换即时生效，覆盖简体中文、English、日本語、한국어、Français、Deutsch、Español、Português、Italiano、Nederlands、Svenska、Polski、Čeština、Русский、العربية、हिन्दी、Bahasa Indonesia、עברית、Ελληνικά、Türkçe、Tiếng Việt、ไทย。
- TTS 文本转语音播放，支持句子级点击发音。
- 书签收藏功能，独立于历史记录存储，清空历史不影响已收藏内容。
- 对话历史记录，支持按时间回顾与恢复会话上下文。
- Material3 亮色/暗色主题切换。
- OkHttp SSE 流式响应，支持各家服务商差异化 SSE 解析。
- 完整的数据持久化层：DataStore（偏好设置）+ SQLite（Room 风格 DAO）。
- 单元测试覆盖网络层与 ViewModel 层（JUnit + MockK + Turbine + MockWebServer）。

### Fixed
- 修复冷启动主线程阻塞问题：DNS 预解析异步化、TTS 懒初始化、数据库操作调度到 IO 线程、`reportFullyDrawn` 防 ANR。
- 修复应用语言切换不即时生效的问题。
- 修复 Gemini 模型 404 / 429 错误提示不明确的问题。
- 修复聊天界面与输入法联动导致的布局异常（底部区遮挡 / 黑色空隙）。

### Changed
- 完善 `.gitignore`，屏蔽 `.roo/`、`plans/`、`.geminiignore` 等开发工具目录与构建产物。
- 重写三语 README（中文 / English / 日本語），统一内容结构，补充技术栈、构建指南与项目结构说明。
- 移除无用样例测试文件，保留与业务相关的回归测试。
- 应用图标入口切换到新生成的 `icon` 资源组。

## [0.1.0] - 2026-05-19

### Added
- 新增多语言说明文档：`README.md`（中文）、`README.ja.md`（日文）、`README.en.md`（英文）。
- 新增 `LICENSE`，采用 MIT 协议。
- 新增三语 README 互相跳转链接。

### Changed
- 更新三语 README 的项目背景说明，统一包含 OCAT 来源与 BYOK 使用方式说明。
- 完善开源发布前说明与多语言文档维护约定。
- 应用图标入口切换到新生成的 `icon` 资源组（`@mipmap/icon` / `@mipmap/icon_round`）。
- 清理默认样例测试文件，保留与业务相关的回归测试。
- 清理可再生成构建产物与缓存目录，减少仓库噪音。
- 完善 `.gitignore`，忽略本地配置、IDE 文件、构建输出与敏感文件。

### Fixed
- 修复 `attachBaseContext` 早期调用导致的启动崩溃风险。
- 修复聊天界面与输入法联动导致的布局问题（黑色空隙 / 底部区遮挡）。
- 调整 Gemini 模型配置与错误处理逻辑，明确 404 与 429 错误提示。

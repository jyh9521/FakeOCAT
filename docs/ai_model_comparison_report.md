# AI 模型对比报告（已更新）

> 生成日期：2026-05-19（已根据用户反馈修正）
>
> 本报告对比了 [`AiProviderCatalog.kt`](../app/src/main/java/com/example/fakeocat/network/AiProviderCatalog.kt) 中配置的模型名称（共 12 个提供商，已移除字节豆包）。

---

## 对比总表

| # | 提供商 | 当前配置模型 | 用户指定/确认的最新模型 | 状态 |
|---|--------|------------|----------------------|------|
| 1 | **OpenAI** | `gpt-5.4-mini` | `gpt-5.4-mini` | ✅ 按用户指定保留 |
| 2 | **Anthropic** | `claude-haiku-4-5` | `claude-haiku-4-5` | ✅ 按用户指定保留 |
| 3 | **Google Gemini** | `gemini-2.5-flash`（原为 `gemini-3.1-flash-lite-preview`） | `gemini-2.5-flash` | ✅ 已修正 |
| 4 | **xAI (Grok)** | `grok-3-mini`（原为 `grok-4-1-fast`） | `grok-3-mini` | ✅ 已修正 |
| 5 | **小米 MiMO** | `mimo-v2-flash` | `mimo-v2-flash` | ✅ 保留 |
| 6 | **DeepSeek** | `deepseek-chat`（原为 `deepseek-v3.2`） | `deepseek-chat` | ✅ 已修正 |
| 7 | **阿里千问** | `qwen-mt-lite`（原为 `qwen-turbo-latest`） | `qwen-mt-lite` | ✅ 已修正 |
| 8 | **腾讯混元** | `hunyuan-lite` | `hunyuan-lite` | ✅ 保留 |
| 9 | **百度文心** | `ernie-speed-128k` | `ernie-speed-128k` | ✅ 保留 |
| 10 | **智谱AI** | `glm-4.7-flash` | `glm-4.7-flash` | ✅ 按用户指定保留 |
| 11 | **Kimi (Moonshot)** | `moonshot-v1-8k` | `moonshot-v1-8k` | ✅ 保留 |
| 12 | **MiniMax** | `minimax-m2.5-highspeed` | `minimax-m2.5-highspeed` | ✅ 保留 |

> ~~字节豆包（doubao-lite-128k）~~ — 已根据用户要求移除。

---

## 本次修改汇总

| 文件 | 修改内容 |
|------|---------|
| [`AiProviderCatalog.kt`](../app/src/main/java/com/example/fakeocat/network/AiProviderCatalog.kt) | ① Gemini: `gemini-3.1-flash-lite-preview` → `gemini-2.5-flash`；② Grok: `grok-4-1-fast` → `grok-3-mini`；③ DeepSeek: `deepseek-v3.2` → `deepseek-chat`；④ 阿里千问: `qwen-turbo-latest` → `qwen-mt-lite`；⑤ **移除字节豆包**（doubao 整个条目） |
| [`LlmClient.kt`](../app/src/main/java/com/example/fakeocat/network/LlmClient.kt) | 移除 `doubao` 在 `getBaseUrl()` 中的路由 |
| [`AiProviderCatalogTest.kt`](../app/src/test/java/com/example/fakeocat/network/AiProviderCatalogTest.kt) | 同步更新模型名称、移除 doubao 测试项、数量从 13 变为 12 |
| [`docs/ai_model_comparison_report.md`](docs/ai_model_comparison_report.md) | 重写对比报告，反映用户指定的最新模型 |

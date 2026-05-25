# API 集成说明（12 家服务商）

## 统一调用方式

- 统一入口：`LlmClient.streamChat(provider, apiKey, model, systemPrompt, userPrompt, baseUrlOverride)`
- UI/业务只关心：
  - `provider`（服务商 id）
  - `apiKey`（密钥）
  - `model`（模型名）
  - 输出：`Flow<String>`（标准化后的流式文本 chunk）

## 配置来源

- 运行时使用的配置清单：`AiProviderCatalog.providers`
- 服务商模型、认证方式与端点都在代码中统一维护，避免资源文件与实现分叉

目前的实现假设除 Gemini/Anthropic 外，其余服务商可通过 OpenAI 风格 `POST .../chat/completions` + `Authorization: Bearer <apiKey>` 进行流式 SSE 输出。

## 发送/停止按钮与取消机制

- 发送后立即切换为“停止”（带旋转加载图标），用户点击后取消当前生成
- 取消通过取消 Flow 收集协程触发，底层 SSE 会在 `awaitClose { eventSource.cancel() }` 中真正中断连接

## 超时与重试

- 超时：5 秒内未收到任何流式 token 判定为超时（首 token 超时，由 OkHttp 层实现）
- 重试：最多 3 次；若已收到部分 token，则不做重试（避免重复文本）


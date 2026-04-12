# 性能基准测试报告（模板）

说明：由于仓库不包含各服务商的真实密钥与测试账号，本仓库只能提供“可运行的测量点”和“采集口径”。真实 SLA/错误率/并发压测结果需在你本地填入密钥后执行采集。

## 采集口径

- 指标
  - TTFT（time-to-first-token）：从点击发送到收到第一个 token 的耗时（ms）
  - TPS（tokens/sec）：近似值，可用字符数/时间替代（移动端无需严格 token 化）
  - 完成耗时：从点击发送到流结束（ms）
  - 错误率：错误次数 / 总请求次数
  - 取消成功率：用户点击停止后连接成功中断的比例
- 场景
  - Wi-Fi / 4G / 弱网
  - 前台 / 后台切回
  - 并发（网络层允许并发；UI 侧默认单请求在途）

## 建议阈值（可按 SLA 调整）

- TTFT：P50 < 1500ms，P95 < 5000ms
- 错误率：< 1%
- 停止响应：点击停止后 < 500ms 不再追加文本

## 结果填报（示例表）

| Provider | Model | TTFT P50(ms) | TTFT P95(ms) | 完成 P50(ms) | 错误率 | 取消成功率 |
|---|---|---:|---:|---:|---:|---:|
| OpenAI | gpt-5-mini | - | - | - | - | - |
| Anthropic | claude-4.5-haiku | - | - | - | - | - |
| Gemini | gemini-3.1-flash-lite | - | - | - | - | - |
| Grok | grok-4.1-fast | - | - | - | - | - |
| 小米 mimo | mimo-v2-flash | - | - | - | - | - |
| DeepSeek | deepseek-chat | - | - | - | - | - |
| 字节豆包 | doubao-lite-128k | - | - | - | - | - |
| 阿里千问 | qwen-turbo-latest | - | - | - | - | - |
| 腾讯混元 | hunyuan-lite | - | - | - | - | - |
| 百度文心一言 | ernie-speed-128k | - | - | - | - | - |
| 智谱AI | glm-4-flash | - | - | - | - | - |
| Kimi | moonshot-v1-8k | - | - | - | - | - |
| MiniMax | abab6.5t-chat | - | - | - | - | - |


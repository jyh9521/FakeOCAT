# 历史记录与收藏解耦方案（SQLite）

## 目标

- 历史记录与收藏在数据库层面完全分离：历史仅存 `messages`，收藏仅存 `bookmarks`
- 清空历史不影响收藏（收藏数据不依赖历史表中的任何字段）
- 旧版本混合数据可安全迁移并保持数据完整性
- 上线失败可快速回滚（提供数据库文件级备份与恢复步骤）

## 新数据库结构

### messages（历史）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | 历史消息 ID |
| text | TEXT NOT NULL | 内容 |
| is_user | INTEGER NOT NULL | 是否用户消息（0/1） |
| timestamp | INTEGER NOT NULL | 消息时间戳 |
| mode | TEXT NOT NULL | 模式（HowToSay/WhatMeans/FreeChat） |

### bookmarks（收藏）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | 收藏记录 ID |
| source_message_id | INTEGER NULL | 指向历史消息（可为空，历史清除后置空） |
| text | TEXT NOT NULL | 收藏快照内容 |
| is_user | INTEGER NOT NULL | 是否用户消息（0/1） |
| message_timestamp | INTEGER NOT NULL | 被收藏消息原始时间戳 |
| mode | TEXT NOT NULL | 模式 |
| created_at | INTEGER NOT NULL | 收藏时间戳 |

设计要点：
- 收藏表存“快照”，不依赖历史表字段（历史清除/单条删除不会导致收藏丢失）
- `source_message_id` 外键 `ON DELETE SET NULL`，保证历史表删除不会级联删除收藏

## 关键行为变化（对用户无感）

- 聊天列表的收藏状态不再来自 `messages.is_bookmarked`，而是由 `bookmarks` 表是否存在对应 `source_message_id` 计算得出
- 收藏列表数据源从独立的 `bookmarks` 表读取，不再从历史列表过滤
- 清空历史仅删除 `messages`，不触碰 `bookmarks`

## 数据迁移（v1 -> v2）

- 应用内迁移在 `DatabaseHelper.onUpgrade()` 中执行，包含：
  - 从旧 `messages.is_bookmarked=1` 拆分写入 `bookmarks`
  - 重建 `messages` 表移除 `is_bookmarked` 字段并保留原 `id`

一致性校验建议（发布前/灰度期间）：
- `SELECT COUNT(*) FROM bookmarks` 应等于旧库中 `SELECT COUNT(*) FROM messages WHERE is_bookmarked=1`
- 抽样对比若干条收藏内容的 `text/is_user/timestamp/mode` 是否与旧表一致

## 回滚方案（5 分钟内）

本次迁移为破坏性 schema 变更（删除 `messages.is_bookmarked`），为保证快速回退，升级时会自动做数据库文件备份：
- 备份文件：`ocat_database_v1_backup.db`（位于应用数据库目录，与主库同目录）
- 触发时机：首次从 v1 升级到 v2 时

### 快速回滚步骤（开发/发布应急）

1. 停止应用进程（强制停止）
2. 使用备份文件覆盖当前数据库文件（恢复为 v1 数据）
3. 安装/切换到旧版本 APK（使用旧 schema 的版本）
4. 启动应用验证历史/收藏恢复

示例（调试环境，需对应包名权限）：
- `run-as <package> cp databases/ocat_database_v1_backup.db databases/ocat_database.db`

注意：
- 若已执行“清空历史”，回滚后历史也会被清空（这与用户行为一致）；收藏仍可通过备份恢复到迁移时刻的数据快照


# .mmr 备份格式（v1）

`.mmr` = **Meowmorize Memory Record**，是 UTF-8 编码的 JSON 文件。
扩展名只是约定，内容与 `.json` 完全一致，方便任何工具直接读。

## 顶层结构

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `format` | string | 是 | 固定为 `meowmorize.mmr`（读取端同时接受旧写法 `meowmorize`） |
| `version` | int | 是 | 当前为 `1`。读取端**只接受 ≤ 自身支持的版本** |
| `exportedAt` | string | 否 | ISO-8601 UTC，如 `2026-11-28T08:00:00Z` |
| `subjects` | array | 否 | 学科列表 |
| `items` | array | 否 | 任务列表（含记忆进度） |

## subjects[]

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `name` | string | 学科名，**导入时按名称匹配**；同名即复用，不新建 |
| `colorArgb` | int | ARGB 颜色，可为负数 |
| `sortOrder` | int | 排序 |
| `weight` | double | 学科权重，参与配额分配 |
| `examAt` | long | 考试时间（毫秒时间戳），可缺省 |

## items[]

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `topic` | string | 主题。与 `subjectName` 一起构成**去重键** |
| `content` | string | 背诵内容 |
| `answer` | string | 答案，可缺省（缺省即「只有内容」） |
| `source` | string | 来源 |
| `contentFormat` | string | `PLAIN` / `MARKDOWN`，缺省 `PLAIN` |
| `priority` | string | `LOW` / `NORMAL` / `HIGH` |
| `status` | string | `ACTIVE` / `ARCHIVED` |
| `estimatedMinutes` | int | 预计用时（分钟），导入时夹到 1–240 |
| `durationIsCustom` | bool | 是否用户手填过用时 |
| `createdAt` / `updatedAt` | long | 毫秒时间戳 |
| `hint` | string | 背诵提示 |
| `deadlineAt` | long | 截止时间，可缺省 |
| `subjectName` | string | 所属学科名；缺省或匹配不到则归入「未分类」 |
| `state` | object | 记忆进度，见下 |

### items[].state

| 字段 | 类型 | 缺省 |
| --- | --- | --- |
| `dueAt` | long | 当前时间（宁可早复习，也不产生「永不到期」的幽灵状态） |
| `stability` | double | 1.0，下限 0.1 |
| `difficulty` | double | 5.0，夹在 1–10 |
| `reps` / `lapses` | int | 0 |
| `phase` | string | `NEW` |
| `scheduledDays` | int | 0 |
| `leech` | bool | false |
| `lastReviewedAt` / `leechSince` | long | 可缺省 |
| `learningStep` / `sameDayReps` | int | 0 |

## 兼容性约定

1. **读取端必须允许字段缺失**，缺省值取上表。新增字段只能追加，不能改变已有字段语义。
2. **`version` 高于读取端支持值时直接拒绝**，并提示「请先升级应用」——
   不能读一半，否则会静默丢数据。
3. 导入是**合并**：同「`subjectName` + `topic`」视为同一条并跳过（计入 `skipped`）。
   需要覆盖时，先在应用内删除对应任务再导入。
4. 主键 `id` 不进入文件：导入端重新分配，学科按名称匹配。

## 不包含的内容

- **复习日志**（`review_logs`）：历史明细，体积大，且恢复排程只需 `state`。
- 设置项（主题、容量等）：属于设备偏好，换设备后通常希望重新选择。

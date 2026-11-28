<p align="center">
  <img src="docs/branding/icon-512.png" width="132" alt="Meowmorize" />
</p>

<h1 align="center">Meowmorize</h1>

<p align="center">
  <a href="https://github.com/feitianwuhu/Meowmorize/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/feitianwuhu/Meowmorize?sort=semver"></a>
  <a href="https://github.com/feitianwuhu/Meowmorize/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/feitianwuhu/Meowmorize/actions/workflows/ci.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/feitianwuhu/Meowmorize"></a>
  <img alt="Android" src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white">
</p>

> **它不是又一个待办清单，而是回答「今天该背多少」的排程器。**
> 你只管写内容，剩下的「今天背什么、背几条、什么时候再见」交给遗忘曲线与弹性规则。

大多数背诵工具把到期内容一次性推给你；一旦停几天，回来就是几百条待复习，
然后就没有然后了。Meowmorize 把「记忆量」和「人的承受力」放在同一条约束里：
**宁可顺延，也不超发**。

---

## 为什么是「今天背多少」

Meowmorize 想解决的不是「怎么让人背得更多」，而是「怎么让人**愿意继续背下去**」。
它用 FSRS 遗忘曲线决定**该复习什么**，用弹性容量规则决定**今天该复习多少**，
并把这套判断的依据直接摊开给用户看。

> 更完整的项目介绍（含算法细节与设计取舍）：**[docs/INTRO.md](docs/INTRO.md)**
> 需要发到微信的场景，用纯文本版：[docs/INTRO-wechat.txt](docs/INTRO-wechat.txt)

---

## 零、下载与安装

最新正式版见 GitHub Releases：`https://github.com/feitianwuhu/Meowmorize/releases/latest`

- 安装包：**Meowmorize.apk**（`applicationId = com.meowmorize.app`，minSdk 29 / Android 10+）
- 首次安装需要在系统里允许「安装未知来源应用」
- 校验：`sha256sum Meowmorize.apk` 应与 Release 页面给出的一致
- 签名：`CN=Meowmorize`，SHA-256 指纹见 Release 说明。**后续升级必须用同一密钥签名**，
  否则系统会拒绝覆盖安装（只能卸载重装，本地数据会丢）

调试版与正式版包名不同（`com.meowmorize.app.debug`），可以并存，互不影响数据。

---

## 一、设计原则

1. **算法结论必须可解释。** 每日计划会附带「为什么今天是这样安排的」说明，
   用户能看到「近 3 天完成率偏低 → 容量下调 25%」这类推导，而不是一个黑盒数字。
2. **容量是硬约束，宁可不排也不超发。** 任务不会因为「到期了」就无上限堆积；
   放不下的内容顺延到明天，记忆曲线会按真实的遗忘进度重算，不需要「补课」。
3. **用户随时可以否决算法。** 每天可以拖动「今天能做多久」，编辑内容时可以决定
   是否让算法重新排程，背诵用时也可以直接改——算法是助手，不是监工。

---

## 二、功能

| 场景 | 能力 |
| --- | --- |
| 今日 | 打开即生成当日计划；完成度环形进度；算法判定的负荷档位标签 + 容量上限；「为什么今天是这样」解释卡；「今天能做多久？」单滑杆调整（放不下的顺延，不超发） |
| 背诵 | **评分即揭晓**：屏上没有任何「显示答案 / 显示内容」按钮，先凭记忆判断、点四档熟练度后才自动展示答案（有答案）或内容（无答案）；该档变成「下一步」，可改选，再确认才落库，**出结果后按钮整体锁定**；「显示提示」在下半屏、整宽按钮，用过提示即禁用「轻松」；有截止日期时显示倒计时与生效保持率，反复遗忘时给出改写建议 |
| 日历 | 月视图：学科色点表示已排任务、空心点表示「按曲线推算会到期」；点选某天查看任务与完成情况，可直接进入背诵 |
| 任务库 | **长按进入多选**，批量归档 / 恢复 / 移动到学科 / 改优先级 / 删除；搜索（主题/内容/来源）、按学科筛选、显示归档；**6 种排序**：最近更新 / 最近创建 / 下次复习 / 主题名称（拼音）/ 预计用时 / 优先级；**删除任务**；**管理学科（可删除学科，任务转为未分类）** |
| 新增/编辑 | **固定「背诵内容 + 答案」两栏**（不再区分是否为填空题，答案可留空）；学科、主题、来源、优先级、**背诵提示**、**截止日期**，所有文本框均支持换行；背诵用时自动估算且可 **+/- 或直接输入**；内容支持 **Markdown 高级模式**（标题/列表/引用/两栏对照 + 快捷插入 + 预览）；**OCR 取字**：拍照 / 相册后会先进入**四点框选 + 透视矫正**（斜拍的书页摊平再识别），识别用自带中文模型，**不依赖 Google 服务**；另有粘贴文本；编辑时可选择「保持排程」或「重新学习」 |
| 设置 | **主页分组 + 搜索**：今日节奏 / 弹性规则 / 复习算法 / 外观 / 日历同步 / 检查更新 / **数据与备份（.mmr 导出与导入）** / 测试工具，逐项可搜（搜「保持率」「间隔」直接跳页）。外观含**三套风格**（柔角卡片 / 终端 / **磨砂玻璃**）、主题模式、**强调色**（预设五组主流配色 + 色相自调）；选磨砂玻璃时下方展开**每日壁纸**（Bing 每日一图、铺满/适应/拉伸/居中、模糊与遮罩、亮/暗磨砂、下载壁纸）；复习算法参数可一键恢复默认；弹性实验默认关；系统日历同步；时间快进（测试）；重排今日计划 |
| 更新 | 设置 → 检查更新：查 GitHub Releases 后**应用内下载 APK**（页内进度 + 通知栏进度），下载完唤起系统安装器由用户确认；未授权「安装未知应用」会引导去授权；失败可回退「在浏览器打开」。「启动时检查更新」开关（6 小时节流） |

首次启动会写入 3 个学科与 3 条明确标注「示例」的任务，方便直接体验完整流程。

---

## 三、整体架构

### 3.1 分层

```
┌──────────────────────────────────────────────────────────────┐
│  :app          Application / MainActivity / NavHost / 顶栏    │
├──────────────────────────────────────────────────────────────┤
│  :feature:*    today  calendar  library  editor  settings     │
│                Compose UI + HiltViewModel + UiState            │
├──────────────────────────────────────────────────────────────┤
│  :core:designsystem   三套风格主题、动效令牌、卡片/输入/导航    │
├──────────────────────────────────────────────────────────────┤
│  :core:domain   用例 / FSRS 排程 / 计划生成 / 仓库接口（纯 Kotlin）│
│  :core:model    领域模型（纯 Kotlin，零依赖）                   │
│  :core:common   时间抽象 TimeProvider、展示格式化               │
├──────────────────────────────────────────────────────────────┤
│  :core:data     仓库实现 / DataStore / 系统日历适配 / 可调时钟  │
│  :core:database Room 实体、DAO、迁移                           │
└──────────────────────────────────────────────────────────────┘
```

依赖方向严格单向：`feature → domain → model`，`data → domain/database`，`app` 只做装配。
domain 层不引用任何 Android / Hilt / Room 类型，因此排程、弹性规则、排序与同步窗口计算
都可以纯 JVM 单测（当前 76 个用例，见 `core/domain/src/test`）。

### 3.2 模块职责

| 模块 | 职责 | 关键文件 |
| --- | --- | --- |
| `:app` | Application 装配、启动引导、主题注入（强调色/磨砂明暗）、壁纸层、导航图、更新提示 | `MeowmorizeApp.kt`, `MainActivity.kt`, `WallpaperBackdrop.kt` |
| `:core:model` | `MemorizeItem`、`ReviewState`、`DailyPlan`、`MonthCalendar`、`TaskSortOrder` | `Calendar.kt`, `DailyPlan.kt` |
| `:core:domain` | 用例（生成计划/评分/保存/日历同步）、FSRS、弹性计划、排序、仓库接口 | `FsrsScheduler.kt`, `TodayPlanGenerator.kt`, `SyncUpcomingToCalendarUseCase.kt` |
| `:core:data` | 仓库实现、实体映射、DataStore、系统日历适配、可调时钟、**备份编解码（.mmr）**、OCR、Hilt 模块 | `PlanRepositoryImpl.kt`, `AndroidCalendarRepository.kt` |
| `:core:database` | Room 实体与 DAO、v1→v2 迁移、schema 导出 | `MeowmorizeDatabase.kt`, `MeowMigrations.kt` |
| `:core:designsystem` | 三套主题（柔角卡片 / 终端 / 磨砂玻璃）、强调色、动效令牌、卡片/标签/环进度/输入/底部弹层 | `Theme.kt`, `AccentPalette.kt`, `motion/MeowMotion.kt` |
| `:feature:today` | 今日计划、调整任务量弹层、背诵页 | `TodayScreen.kt`, `ReviewScreen.kt` |
| `:feature:calendar` | 月历网格、按日任务列表、预计到期预测 | `CalendarScreen.kt`, `CalendarViewModel.kt` |
| `:feature:library` | 任务库检索、筛选、排序、归档 | `LibraryScreen.kt` |
| `:feature:editor` | 新增/编辑、用时覆盖、排程更新策略 | `EditorScreen.kt` |
| `:feature:settings` | 设置主页（分组 + 搜索）、各二级页面、壁纸与磨砂、检查更新 | `SettingsScreen.kt`, `SettingsPages.kt` |

每个 feature 只依赖 `model + domain + common + designsystem`，不直接依赖 `data`，
仓库实现通过 Hilt 在 `:app` 的依赖图里注入——「换数据库/加网络同步/换日历实现」都不影响 UI。

### 3.3 单页面数据流

```
UI (Compose) ──事件──► ViewModel ──调用──► UseCase ──► Repository ──► Room / DataStore / CalendarProvider
     ▲                    │                                  │
     └──── StateFlow ◄────┴────────── Flow 订阅 ◄─────────────┘
```

---

## 四、数据模型

### 4.1 关系

```
subjects 1 ──── * items 1 ──── 1 review_states
                    │ 1
                    ├──── * review_logs          （只追加，记录每次评分与前后间隔）
                    ├──── 0..1 calendar_sync     （与系统日历事件的映射）
                    └──── * daily_plan_items * ──── 1 daily_plans（按日）
```

关键设计：**任务内容与记忆状态分表**。

- `items` 只存「要背什么」；
- `review_states` 只存「记得怎么样、何时该复习」；
- `daily_plan_items` 只存「今天打算背哪几条」；
- `review_logs` 只追加，今日完成度由日志推导，而不是写回计划表。

因此：编辑任务不会破坏进度，重新生成计划不会弄丢「已完成」，
后续做统计/参数拟合也只是读日志。

### 4.2 表结构（v5）

| 表 | 主要字段 | 说明 |
| --- | --- | --- |
| `subjects` | `id, name(unique), colorArgb, sortOrder, weight, examAt` | 学科，名字唯一；`weight` 供配额分配，`examAt` 作为任务截止日期的兜底 |
| `items` | `id, subjectId(FK, SET_NULL), topic, content, hint, contentFormat, source, priority, status, estimatedMinutes, durationIsCustom, deadlineAt, clozeAnswer, createdAt, updatedAt, revision` | `revision` 每次内容变更自增；`durationIsCustom` 区分手填/估算；`deadlineAt` 为考试/汇报截止；`clozeAnswer` 是**答案**（字段名沿用历史），为空表示只有内容、没有需要单独揭晓的答案 |
| `review_states` | `itemId(PK, FK CASCADE), dueAt, stability, difficulty, reps, lapses, phase, lastReviewedAt, scheduledDays, leech, leechSince, learningStep, sameDayReps` | 与任务一对一；`leech` 为「反复遗忘」标记，只提示不改算法 |
| `review_logs` | `id, itemId(FK), reviewedAt, grade, intervalBefore, intervalAfter, stabilityAfter, phaseAfter, itemRevision, elapsedDays, scheduledDueAt, stabilityBefore, difficultyBefore, difficultyAfter, retrievability, hintUsed, wasOverdue, actualMs` | 只追加；后九个字段用于回溯与将来做参数校准 |
| `daily_plans` | `date(PK), generatedAt, capacityMinutes, plannedMinutes, loadLevel, targetRetention, elasticityFactor, backlogCount, newCount, notes, userAdjusted, baselinePlannedMinutes, baselineTaskCount, todayState` | `baseline*` 是当天首次生成的快照，重排不覆盖，保证完成率分母不被改写 |
| `daily_plan_items` | `(date, itemId)(PK), reason, orderIndex, minutes` | 计划内容，`reason` 区分逾期/到期/顺延/新学 |
| `calendar_sync` | `itemId(PK, FK CASCADE), eventId, syncedAt` | 与系统日历事件的映射，保证同步幂等 |

设置项未入 Room：`UserSettings` 存在 DataStore（键值、低频写、按 Flow 订阅）。
时间快进的偏移量单独存在 SharedPreferences：它必须在**第一次读时钟之前**同步可用，
DataStore 的异步 API 会带来「启动瞬间算错日期」的窗口，这是刻意的取舍。

### 4.3 迁移策略

- 版本号 `5`：`MIGRATION_1_2` 增加 `durationIsCustom` 与 `calendar_sync`；
  `MIGRATION_2_3` 增加 `hint` / `contentFormat`；`MIGRATION_3_4` 增加观测字段、
  截止日期、学科权重、leech 标记与计划快照。
- 新增列一律 `@ColumnInfo(defaultValue = …)` 且迁移 SQL 带同样的 `DEFAULT`，
  保证「新装」与「升级」得到完全一致的 schema——两者不一致时 Room 会在启动校验时报错。
- `schemas/` 下的 `1.json`~`5.json` 纳入版本控制，可据此生成迁移测试。
- 仅对「降级」保留兜底重建，正常升级永远走迁移。

---

## 五、核心算法

### 5.1 FSRS 遗忘曲线（`FsrsScheduler`）

每条内容维护稳定性 `S`（天）与难度 `D`（1~10）：

```
回忆概率      R(t, S) = (1 + 19/81 · t / S) ^ -0.5
反解间隔      I(S, r) = S / (19/81) · (r^(1/-0.5) − 1)
```

- `t = S` 时 `R = 0.9`，这正是「目标保持率 90%」的默认含义；
- 评分后更新 `S`：成功回忆按增长曲线提升，遗忘则按衰减公式回退；
- 评分同时更新 `D`，并向「轻松」的初值回归（`D0(Easy)=3.93`），
  避免长期难度偏高把增长项 `(11-D)` 一直压低；
- 点「忘了」不排到明天，而是按 `10m → 60m → 240m` 步进重练；
  当天超过「同日重练上限」后冷却 6 小时，`lapses` 仍如实累加。

**三处曾经算错、现已修正的地方**（详见 `SchedulingFixTest`）：

| 问题 | 旧行为 | 现行为 |
| --- | --- | --- |
| 同日重练的时间间隔 | 把 10 分钟当成 0.25 天（6 小时），R 被算低 | 用真实分钟换算，10 分钟 = 0.0069 天 |
| 逾期后成功回忆 | 逾期 1 天与 10 天得到同样的间隔 | 按逾期天数给 `S` 加成（最多 +10%，30 天封顶），间隔仍从今天起算 |
| HARD/EASY | `w15/w16` 乘稳定性，D 变化又影响增长 → 双重加权 | 只乘间隔，稳定性只由 `D` 与 `R` 决定 |

> 相比固定 1/2/4/7 天间隔，FSRS 的最大好处是**抗中断**：
> 用户停三天，算法只是按真实遗忘程度重算，不会雪崩成「几百条待复习」。

### 5.2 弹性计划生成（`TodayPlanGenerator` + `ElasticityPolicy`）

```
factor = 负荷偏好 × 今天状态 × 完成率(或趋势) × 周末
factor = clamp(factor, 0.60, 1.25)
if (到期分钟 > 2 × 基准容量) factor = min(factor, 0.90)   // 积压时最后压一手
有效容量 = round(基准容量 × factor)；手动设定容量时直接采用用户数字，但仍输出推荐值
有积压时：复习最多占 80% 容量，新学只填剩余空间，且受「每日新任务上限」约束
放不下的任务 → 顺延（backlog），绝不超发
极端情况：容量小于任何一条任务，也至少保留最短的一条
```

规则本身在 `ElasticityPolicy` 这个纯函数里，生成器只做输入装配——这样每条规则都能单测。

**选择逻辑的两个修正**：

- 逾期与今日到期**共用**同一条 80% 复习配额。旧实现让逾期任务用满容量，
  结果在最需要缓冲的积压期，新学被彻底饿死。
- 新学改「两遍法」：第一遍严格按优先级，装不下就记入 deferred 并停止；
  第二遍才用剩余碎片补入较短的新内容，并说明补了几条——避免短任务插队破坏优先级。

生成结果附带 `notes`（例：`近 3 天完成率 42%，今天自动减负 30%`），UI 原样展示。

### 5.2.1 三项弹性实验（默认关闭）

设置 →「弹性实验」，逐项可开，便于对比：

| 实验 | 做什么 | 为什么默认关 |
| --- | --- | --- |
| 今天的状态入口 | 首页三枚胶囊（累 0.80 / 正常 1.0 / 状态好 1.12），只影响当天，跨天自动失效 | 需要用户每天多按一次，属于交互取舍 |
| 按分钟计算完成率 | 完成率分母 = 当天首次生成的计划分钟快照，分子 = 已完成条目的计划分钟；7 天窗口做最小二乘斜率 | 新装用户没有历史，先积累样本 |
| 学科配额与轮换 | 仅在过载时生效：按学科权重分容量，夹在 [20%, 60%]，每科保底 1 条量，单科不超发 | 学科少时它就是「平均主义」，反而碍事 |

趋势识别：`slope ≤ -0.04` 判为下滑（×0.90）；后 3 天比前 3 天高 0.08 且最后一天 ≥0.75
判为恢复（×1.05）。样本 < 3 天或覆盖率 < 50% 时**不动**，只在 notes 里说明「样本不足」。

### 5.2.2 截止日期与 leech

- **截止日期**（`DeadlinePolicy`）：进入冲刺窗口后目标保持率线性升到 97%，
  并把最长间隔压到「剩余天数」以内，保证考前还能再见一次；已过期则不惩罚。
  `items.deadlineAt` 优先，`subjects.examAt` 兜底。
  评分与复习页预览共用 `RetentionResolver`，否则会出现「按钮写 4 天、存进去 2 天」。
- **反复遗忘**（`LeechPolicy`）：`lapses ≥ 阈值` 或 `reps ≥ 6 且 lapses×2 ≥ reps` 时标记。
  只做提示（复习页黄字、建议改写或拆分），不自动删除、不改 `S`——
  这类内容的问题通常在「写得太长/没有线索」，而不是复习次数不够。

### 5.2.3 背诵内容与答案

每条任务固定两部分：`content`（背诵内容，复习时先展示）与 `clozeAnswer`（答案，默认隐藏）。

- **不再区分「是不是填空题」**：编辑器只有「背诵内容 + 答案」两栏，没有开关。
  历史、政治这类「一句话挖空」的内容，把空位直接写进内容里、答案单独填即可；
- **有答案** → 复习时内容常显，**点了熟练度之后**才展示答案（先判断、再对照）；
- **无答案**（留空）→ 复习时连内容一起遮住，评分后才展示，适合整段文字背诵；
- 存储上是一个可空列而不是两张表：一条内容随时可以从「只有内容」变成「内容 + 答案」，
  排程与复习记录完全连续，不需要重建。

### 5.3 背诵用时：估算 + 用户覆盖

- 默认按「内容字数 ÷ 200 字/分钟 × 2 遍 + 1 分钟准备」估算，范围 3~60 分钟；
- 用户一旦手动调整，`durationIsCustom = true`，此后内容修改**不会**覆盖用户数字；
- 想回到自动估算，点「恢复自动」即可。

> 为什么不让算法一意孤行：字数与真实背诵速度的相关性有限（陌生术语、公式、诗句差异很大），
> 用户的体感更可靠。后续可用 `review_logs` 里「评分间隔 vs 实际时长」拟合个性化系数，
> 再把估算值作为推荐初值，而不是强制值。

### 5.4 编辑与排程的关系

| 选项 | 行为 | 适用 |
| --- | --- | --- |
| 保持现有排程 | 只更新主题/内容/来源/时长，`review_states` 不变 | 改错别字、补例子 |
| 按新内容重新学习 | 清除 `review_states`，任务作为新学重新排程 | 整段替换内容 |

### 5.5 日历同步（`SyncUpcomingToCalendarUseCase`）

```
任务保存 / 评分完成 / 用户手动点同步
        ↓
读取设置：开关（默认关） + 窗口（默认 24h，可选 12/24/48/72）
        ↓
候选 = 今日计划未完成 + 窗口内到期的任务（按 itemId 去重）
        ↓
写入系统日历：标题「背诵 · 主题」，描述含学科/来源/正文摘要/预计时长
        ↓
幂等：已有事件 → 更新；已不在窗口 → 删除事件与映射
```

- 未授权时返回 `permissionDenied`，UI 引导去开启权限，不静默失败；
- 关闭同步会清空已写入的事件，避免日历里留一堆无效日程；
- 所有异常收敛为 `CalendarSyncResult`，同步失败绝不影响「保存任务」本身；
- 同时提供手动同步入口，不依赖「任务是否刚被更新」。

### 5.6 时间快进（测试工具）

- `TimeProvider` 是全应用唯一时间源：`AdjustableTimeProvider = 真实时间 + 持久化偏移`；
- 偏移改变后通过 `clockChanges` 广播：今日页重算当天计划、日历页跳回新的「今天」；
- 支持「+1/3/7/30 天」与「跳到某天某时刻」，并可一键回到真实时间；
- 偏移持久化，杀进程重启后仍然有效——否则「快进 3 天再重启」的测试会立刻穿帮。

---

### 5.7 复习交互与内容编辑

**两段式评分**（`ReviewViewModel` 的状态机）：

```
未选择 → 点「记得」→ 自动展开答案/内容 + 该按钮变成「下一步」
        → 可改选（原按钮恢复，新按钮变「下一步」）
        → 点「下一步」才写入 review_logs 并推进 FSRS
```

要点：**屏上没有独立的「显示答案 / 显示内容」按钮**，揭示只由「点熟练度」触发
（`selectGrade` 内部把 `revealed` 置 true）。这样用户必须先凭记忆做出判断，
再看对照——而不是先看答案再自评，后者会让曲线系统性偏乐观。

底部操作条（熟练度 + 提示按钮）放在屏幕下半区：拇指够得到的位置，
而不是顶部。

**背诵提示**：每条任务可写一句线索，复习页底部有整宽的「显示提示」按钮
（就在熟练度上方，拇指最容易够到）。
用过提示即标记 `hintUsed`，本次直接隐藏「轻松」——提示是降难度的操作，必须付出代价，
否则评分会系统性偏高、曲线失真。

**Markdown 与分栏**：内容可选 `PLAIN` / `MARKDOWN`。Markdown 模式提供快捷插入
（标题 / 加粗 / 列表 / 引用 / 两栏对照表）与实时预览；复习时用同一个渲染器显示，
两栏对照表适合「原文—释义」「问题—答案」这类结构化背诵。

**用时输入**：+/- 按钮与数字输入框并存，输入框会把 3~60 之外的值即时纠正，
避免出现「界面显示 999、实际存 60」的错位；手填后 `durationIsCustom = true`。

**OCR（测试）**：`OcrRepository` 抽象 + ML Kit 端侧中文识别实现，支持拍照与相册取图，
识别结果追加到内容末尾。由于 ML Kit 依赖 Google Play 服务，界面会先读取可用性：
不可用时明确提示改走「粘贴文本」（编辑器内置按钮）或手动输入，
不会让按钮静默失败。后续可替换为厂商 OCR 或云端识别，接口不变。

### 5.8 兼容性笔记（v1.0.3）

v1.0.0–1.0.2 在 Android 13 及以下的机型上「点开即闪退」，Android 14/15 正常。
前两版先后怀疑过打包版 OCR 的原生库、native 库解压方式，都不是原因——真正的根因是
一个 Java 标准库方法：

| 环节 | 说明 |
| --- | --- |
| 根因 | `LocalDate.ofInstant(...)` 是 **Java 9** 追加的方法，Android 上要 **API 34** 才有实现。项目 `minSdk 29`、`compileSdk 37`，编译期完全合法，运行到「今天」页创建 ViewModel 取当天日期时抛 `NoSuchMethodError` |
| 为什么难查 | 抛的是 `Error` 而不是 `Exception`，且崩溃发生在 Compose 首帧组合过程中——系统视为「首个 Activity 挂上之前就死了」，既不弹「应用停止运行」，应用也来不及画任何东西，用户看到的就是纯粹的闪烁 |
| 为什么日志也没留 | 崩溃处理器被安装在 `Application.attachBaseContext`，却从 `context.applicationContext` 取 Context：该阶段这个值必为 `null`，处理器于是装上了却永远写不出文件。「没有日志」被误读成了「应用代码没跑」 |
| 修复 | 统一改为 `Instant.atZone(zone).toLocalDate()`（API 26 起可用），共 7 处调用点 |
| 兜底 | 处理器改为回退到传入的 Context，并前移到 `super.attachBaseContext` 之前；新增启动阶段记录，崩溃报告会附带「走到哪一步」 |

留个教训在这里：`NewApi` 这类 lint 只比对 `android.jar`，抓不到「Java 标准库方法在低版本
ART 上不存在」这一类问题。这类坑要么靠低版本真机覆盖，要么给模块开启 core library
desugaring，从机制上把 `java.time` / `java.util` 的新增量统一降级。

## 六、界面：三套风格

### 6.1 柔角卡片（默认）

- **色板**：中性浅灰/近黑打底，默认活力橙强调、薄荷绿表示完成；强调色可换成五组预设或自选色相（见 §6.3）；
- **形状**：24–30dp 大圆角卡片，用「极浅描边 + 层次色」代替重阴影，暗色同样成立；
- **排版**：大标题字距收紧、正文 16sp、层级少；
- **组件**：悬浮胶囊底部导航（今日/日历/任务库/设置）、环形进度、胶囊标签、
  自绘底部弹层（非实验 API）、月历格子（学科色点 + 负荷底色）；
- **无障碍**：所有图标按钮带 `contentDescription`，触控目标 ≥ 40dp。

---

### 6.2 终端仪表盘

设置 →「界面风格」可切换。它换的不是配色，而是信息组织方式：

| 维度 | 柔角卡片（默认） | 终端（socpk 语汇） |
| --- | --- | --- |
| 底色 | 浅灰 / 近黑 | `#0A1018` 深蓝黑，固定深色 |
| 强调色 | 活力橙 | 中饱和霓虹四色：蓝 `#4DA3FF`、橙 `#FF8A4C`、绿 `#4FE0A8`、灰 `#7E8FA3` |
| 今日概览 | 环形进度 + 负荷标签 | 规格条（计划/完成/剩余/容量/保持率）+ 记忆保持曲线 + 近 7 天负载柱 |
| 任务列表 | 圆角卡片 | 紧凑行：序号 / 学科色条 / 主题 / 学科·时长 / OVERDUE 标记 |
| 形状 | 24–30dp 圆角 | 3–10dp 极小圆角，层级靠 1px 描边 |
| 字体 | 大标题抒情、正文 16sp | 数字与英文标签用 condensed / 等宽，中文用系统无衬线 |
| 设置 | 可切换浅色/深色/跟随系统 | 固定深色，**不显示主题模式** |
| 动效 | 240ms 淡入淡出 | 150ms 硬切，仅用于转场，不加装饰动画 |

组件库（`core:designsystem/.../Charts.kt`）：

- **`SpecStrip`**：等宽规格条，value/unit/label 三层，数字列对齐；
- **`RetentionCurve`**：单序列折线，1.6dp 描边、不填面积；目标保持率用绿色虚线，属于参考线而非数据；
- **`LoadBars`**：并排细柱（灰=计划、蓝=完成），数值标在柱顶外侧，柱体无特效；
- **`SectionLabel`**：`01 / RETENTION` + 中文标题，分区靠编号与细线建立，不用卡片阴影。

约束（写进代码注释，避免后续被「优化」掉）：禁止装饰性渐变、发光、玻璃拟态、粒子；
主按钮在终端风格下自动退化为纯色块；不使用超过两个色相装饰同一画面；
所有线条必须对应一条信息边界。

### 6.3 磨砂玻璃

设置 → 外观 → 界面风格选「磨砂玻璃」。它把底色交给照片，靠**材质**而不是色板建立层次。

| 维度 | 说明 |
| --- | --- |
| 壁纸 | 微软 Bing 每日一图；按屏幕尺寸自动选图（宽屏 UHD / 常规 1080p），当天缓存、次日自动换 |
| 铺法 | 铺满 / 适应 / 拉伸 / 居中（对齐 Windows） |
| 可读性 | 模糊 0–48dp（**默认 24dp**）、遮罩 0–70%。默认比 iOS 的浅磨砂重得多——背诵时背景不该抢注意力 |
| 明暗 | 跟随系统 / 亮磨砂 / 暗磨砂 |
| 材质 | `background` 透明、`surface` 半透明白：卡片、按钮、底栏本来就用 surface，叠在壁纸上天然是玻璃，不需要逐组件写磨砂分支 |
| 强调色 | 五组预设（暖橙 / 电光蓝 / 正红 / 青绿 / 亮蓝）+ 色相自调，对所有风格生效 |
| 下载 | 「下载今日壁纸」写到系统 `下载/Meowmorize`，可拿去当手机壁纸 |

两条实现约束（改动时别踩）：

- **遮罩颜色随明暗切换**：亮磨砂压白、暗磨砂压黑。统一压黑会让深色文字糊在暗遮罩上；
- 壁纸解码放在 IO 线程并降采样（≤2200px）；`Modifier.blur` 需要 **API 31+**，
  更低版本只靠遮罩保证可读性。

## 七、构建与运行

环境：**JDK 21**（AGP 9 的要求）、Android SDK 37、Gradle 9.9.1、AGP 9.5.1。
APK 内含**自带中文 OCR 模型**（约 20MB），这是为了让无 GMS 的国产设备也能「拍照取字」；
如果只需要手动录入，可以移除 `mlkit-text-recognition-chinese` 依赖把包体积降回去。

`app` 模块已开启 **core library desugaring**：`java.time` / `java.util` 里 Java 9+ 追加、
Android 低版本没有的 API 会统一降级到 minSdk 29，避免「编译期合法、运行期 NoSuchMethodError」。
签名材料不入库，release 构建会从 `~/.meowmorize/keystore.properties` 读取，缺失时产出 unsigned 包。

```bash
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew :app:assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk

./gradlew :core:domain:test           # 76 个领域层用例
./gradlew :app:lintRelease            # 发版前的静态检查（当前 0 error）
```

权限说明：

| 权限 | 用途 | 申请时机 |
| --- | --- | --- |
| `READ_CALENDAR` / `WRITE_CALENDAR` | 写入/清理「未来窗口内的背诵任务」日程 | 仅在设置中开启「系统日历同步」时申请，拒绝后功能保持关闭 |

> AGP 9 内置 Kotlin 编译，Android 模块无需再应用 `kotlin-android` 插件；
> 纯 Kotlin 模块（`model/domain/common`）仍使用 `kotlin-jvm`。

---

## 八、目录结构

```
Meowmorize/
├── app/                          # 应用壳：导航、主题注入、启动引导
├── core/
│   ├── common/                   # TimeProvider、格式化
│   ├── model/                    # 领域模型（含日历、排序枚举）
│   ├── domain/                   # FSRS / 弹性计划 / 排序 / 用例 / 仓库接口（含单测）
│   ├── database/                 # Room 实体、DAO、迁移、schema
│   ├── data/                     # 仓库实现、DataStore、日历适配、可调时钟、Hilt 装配
│   └── designsystem/             # 主题与通用组件
├── feature/
│   ├── today/  calendar/  library/  editor/  settings/
├── docs/                         # 项目介绍、动效规范、发版清单、图标
├── tools/generate_icons.py       # 从源图生成全套启动图标（纯标准库）
├── .github/                      # CI、issue/PR 模板、dependabot
├── gradle/libs.versions.toml     # 版本目录
└── README.md
```

图标不是手工导出的：`python tools/generate_icons.py <源图.png>` 会生成 5 个密度的
legacy / 圆形 / 自适应前景，并重写 `mipmap-anydpi-v26` 的 XML。

---

## 九、扩展点

| 方向 | 落点 |
| --- | --- |
| 统计与趋势 | 读 `review_logs` 增加 `:feature:stats`，domain 加 `ReviewStatsUseCase`，不改表结构 |
| 复习提醒 | 用 `review_states.dueAt` 做 WorkManager 每日调度；`calendar_sync` 已可作为事件锚点 |
| 个性化时间估算 | 用日志拟合「主题类型 → 实际耗时」系数，替换 `DurationEstimator` 的常数 |
| 语音背诵 | 新增 `feature:recite`，用系统 SpeechRecognizer 对比 `content`，不改表结构 |
| 更多复习模式 | 内容与答案已经分离（`content` + `clozeAnswer`），可在此基础上扩展选择题 / 听写 |
| 多端同步 | 替换 `:core:data` 的仓库实现（接口隔离在 domain） |
| 更快的时间实验 | 在现有 `TimeTravelRepository` 上增加「按天自动推进」的测试脚本 |

---

## 十、许可证与素材声明

代码以 [MIT License](LICENSE) 发布，可自由使用、修改、分发，保留版权声明即可。

**应用图标是例外**：它取自《卡拉彼丘》（Strinova）**官方表情包**，角色形象与著作权
归原权利方所有，**不在 MIT 许可范围内**，这里仅作为个人项目的非商业标识使用，
并非本项目原创，也不代表与官方有任何关联。
如果你要基于本项目做二次分发或商业使用，请替换 `app/src/main/res/mipmap-*` 下的图标资源。

替换方式见 [CONTRIBUTING.md](CONTRIBUTING.md)：准备一张透明背景的方形图，
跑一次 `python tools/generate_icons.py <图>` 即可，不要手工改单张 PNG。

---

## 十一、更新与发布

应用内自带更新（设置 → 检查更新）：向 GitHub Releases 查最新正式版后，**在应用内下载 APK**
并交给系统安装器（用户确认后才安装）；「启动时检查更新」默认开启，6 小时内不重复查询。

`.mmr` 备份格式见 [docs/MMR-FORMAT.md](docs/MMR-FORMAT.md)，含字段表与兼容性约定。

因此发版流程是固定的——**README 与代码同版本**，不留「下次再补」：

1. 升 `versionCode` / `versionName`（`app/build.gradle.kts`）
2. 更新 `CHANGELOG.md`（新增 / 变更 / 修复）
3. **补齐 README**：功能表、架构图里的模块与用例数、构建章节的工具链版本、素材声明
   —— 这几处最容易随版本过期
4. `./gradlew :core:domain:test :app:lintRelease :app:assembleRelease`
5. 导出 APK、记录 sha256，创建 Release（tag `vX.Y.Z`）并上传 `Meowmorize.apk`
6. 确认 `main` 上 CI 跑绿

清单同步放在 [docs/RELEASE.md](docs/RELEASE.md)，发版前照抄。

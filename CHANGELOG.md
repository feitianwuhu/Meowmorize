# 更新日志

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)，格式参考
[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

（暂无）

## [1.0.4] - 2026-11-15

这一版只动构建与工具链，功能与 1.0.3 一致。

### 变更
- 开启 **core library desugaring**：`java.time` / `java.util` 里「Java 9+ 追加、Android 高版本才有」
  的库 API（如 `LocalDate.ofInstant`）统一降级到 minSdk 29，从机制上堵住 1.0.3 修的那类
  `NoSuchMethodError`——以后即使有人写出同样的调用，也不会再在低版本设备上崩
- 依赖升级：AGP `9.4.0 → 9.5.1`、Gradle `9.6.0 → 9.9.1`

### 修复
- `gradlew` 在仓库里丢了可执行位（`100644`），Linux / macOS 克隆后 `./gradlew` 直接
  `Permission denied`；改回 `100755`
- CI 工作流恢复并修正两处：`android-actions/setup-android` 在新版 cmdline-tools 上会去装
  已下线的 `tools` 包，改为直接用 runner 自带的 SDK

### 新增
- CI 增加门禁：提交里出现 `LocalDate.ofInstant` 等「低版本缺失的 Java API」直接失败
  （注释中提及不算），避免同类问题再次流到真机

## [1.0.3] - 2026-11-15

### 修复
- **启动即闪退（Android 13 及以下）**：根因是 `LocalDate.ofInstant(...)`——Java 9 才追加、
  Android 上要 **API 34** 才有实现的方法。项目 minSdk 29、compileSdk 37，编译期毫无警告，
  运行到「今天」页实例化 `TodayViewModel` 取当天日期时直接抛 `NoSuchMethodError`。
  改为 `Instant.atZone(zone).toLocalDate()`（API 26 起就有），共 7 处调用点。
  这就是 1.0.1 / 1.0.2 两次「换 OCR / 换打包方式」都没治好的那个闪退。
- **崩溃上报本身就是坏的**：`CrashLog.install()` 存的是 `context.applicationContext`，
  而它首次执行于 `Application.attachBaseContext`——此时该值必为 `null`，
  于是处理器装上了却永远写不出文件，此前「没有日志」被误读成「应用代码没跑」。
  改为回退到传入的 Context，并把安装点移到 `super.attachBaseContext` 之前。

### 新增
- 启动阶段记录：每个关键阶段（attachBaseContext / Application.onCreate / 首个 Activity /
  首帧）落一行，崩溃报告里会整段附带
- debug 变体新增「探针」界面（纯 `Activity` + `TextView`，不经 Compose / Hilt），
  可直接在屏幕上读启动记录与崩溃报告，便于手机不接电脑时取证

## [1.0.2] - 2026-11-15

### 新增
- 全新应用图标：深蓝黑底 + 自适应前景，覆盖 5 个密度与圆形版本，并适配 Android 12 启动画面
- `tools/generate_icons.py`：从一张透明背景源图生成全套图标，纯标准库、可复现

### 变更
- 仓库补齐开源基础设施：MIT License、贡献指南、行为准则、安全策略、更新日志、
  issue / PR 模板、GitHub Actions CI、Dependabot、`.editorconfig`、`.gitattributes`
- README 重写开头：图标、徽章、项目定位与素材声明

## [1.0.1] - 2026-11-15

### 修复
- **部分 ROM 启动即闪退**（Android 13 及以下复现，Android 14/15 正常）：
  OCR 从打包版 ML Kit 换成 GMS 按需下载版，APK 内不再包含 OCR 原生库
  （注：真正的根因在 1.0.3 才定位，见该版本说明）
- native 库改为安装时解压（`useLegacyPackaging`），避免个别 ROM 直接 mmap 失败
- 启动页不再对 View 的 Context 强转 `Activity`，改为逐层解包 `ContextWrapper`
- 复习评分出结果后按钮未锁定，可连续点击把间隔推到很远

### 新增
- 任务库支持**删除任务**，二次确认后连带清除记忆进度与复习日志
- **管理学科**：可删除学科，其下任务转为「未分类」而非被删除
- **填空题**：题干 + 答案分开填写，复习时题干可见、答案需点「显示答案」
- 未捕获异常写入 `filesDir/crash-*.txt`，便于无 adb 时取证

### 变更
- 数据库升级到 v5（`items.clozeAnswer`）
- APK 体积 93MB → 51MB（移除内置 OCR 模型）

## [1.0.0] - 2026-11-14

### 新增
- FSRS-4.5 排程：稳定性 / 难度双参数，按目标保持率反解间隔
- 弹性容量：负荷偏好 × 今天状态 × 完成率趋势 × 周末，夹在 0.60–1.25，绝不超发
- 同日重练步进（10m → 60m → 240m）与当日重评上限
- 逾期成功回忆的稳定性补偿（30 天封顶）
- 反复遗忘（leech）判定与改写建议
- 考试/截止日期冲刺：临近截止提高保持率并压缩最长间隔
- 今日 / 日历 / 任务库 / 设置四个页签，任务库 6 种排序
- 背诵提示、两段式评分、Markdown 内容、OCR 取字（测试）
- 两套视觉：柔角卡片与终端仪表盘
- 系统日历同步（默认关闭）、时间快进测试工具

[Unreleased]: https://github.com/feitianwuhu/Meowmorize/compare/v1.0.2...HEAD
[1.0.2]: https://github.com/feitianwuhu/Meowmorize/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/feitianwuhu/Meowmorize/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/feitianwuhu/Meowmorize/releases/tag/v1.0.0

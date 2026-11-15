# 更新日志

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)，格式参考
[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

（暂无）

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
- **部分 ROM 启动即闪退**（华为鸿蒙 4.2 / 小米澎湃 OS 1 复现，Android 14/15 正常）：
  OCR 从打包版 ML Kit 换成 GMS 按需下载版，APK 内不再包含 OCR 原生库
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

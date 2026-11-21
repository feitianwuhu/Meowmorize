# 发版清单

发版不只是「打个包」：README 比 Release 说明更容易被看到，也最容易随版本过期。
这份清单保证**代码、CHANGELOG、README 三者同版本**。

## 0. 前置

- `main` 干净、CI 绿
- 本机有签名材料：`~/.meowmorize/keystore.properties`
  （缺失时会产出 unsigned 包——别把它发出去）

## 1. 代码与版本

- `app/build.gradle.kts`：`versionCode + 1`；`versionName` 按语义化
  （修 bug → patch，加功能 → minor）
- `CHANGELOG.md` 顶部加版本段落：新增 / 变更 / 修复

## 2. README 必须同步（最容易漏）

逐条**对照代码**，不要凭记忆：

- 功能表：新功能是否加行？被替换的交互是否还停在旧描述？
  （例：v1.1.0 把「点按钮揭晓」改成「评分即揭晓」，v1.0.x 的说明就不成立了）
- 架构图与模块职责：模块增减、单测数量
  （`./gradlew :core:domain:test` 之后看 `core/domain/build/test-results`）
- 构建章节：JDK / Gradle / AGP / SDK 版本
  （`gradle/wrapper/gradle-wrapper.properties`、`gradle/libs.versions.toml`）
- 数据模型：表结构版本与迁移列表（`core/database/schemas/`）
- 素材声明：图标与第三方资源的出处、授权
- 关键词残留检查：`HyperOS`、`转填空`、`两套风格`、旧 Gradle 版本等

## 3. 验证

```bash
./gradlew :core:domain:test        # 单测
./gradlew :app:lintRelease         # 静态检查，应 0 error
./gradlew :app:assembleRelease     # 正式包
```

- `aapt2 dump badging` 确认 versionCode / versionName
- `apksigner verify --print-certs` 确认签名指纹未变
- 真机覆盖安装，跑一遍：今日 → 复习 → 编辑 → 设置 → 检查更新

## 4. 发布

- 导出 `dist/Meowmorize-<version>.apk` 并计算 sha256
- 创建 Release：tag `vX.Y.Z`，`target_commitish` 用**完整 40 位 SHA**
  （短 SHA 会 422，且报错信息会误导成「tag 非法」）
- 资产名统一 `Meowmorize.apk`——`releases/latest/download/Meowmorize.apk` 才稳定可用
- Release 正文：机型与品牌词 0 处（品牌色名也算命中）
- 确认 `main` 上 CI 绿

## 5. 事后

- 应用内「检查更新」能查到新版本
- 记一条工作日志：改了什么、踩了什么坑

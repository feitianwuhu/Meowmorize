# 贡献指南

感谢你愿意花时间看这个项目。下面是最省事的参与路径。

## 先做这三件事

1. 读 [README](README.md) 的「核心算法」与「整体架构」，了解领域层与 UI 的边界；
2. 跑一遍构建与测试（见下），确认本地环境正常；
3. 有 bug 或想法，先开 issue 说清楚「现象 / 期望 / 复现步骤」，避免白写。

## 环境

| 项 | 版本 |
| --- | --- |
| JDK | 21（AGP 9 要求） |
| Gradle | 9.6+，用仓库自带的 `./gradlew` 即可 |
| Android SDK | compileSdk 37 / minSdk 29 |
| Kotlin | 由 AGP 内置的 Kotlin 编译器提供，**不要**再应用 `kotlin-android` 插件 |

```bash
# 领域层单测（纯 JVM，最快，改算法先跑这个）
./gradlew :core:domain:test

# 编译调试包
./gradlew :app:assembleDebug

# 静态检查
./gradlew :app:lintDebug
```

Windows 上用 `gradlew.bat`；如果 SDK 路径没配，加 `local.properties`：

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## 签名与发布

仓库**不包含**签名材料。`app/build.gradle.kts` 会从 `~/.meowmorize/keystore.properties`
读取，文件不存在时 release 构建仍可完成，只是产出 unsigned APK。

想自己发版：新建 keystore，按同样格式写 properties，并**不要**提交任何 `.jks` / 口令。

## 代码约定

- **分层不能反向**：`feature → domain → model`。UI 不直接依赖 `:core:data`；
  领域层保持纯 Kotlin（不引 Android 依赖），这样排程规则能被纯 JVM 单测覆盖。
- **算法改动必须带测试**。`core/domain/src/test` 里每个规则都有对应用例，
  边界（0、负数、超上限、跨天）比正常路径更值得写。
- **注释写「为什么」**，不写「做了什么」。例如「这里不能直接 `as Activity`，
  部分 ROM 会给 View 套 ContextWrapper」比「转换 context」有用得多。
- **数据库只加不删**：新增列必须带默认值，同时写 `MIGRATION_x_y` 并提交 `schemas/*.json`。
  Room 会在启动时校验 schema，新装与升级必须得到完全一致的表结构。
- 提交信息用 `type(scope): 摘要`，type 取 `feat / fix / refactor / docs / build / chore / test`。

## 提交 PR

- 一个 PR 只做一件事，附带「改动动机」与「怎么验证的」；
- UI 改动请附截图（没有真机的话，说明在哪个 API 级别验证过）；
- 提交前跑一遍 `./gradlew :core:domain:test :app:assembleDebug`；
- 若改动涉及数据库，请在 PR 里写清迁移路径与回滚方式。

## 图标

启动图标由 `tools/generate_icons.py` 从源图生成，纯标准库实现：

```bash
python tools/generate_icons.py <透明背景源图.png>
```

它会输出 5 个密度的 legacy / 圆形 / 自适应前景，并重写 `mipmap-anydpi-v26` 的两个 XML。
**不要手工替换单张 PNG**，否则各密度会不一致。

## 行为准则

参与本项目即表示同意遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

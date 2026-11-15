# 安全策略

## 支持范围

本项目只在**最新发布版**上接受安全修复。旧版本请先升级到
[Releases](https://github.com/feitianwuhu/Meowmorize/releases/latest) 的最新版本。

## 这个应用会接触什么数据

- 全部数据（任务、记忆进度、复习日志、设置）保存在**本机**：Room 数据库 + DataStore，
  没有账号体系，也不会上传任何内容；
- 网络权限仅用于 OCR（Google Play 服务的文字识别模块）与系统组件，应用本身不发请求；
- 日历权限（READ/WRITE_CALENDAR）只在用户主动开启「系统日历同步」时使用，默认关闭；
- 相机不申请权限，拍照走系统相机 Intent。

## 报告漏洞

请不要公开开 issue。用下面任一方式私下联系：

- GitHub 的 [Private vulnerability reporting](https://github.com/feitianwuhu/Meowmorize/security/advisories/new)（推荐）
- 或发邮件到仓库维护者邮箱（见 GitHub 个人主页）

请尽量附上：

1. 影响版本与设备 / Android 版本；
2. 复现步骤或 PoC；
3. 影响判断（数据泄露？本地提权？拒绝服务？）。

我会在 7 天内确认收到，并在修复发布后于 Release Notes 中致谢（如果你愿意署名）。

## 不在范围内

- 需要 root / 解锁 bootloader / 物理接触设备才能利用的问题；
- 第三方依赖自身的已知问题（请直接反馈给上游，同时抄送我一份也可以）；
- 社交工程、钓鱼，以及「应用图标里的角色版权」这类非安全问题（见 README 免责声明）。

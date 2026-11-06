# Git 同步 v1.0.0

Android 8（API 26）及以上可用的 Git 自动同步客户端。界面保持「顶部按钮 + 列表 + 日志」的简单风格。

## 两种模式

- **自动模式**：点一下完成 拉取 → 暂存 → 提交 → 推送
- **手动模式**：获取 / 拉取 / 暂存 / 提交 / 推送 / 状态，每个操作都单独点一次

## 功能

- 管理多个 Git 仓库，支持克隆远程仓库、初始化本地空仓库
- 提交历史与提交 diff、工作区 diff
- 状态查看、分支管理（新建 / 切换 / 重命名 / 删除）、多远程（增删改地址）
- 冲突列表，支持「用本地版本 / 用远程版本 / 打开文件手动改」
- 仓库内文件浏览与文本编辑，`.gitignore` 与 `.git/info/exclude` 编辑
- HTTPS Token / 用户名密码 / SSH 私钥认证
- 定时同步、开机自启、网络恢复重试、快捷设置磁贴、前台服务与结果通知
- 设置页内置 7 步端到端自检（建仓库 → 推送 → 克隆 → 一键同步 → 核对 → 历史 → 分支/远程）

功能参照 [ViscousPot/GitSync](https://github.com/ViscousPot/GitSync) 设计，代码为独立实现（JGit 4.11 + JSch），含 Android 文件系统适配层。

## 附件

- `GitSyncApp-v1.0.0.apk` — 正式版，直接安装
- `GitSyncApp-v1.0.0-debug.apk` — 调试版

仓库 `dist/` 下另有中文文件名的同两份 APK，内容完全相同。

## 从源码构建

需要 JDK 17、Android SDK 34（build-tools 34.0.0）、Gradle 8.13，详见仓库 README。

MIT 许可证。Git 操作只发生在你配置的远程仓库之间，不向第三方上报数据；凭据仅存于本机应用私有目录。同系列另有 SimpleTextEditor 与 MusicPlayer。

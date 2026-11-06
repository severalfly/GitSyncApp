# Git 同步（Android）

一个纯原生 Java 写的本地 Git 客户端，参照开源项目 [ViscousPot/GitSync](https://github.com/ViscousPot/GitSync) 的功能设计。
**两种模式：自动模式点一下就完成拉取 + 提交本地修改 + 推送；手动模式每一步自己点。**
支持多个仓库、提交历史、分支、远程、冲突处理、后台定时同步。

- 适用：Android 8.0（API 26）及以上
- 不联网收集任何数据；git 操作基于 JGit，纯本地实现
- 默认仓库放在应用私有目录，**不需要任何存储权限**；要同步手机里已有的文件夹，才需要开启「所有文件访问权限」

## 安装

可直接在 [Releases](https://github.com/severalfly/GitSyncApp/releases) 下载 `GitSyncApp-v1.0.0.apk`；
或把 `dist/Git同步.apk` 拷到手机直接安装（需允许未知来源）。

## 快速开始

1. 打开 app → 点「设置」→「运行自检」，确认 git 引擎可用（这一步会真的建仓库、推送、克隆、看历史，不联网）
2. 点「添加仓库」：
   - 填仓库名称
   - 本地路径：默认已填好应用私有目录，直接用即可；想同步已有目录就点「选择文件夹」或手填 `/storage/emulated/0/…`
   - 远程地址：`https://…`、`git@…` 或本地 `file://…`
   - 认证方式：
     - `none`：公开仓库 / 本地路径
     - `https`：填用户名 + 密码或 Personal Access Token（GitHub / GitLab / Gitea 都支持）
     - `ssh`：把 PEM 私钥全文粘进输入框
3. 点「克隆远程仓库到这个目录」（或「初始化本地空仓库」）
4. 回到首页点仓库名进详情 → **自动模式**点大按钮一键同步

## 两种模式

| 自动模式 | 手动模式 |
|---|---|
| 一键：拉取远程 → 暂存所有本地修改 → 生成提交 → 推送 | 每个操作独立按钮：获取 / 拉取 / 暂存 / 提交 / 推送 / 状态 |
| 可勾选「加入定时同步」「打开/退出应用时同步」 | 提交时弹窗让你填提交消息 |
| 冲突时自动停下并列出冲突文件 | 另有：提交历史 / 分支 / 远程 / 文件 / 冲突 / 仓库设置 |

底部有实时执行日志，每一步都看得见。

## 参照 GitSync 实现的功能

| GitSync 功能 | 本 app |
|---|---|
| 管理多个仓库 | ✅ 首页列表，长按删除，每个仓库独立配置 |
| 自动同步（fetch / pull / stage / commit / push） | ✅ 一键完成，后台服务执行 |
| 手动逐项操作 | ✅ 六个独立按钮 + 提交消息输入 |
| 提交历史 + commit diff | ✅ 点历史看每条提交的补丁（增删行） |
| 文件 diff / 行 diff | ✅ 工作区 diff + 提交 diff 文本 |
| 分支管理（新建 / 切换 / 重命名 / 删除） | ✅ |
| 多远程（添加 / 改名 / 改 URL / 删除） | ✅ |
| 克隆远程仓库 | ✅ |
| 冲突解决 | ✅ 冲突文件列表 + 用本地版 / 用远程版 / 手动编辑 |
| HTTP/S、SSH 认证 | ✅ 用户名 + Token、PEM 私钥 |
| OAuth（GitHub/GitLab/Gitea） | ⚠️ 用 Personal Access Token 替代，效果等价、不用注册 OAuth 应用 |
| 定时同步 / 开关应用时同步 | ✅ 间隔分钟数可配，AlarmManager + 前台服务 |
| 网络恢复自动重试 | ✅ 失败后联网自动补同步 |
| 快捷设置磁贴、通知、开机自启 | ✅ |
| 文件浏览 + 代码编辑器 | ✅ 内置文本编辑，退出自动保存 |
| 编辑 `.gitignore` 和 `.git/info/exclude` | ✅ |
| 自定义提交消息、作者信息 | ✅ 支持 `%date` 占位符 |
| 关闭 SSL 校验（按仓库） | ✅ |
| 浅克隆 | ⚠️ 所用 JGit 版本不提供该参数，执行完整克隆（界面有提示） |
| 签名提交 | ❌ 未做 |
| Issues / PR / Actions / AI 聊天 | ❌ 未做（属于代码托管平台的云 API，不是本地 git 同步） |
| 主屏小组件、iOS / Shortcuts、LFS / git-crypt | ❌ 未做 |

## 目录结构

```
GitSyncApp/
├─ app/src/main/java/com/leon/gitsync/
│  ├─ AndroidFS.java       关键：绕开 Android 不支持硬链接的 JGit 适配
│  ├─ GitEngine.java       所有 git 操作（JGit）
│  ├─ Repo / RepoStore     多仓库配置与持久化
│  ├─ MainActivity         仓库列表
│  ├─ RepoActivity         自动 / 手动双模式详情
│  ├─ BrowserActivity      历史 / 状态 / 分支 / 远程 / 文件 / 冲突 / diff
│  ├─ EditRepoActivity     添加与编辑仓库
│  ├─ SettingsActivity     全局设置 + 自检
│  ├─ TextEditActivity     文本文件编辑
│  └─ SyncUtil / SyncService / SyncReceiver / SyncTileService  后台同步
├─ dist/                   编译好的 APK
└─ build.bat / build.sh    一键重新编译
```

## 技术说明（踩过的坑）

JGit 默认 `FS_POSIX` 用 `Files.createLink()`（硬链接）原子创建 `.lock` 文件，
而 Android 的 `/sdcard` 是 FUSE 文件系统，不支持硬链接，会直接抛 `AccessDeniedException`，
导致 init / commit / push 全部失败。`AndroidFS` 继承 `FS` 基类（完全不用 `FS_POSIX`），
把 `createNewFile` 换成 `File.createNewFile()`，屏蔽 `setHidden`（Android 没有 `dos:hidden`
属性视图）、符号链接和 POSIX 权限探测，并通过反射把 JGit 的全局 `FS.DETECTED` 换成本实现，
保证 JGit 内部任何取 FS 的路径都拿到它；打开仓库时再用 `FileRepositoryBuilder.setFS()` 双保险。

另一个坑：首页仓库列表每一行里有个「同步」按钮。ListView 的 item 中只要存在可获得焦点的
Button，`setOnItemClickListener` 就不会触发（点整行没反应）。所以 `item_repo.xml` 的根布局加了
`android:descendantFocusability="blocksDescendants"`、按钮设为不可获焦点，同时在
`MainActivity.getView()` 里直接给行根布局绑 `OnClickListener`，保证点行任意位置都能进详情页。

工具链（JDK 17 + Gradle 8.13 + Android SDK 34）复用 `D:\leon\SimpleTextEditor\toolchain\`，
双击 `build.bat` 即可重新编译；Git Bash 下用 `sh build.sh`。两个脚本都会把 release / debug
APK 复制到 `dist/Git同步.apk`、`dist/Git同步-debug.apk`。`build.sh` 直接调用
`org.gradle.launcher.GradleMain`，绕开 gradle 包装脚本在 Git Bash 下的路径转换挂起问题。
`build.bat` 按 GBK 编码保存，避免中文 Windows 的 cmd 把中文文件名读成乱码。

换机器编译只需 JDK 17 + Android SDK 34 + Gradle 8.13，设好 `JAVA_HOME` /
`ANDROID_HOME`（或写进 `local.properties`）后执行 `gradle assembleDebug`，
也可以直接用 Android Studio 打开本目录。`build.bat` / `build.sh` 是开发机上的一键脚本。

## 实测记录（Android 模拟器，API 30）

- 设置页「运行自检」7/7 通过：建裸仓库 → 首次提交推送 → 克隆 → 修改文件 → 一键同步 →
  再克隆核对远程内容 → 提交历史 2 条 → 分支 2 个 / 远程 1 个
- 自动模式：点「一键同步」日志依次输出拉取 / 暂存 / 提交 / 推送四步；无远程仓库时优雅跳过并说明原因
- 手动模式：状态、提交消息弹窗正常；提交历史、分支管理、远程管理、文件浏览、合并冲突、
  仓库设置六个页面全部能打开且无崩溃
- 首页点仓库行进详情、模式切换、日志滚动均正常

## 开源

- 许可证：[MIT](LICENSE)
- Git 操作只发生在你配置的远程仓库之间，不向任何第三方服务上报数据；凭据仅存在本机应用私有目录
- 功能参照 [ViscousPot/GitSync](https://github.com/ViscousPot/GitSync) 设计，代码为独立实现
- 同系列另有 SimpleTextEditor 与 MusicPlayer；欢迎 Issue / PR

## 已知限制

- 仓库路径必须是真实文件路径（不能用 `content://`），所以同步 /sdcard 需要「所有文件访问权限」
- 大文件、二进制文件不能在应用里编辑（>1MB 或含 NUL 会拒绝）
- 定时同步在部分国产系统上需要把 app 加入电池优化白名单
- Android 12+ 从后台定时启动前台服务可能被系统限制，打开一次 app 会补同步

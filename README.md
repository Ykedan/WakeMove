<h1 align="center">WakeMove · 醒动</h1>

<p align="center">
  一款必须完成动作或离线语音挑战，才能结束响铃的 Android 闹钟。
</p>

<p align="center">
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Status" src="https://img.shields.io/badge/status-beta-FF7458">
</p>

## 为什么做醒动

普通闹钟最容易在半睡半醒时被顺手关掉。

WakeMove 把“关闭闹钟”换成一个短挑战：做几次动作，或者完整说出一句话。
只有真正清醒并完成目标，响铃才会结束。

项目坚持本地优先：闹钟、历史记录、相机画面和麦克风音频都留在设备上，
动作与语音识别不依赖云端服务。

## 主要功能

- 创建、编辑、启用和删除一次性或每周重复闹钟
- 精确闹钟调度、锁屏全屏响铃、循环声音和振动
- 深蹲、开合跳、双手举高三种动作挑战
- Vosk 简体中文离线语音挑战
- CameraX + MediaPipe 本地人体关键点识别
- 最多 3 次贪睡，并可从闹钟卡片随时“立即挑战”
- 4 段内置舒缓铃声，支持试听
- 3 种震动节奏和 3 档震动力度
- 响铃历史、权限与系统能力健康检查
- 按住 10 秒的紧急停止通道
- 首页问候和副标题随早晨、中午、下午、晚上自动变化

## 界面设计

WakeMove 使用名为“蓝调破晓”的视觉系统：

- 雾白背景承载日常设置与管理
- 深夜蓝用于下一次闹钟和响铃等核心场景
- 珊瑚色只强调必须立即执行的唤醒动作
- 原生 Compose 绘制的“唤醒轨道”作为品牌图形

详细设计说明见 [UI-DESIGN-2026-07-30.md](docs/UI-DESIGN-2026-07-30.md)。

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- Room
- AlarmManager + Foreground Service
- CameraX
- MediaPipe Tasks Vision
- Vosk Android
- JUnit、Robolectric、Compose UI Test

## 项目结构

```text
WakeMove/
├─ android/                         Android 应用
│  └─ app/src/main/java/com/wakemove/android/
│     ├─ challenge/                 动作与离线语音识别
│     ├─ data/                      Room 数据与仓库
│     ├─ domain/                    闹钟模型和业务规则
│     ├─ health/                    权限与系统能力检查
│     ├─ ringing/                   响铃服务、音频和会话
│     ├─ scheduling/                精确闹钟调度与恢复
│     └─ ui/                        Compose 界面
├─ shared/                          跨端共享内容
└─ docs/                            设计与开发文档
```

## 本地运行

### 环境要求

- Android Studio
- JDK 17
- Android SDK 37
- Android 10（API 29）或更高版本的设备/模拟器

### 构建 Debug APK

```powershell
git clone https://github.com/Ykedan/WakeMove.git
cd WakeMove\android
.\gradlew.bat assembleDebug
```

构建产物：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

也可以直接使用 Android Studio 打开 `android` 目录并运行 `app`。

> Release 构建需要自行准备本地签名配置。签名文件和密码不会提交到仓库。

## 测试

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug
```

需要已启动 Android 模拟器或已连接真机时：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

当前版本已通过：

- 159 项 JVM 测试
- 60 项 Android 模拟器测试
- Android lint：0 个错误

## 下载体验

最新正式测试版可从
[GitHub Releases](https://github.com/Ykedan/WakeMove/releases/latest) 下载：

```text
WakeMove-v1.2.0.apk
```

- 支持 Android 10（API 29）及以上版本
- APK SHA-256：
  `6A2D5081012E8EF661C0B2B3D16F5BF778632F7A9F96D9EC6DBE6A85531AF323`
- 此版本使用 WakeMove 正式签名；如果设备装过开发签名测试版，需要先卸载旧版

## 当前状态

WakeMove 目前处于公开测试阶段，尚未上架应用商店。GitHub Release 提供的是
正式签名测试包，现阶段重点是继续验证不同品牌 Android 手机上的锁屏响铃、后台调度
和权限兼容性。

欢迎通过 [Issues](https://github.com/Ykedan/WakeMove/issues) 提交复现步骤、设备型号、
Android 版本和相关截图。

## 授权说明

本项目暂未选择开源许可证。在正式补充许可证前，代码及素材保留全部权利。

<h1 align="center">WakeMove · 醒动</h1>

<p align="center">
  An Android alarm clock that makes you complete a movement or offline voice challenge before the alarm stops.<br>
  一款必须完成动作或离线语音挑战，才能结束响铃的 Android 闹钟。
</p>

<p align="center">
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Languages" src="https://img.shields.io/badge/UI-中文%20%7C%20English-3B82F6">
  <img alt="Status" src="https://img.shields.io/badge/status-beta-FF7458">
</p>

<p align="center">
  <a href="https://ykedan.github.io/WakeMove/">Website / 官网</a>
  ·
  <a href="https://github.com/Ykedan/WakeMove/releases/latest">Download APK / 下载 APK</a>
  ·
  <a href="https://ykedan.github.io/WakeMove/privacy">Privacy / 隐私政策</a>
  ·
  <a href="https://ykedan.github.io/WakeMove/security">Security / 安全说明</a>
</p>

English | [简体中文](#简体中文)

## English

### Why WakeMove

It is easy to dismiss a normal alarm while you are still half asleep. WakeMove replaces that single swipe with a short wake-up challenge: complete a few movements or read a phrase aloud. The alarm stops only after you finish the goal.

WakeMove is local-first. Alarm data and history stay on the device. Camera frames are processed locally by MediaPipe, and voice challenges run through the bundled offline Vosk model. Camera frames and recordings are not saved or uploaded.

### Features

- One-time and weekly repeating alarms with exact scheduling
- Full-screen lock-screen alarm, looping audio, and vibration
- Squat, jumping-jack, hands-up, and offline voice challenges
- CameraX + MediaPipe on-device body landmark detection
- Bundled Vosk offline speech recognition
- Up to three snoozes, followed by an optional “Start challenge now” action
- Four original calming alarm sounds with previews
- Three vibration patterns and three intensity levels
- Alarm history and a system capability Health Check
- Light, dark, follow-system, and Android 12+ dynamic color themes
- Complete Simplified Chinese and English interfaces
- In-app update checks, real download progress, SHA-256 verification, and Android installer guidance
- A deliberate 10-second emergency-stop gesture

### Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room
- AlarmManager and foreground services
- CameraX and MediaPipe Tasks Vision
- Vosk Android
- JUnit, Robolectric, and Compose UI Test

### Project structure

```text
WakeMove/
├─ android/     Android application
├─ website/     Product website
├─ shared/      Shared content
└─ docs/        Design and development documentation
```

### Build locally

Requirements: Android Studio, JDK 17, Android SDK 37, and an Android 10 (API 29) or newer device/emulator.

```powershell
git clone https://github.com/Ykedan/WakeMove.git
cd WakeMove\android
.\gradlew.bat assembleDebug
```

The debug APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

Release builds require your own local signing configuration. Keystores and passwords are never committed to this repository.

### Tests

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug
```

With an emulator or device connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

### Download

Download the latest officially signed beta APK from [GitHub Releases](https://github.com/Ykedan/WakeMove/releases/latest). WakeMove supports Android 10 and later.

WakeMove is currently in public beta and has not yet been published to an app store. Please report reproducible issues with the device model, Android version, and relevant screenshots in [Issues](https://github.com/Ykedan/WakeMove/issues).

---

## 简体中文

### 为什么做醒动

普通闹钟最容易在半睡半醒时被顺手关掉。WakeMove 把“关闭闹钟”换成一个短挑战：完成几次动作，或者完整说出一句话。只有真正清醒并完成目标，响铃才会结束。

WakeMove 坚持本地优先。闹钟与历史数据保存在设备上；相机画面由 MediaPipe 在本机处理；语音挑战使用内置 Vosk 离线模型。相机画面与录音不会被保存或上传。

### 主要功能

- 一次性与每周重复闹钟，支持精确调度
- 锁屏全屏响铃、循环铃声和震动
- 深蹲、开合跳、双手举高和离线语音挑战
- CameraX + MediaPipe 本地人体关键点识别
- 内置 Vosk 离线语音识别
- 最多 3 次贪睡，并可随时选择“立即挑战”
- 4 段原创舒缓铃声，支持试听
- 3 种震动节奏和 3 档震动力度
- 响铃历史与系统能力健康检查
- 浅色、深色、跟随系统和 Android 12+ 动态主题色
- 完整的简体中文与英文界面
- 应用内检查更新、真实下载进度、SHA-256 校验和系统安装引导
- 按住 10 秒的紧急停止通道

### 技术栈

- Kotlin、Jetpack Compose、Material 3
- Room
- AlarmManager 与前台服务
- CameraX 与 MediaPipe Tasks Vision
- Vosk Android
- JUnit、Robolectric、Compose UI Test

### 项目结构

```text
WakeMove/
├─ android/     Android 应用
├─ website/     产品官网
├─ shared/      跨端共享内容
└─ docs/        设计与开发文档
```

### 本地构建

需要 Android Studio、JDK 17、Android SDK 37，以及 Android 10（API 29）或更高版本的设备/模拟器。

```powershell
git clone https://github.com/Ykedan/WakeMove.git
cd WakeMove\android
.\gradlew.bat assembleDebug
```

Debug APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`。

Release 构建需要自行准备本地签名配置，密钥文件和密码不会提交到仓库。

### 测试

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug
```

连接真机或启动模拟器后，可运行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

### 下载体验

从 [GitHub Releases](https://github.com/Ykedan/WakeMove/releases/latest) 下载最新正式签名测试版。WakeMove 支持 Android 10 及以上版本。

WakeMove 目前处于公开测试阶段，尚未上架应用商店。欢迎在 [Issues](https://github.com/Ykedan/WakeMove/issues) 中提供复现步骤、设备型号、Android 版本和相关截图。

## License / 授权说明

No open-source license has been selected yet. All rights are reserved until a license is added.

本项目暂未选择开源许可证。在正式补充许可证前，代码与素材保留全部权利。

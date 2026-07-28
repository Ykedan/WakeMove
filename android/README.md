# WakeMove Android

WakeMove 是一个 Android 10（API 29）及以上可用的本地闹钟原型。闹钟响起后，需要完成深蹲、开合跳、双手举起或随机中文语音口令才能正常停止；没有普通“关闭闹钟”按钮。

> 当前交付的是开发测试版本。项目开发密钥只用于本地安装，不是应用商店生产签名。

## 第一次用 Android Studio 打开

1. 启动 Android Studio，选择 **Open**。
2. 选择仓库中的 `android` 子目录，而不是仓库根目录。
3. 等待右下角 Gradle Sync 完成。首次同步会下载依赖。
4. 打开 **Tools > Device Manager**。如果列表为空，点击 **+** 或 **Add a new device**，选择一个 Pixel 机型，再下载并选择 API 35 或更高的 x86_64 系统镜像。
5. 启动虚拟机，等待 Android 桌面出现。
6. 顶部运行配置选择 `app`，设备选择刚启动的虚拟机，然后点击绿色运行按钮。

项目固定使用 Gradle Wrapper，不需要单独安装 Gradle。命令行构建需要 Android Studio 自带的 JDK 和 Android SDK；本机 SDK 路径由不入 Git 的 `local.properties` 或 `ANDROID_HOME` 提供。

## 在 PowerShell 中构建和测试

从仓库的 `android` 子目录运行：

```powershell
$env:JAVA_HOME = 'C:\path\to\Android Studio\jbr' # 替换为本机 Android Studio 的 jbr 目录
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

.\gradlew.bat clean testDebugUnitTest lintDebug connectedDebugAndroidTest assembleDebug
```

`connectedDebugAndroidTest` 需要已经启动并解锁的虚拟机或真机。常见输出：

- 调试 APK：`app\build\outputs\apk\debug\app-debug.apk`
- 发布 APK：`app\build\outputs\apk\release\app-release.apk`
- 单元测试报告：`app\build\reports\tests\testDebugUnitTest\index.html`
- Android 仪器测试报告：`app\build\reports\androidTests\connected\debug\index.html`
- Lint 报告：`app\build\reports\lint-results-debug.html`

安装调试 APK：

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## 创建本机开发签名并构建发布 APK

仓库只提供模板，不包含密钥或密码。首次构建时：

1. 在仓库外创建一个仅供本项目开发测试的 JKS。文档统一用通用占位路径 `D:/path/outside/repository/wakemove-development.jks`；请把 `path/outside/repository` 替换为你自己选择的仓库外目录。
2. 将 `keystore.properties.example` 复制为 `keystore.properties`。
3. 在 `keystore.properties` 中填写 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`。
4. 确认 `keystore.properties` 和 JKS 都没有出现在 `git status` 中。
5. 运行：

```powershell
.\gradlew.bat assembleRelease
```

Windows 路径在 `.properties` 文件中应写成正斜杠，例如：

```properties
storeFile=D:/path/outside/repository/wakemove-development.jks
```

也可以把每个反斜杠写成双反斜杠，例如 `D:\\path\\outside\\repository\\wakemove-development.jks`。不要写单反斜杠路径；Java `Properties.load` 会把反斜杠当成转义符，导致实际读取的路径不正确。

缺少本地签名配置时，调试构建仍可运行；发布构建会给出明确错误并停止。不要把这个开发密钥当作商店生产密钥，也不要把密码粘贴到 issue、聊天、截图或构建日志中。

验证签名：

```powershell
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --print-certs `
  .\app\build\outputs\apk\release\app-release.apk
```

## 权限、验收与已知限制

- 权限用途和修复方法见 [docs/permissions.md](docs/permissions.md)。
- 模拟器证据、真机测试步骤和待填写矩阵见 [docs/device-acceptance.md](docs/device-acceptance.md)。
- 摄像头姿态识别、锁屏响铃、重启恢复、弱光和真实麦克风识别必须在物理 Android 设备上最终验收。模拟器测试不能替代这些结果。
- Android 无法绕过用户强制停止应用、关机或系统厂商的极端后台限制；“健康检查”页面会提示可检测的问题。
- WakeMove 当前**不支持 Direct Boot**：设备重启后、用户完成首次解锁前，不会从加密的 Room 数据库恢复闹钟。应用未声明 `LOCKED_BOOT_COMPLETED`，也不会把“首次解锁前恢复”误报为已支持；首次解锁后系统发送 `BOOT_COMPLETED`，应用才重新注册闹钟。

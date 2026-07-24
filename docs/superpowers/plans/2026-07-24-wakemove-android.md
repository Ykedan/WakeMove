# WakeMove Android MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a locally installable Android 10+ alarm app whose alarms can be dismissed by a squat, jumping-jack, hands-up, or random Chinese voice challenge.

**Architecture:** A single Android application uses small feature-focused Kotlin packages. Pure domain modules calculate schedules and recognize challenge state; Room persists alarms and sessions; platform adapters own AlarmManager, CameraX, MediaPipe, SpeechRecognizer, audio, vibration, notifications, and full-screen UI.

**Tech Stack:** JDK 17, Gradle 9.4.1 wrapper, Android Gradle Plugin 9.2.0, Kotlin 2.3.21, compile/target SDK 37, min SDK 29, Compose BOM 2026.06.00, Room 2.8.4, CameraX 1.5.3, MediaPipe Tasks Vision 0.10.35, JUnit, Robolectric, Compose UI Test.

## Global Constraints

- Support Android 10/API 29 and later; compile and target API 37.
- Use simplified Chinese copy and the approved warm-white/orange/coral “日出活力” visual system.
- Store alarms, settings, sessions, and history only on the device.
- Never persist camera frames or microphone recordings.
- Start camera or microphone capture only after the user starts a challenge.
- A normal dismiss button must not exist; challenge success is the standard stop path.
- Snooze defaults to 5 minutes and is limited to 3 uses per ringing session.
- Both sensors unavailable must expose a press-and-hold 10-second emergency stop and record `BYPASSED`.
- Every task must run its focused tests plus `.\gradlew.bat testDebugUnitTest lintDebug` before commit.

---

## Planned File Map

```text
android/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle/libs.versions.toml
├─ app/build.gradle.kts
├─ app/src/main/AndroidManifest.xml
├─ app/src/main/assets/pose_landmarker_lite.task
├─ app/src/main/java/com/wakemove/android/
│  ├─ WakeMoveApplication.kt
│  ├─ MainActivity.kt
│  ├─ data/AlarmDatabase.kt
│  ├─ data/AlarmDao.kt
│  ├─ data/AlarmEntities.kt
│  ├─ data/RoomAlarmRepository.kt
│  ├─ domain/AlarmModels.kt
│  ├─ domain/AlarmRepository.kt
│  ├─ domain/ScheduleCalculator.kt
│  ├─ scheduling/AndroidAlarmScheduler.kt
│  ├─ scheduling/AlarmReceiver.kt
│  ├─ scheduling/RescheduleReceiver.kt
│  ├─ ringing/RingingService.kt
│  ├─ ringing/RingingSessionController.kt
│  ├─ challenge/PoseModels.kt
│  ├─ challenge/MovementCounters.kt
│  ├─ challenge/PoseLandmarkerAdapter.kt
│  ├─ challenge/SpeechChallengeController.kt
│  ├─ health/AndroidHealthService.kt
│  └─ ui/...
├─ app/src/main/res/raw/default_alarm.ogg
└─ app/src/test/java/com/wakemove/android/...
shared/
├─ phrases/zh-CN.json
└─ design/tokens.json
```

## Task 1: Bootstrap the Android application and shared assets

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle/libs.versions.toml`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/wakemove/android/MainActivity.kt`
- Create: `shared/phrases/zh-CN.json`
- Create: `shared/design/tokens.json`
- Modify: `.gitignore`

**Interfaces:**
- Produces package `com.wakemove.android`, an installable debug app, and shared phrase/design JSON consumed by both clients.

- [ ] **Step 1: Verify the required toolchain is absent or available**

Run:

```powershell
java -version
Get-Command sdkmanager, adb -ErrorAction SilentlyContinue
```

Expected now: JDK 17 succeeds; Android SDK commands are absent. Install Android Studio stable with SDK Platform 37, Build Tools 36.0.0, Platform Tools, and command-line tools, then reopen PowerShell and verify `adb version`.

- [ ] **Step 2: Create the Gradle wrapper and pinned build configuration**

Use Android Studio’s bundled Gradle once to generate wrapper 9.4.1, then commit the wrapper. Configure:

```kotlin
// android/build.gradle.kts
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.3.21" apply false
}
```

The app configuration must set `namespace = "com.wakemove.android"`, `compileSdk = 37`, `minSdk = 29`, `targetSdk = 37`, Java/Kotlin target 17, Compose enabled, and test instrumentation runner `androidx.test.runner.AndroidJUnitRunner`. Pin Room to 2.8.4, CameraX to 1.5.3, MediaPipe Tasks Vision to 0.10.35, and use Compose BOM 2026.06.00; do not use dynamic Maven versions.

- [ ] **Step 3: Add a build smoke test**

Create `MainActivity.kt` with `WakeMoveTheme { Text("WakeMove") }`, then run:

```powershell
Set-Location D:\WakeMove\android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: all three tasks succeed and `app\build\outputs\apk\debug\app-debug.apk` exists.

- [ ] **Step 4: Add deterministic shared resources**

`shared/phrases/zh-CN.json` must be a JSON array containing 30 short phrases, including `"今天也要准时起床"` and `"完成挑战开始新一天"`. `shared/design/tokens.json` must define:

```json
{
  "background": "#FFF7ED",
  "surface": "#FFFFFF",
  "primary": "#F97316",
  "accent": "#FB7185",
  "text": "#292524",
  "ringingBackground": "#30170E"
}
```

- [ ] **Step 5: Commit**

```powershell
git add .gitignore android shared
git commit -m "build(android): bootstrap WakeMove app"
```

## Task 2: Define alarm models and schedule calculation

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/domain/AlarmModels.kt`
- Create: `android/app/src/main/java/com/wakemove/android/domain/ScheduleCalculator.kt`
- Test: `android/app/src/test/java/com/wakemove/android/domain/ScheduleCalculatorTest.kt`

**Interfaces:**
- Produces `enum class ChallengeType`, `data class Alarm`, `data class RingingSession`, `data class AlarmEvent`.
- Produces `ScheduleCalculator.nextOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime?`.

- [ ] **Step 1: Write failing schedule tests**

Cover a same-day future one-shot, past one-shot returning `null`, weekly rollover, and daylight-saving transition:

```kotlin
@Test fun weekly_alarm_rolls_to_selected_day() {
    val alarm = alarmAt(7, 30, repeatDays = setOf(DayOfWeek.MONDAY))
    val now = ZonedDateTime.parse("2026-07-24T10:00:00+08:00[Asia/Shanghai]")
    assertEquals(
        ZonedDateTime.parse("2026-07-27T07:30:00+08:00[Asia/Shanghai]"),
        ScheduleCalculator.nextOccurrence(alarm, now)
    )
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.ScheduleCalculatorTest"
```

Expected: compilation fails because `ScheduleCalculator` does not exist.

- [ ] **Step 3: Implement immutable domain types and calculator**

Use UUID strings, `LocalTime`, `Set<DayOfWeek>`, `Instant`, and the enums from the design. The calculator must iterate at most eight local dates, create the candidate with `ZonedDateTime.of(date, alarm.time, now.zone)`, require `candidate.isAfter(now)`, and return `null` for an expired one-shot alarm.

- [ ] **Step 4: Run domain tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.ScheduleCalculatorTest"
```

Expected: all schedule tests pass.

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/wakemove/android/domain android/app/src/test/java/com/wakemove/android/domain
git commit -m "feat(android): add alarm domain and scheduling rules"
```

## Task 3: Persist alarms, ringing sessions, events, and settings with Room

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/domain/AlarmRepository.kt`
- Create: `android/app/src/main/java/com/wakemove/android/data/AlarmEntities.kt`
- Create: `android/app/src/main/java/com/wakemove/android/data/AlarmDao.kt`
- Create: `android/app/src/main/java/com/wakemove/android/data/AlarmDatabase.kt`
- Create: `android/app/src/main/java/com/wakemove/android/data/RoomAlarmRepository.kt`
- Test: `android/app/src/test/java/com/wakemove/android/data/RoomAlarmRepositoryTest.kt`

**Interfaces:**
- Produces `AlarmRepository.observeAlarms(): Flow<List<Alarm>>`.
- Produces suspend functions `upsertAlarm`, `deleteAlarm`, `getAlarm`, `saveSession`, `activeSession`, `appendEvent`, `recentEvents`, and `clearHistory`.

- [ ] **Step 1: Write failing repository tests**

Use an in-memory Room database. Verify round-trip mapping, sorted alarms, session recovery, and cascade-safe history retention after deleting an alarm.

- [ ] **Step 2: Confirm tests fail**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.RoomAlarmRepositoryTest"
```

Expected: missing Room entities and repository.

- [ ] **Step 3: Implement schema version 1**

Create separate `alarms`, `ringing_sessions`, `alarm_events`, and `app_settings` tables. Store repeat days as a seven-bit integer and challenge type as its enum name. Do not use destructive migrations.

- [ ] **Step 4: Run repository and full unit tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.RoomAlarmRepositoryTest"
.\gradlew.bat testDebugUnitTest
```

Expected: both commands pass.

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/wakemove/android/data android/app/src/main/java/com/wakemove/android/domain/AlarmRepository.kt android/app/src/test/java/com/wakemove/android/data
git commit -m "feat(android): persist alarms and ringing history"
```

## Task 4: Implement exact scheduling, permission checks, and reboot recovery

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/scheduling/AlarmScheduler.kt`
- Create: `android/app/src/main/java/com/wakemove/android/scheduling/AndroidAlarmScheduler.kt`
- Create: `android/app/src/main/java/com/wakemove/android/scheduling/AlarmReceiver.kt`
- Create: `android/app/src/main/java/com/wakemove/android/scheduling/RescheduleReceiver.kt`
- Create: `android/app/src/main/java/com/wakemove/android/health/AndroidHealthService.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Test: `android/app/src/test/java/com/wakemove/android/scheduling/AndroidAlarmSchedulerTest.kt`

**Interfaces:**
- Produces `AlarmScheduler.schedule(alarm: Alarm, at: Instant)`, `cancel(alarmId: String)`, and `rescheduleAll()`.
- Produces `AndroidHealthService.snapshot(): HealthSnapshot`.

- [ ] **Step 1: Write failing scheduler tests**

With Robolectric shadows, verify stable request codes, `setAlarmClock` use, cancellation, and that `rescheduleAll()` schedules only enabled alarms with a future occurrence.

- [ ] **Step 2: Confirm failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.AndroidAlarmSchedulerTest"
```

- [ ] **Step 3: Implement platform scheduling**

Use immutable broadcast `PendingIntent`s keyed by a deterministic hash of alarm UUID. On API 31+, check `alarmManager.canScheduleExactAlarms()`. Register receivers for `BOOT_COMPLETED`, `TIME_CHANGED`, `TIMEZONE_CHANGED`, and `MY_PACKAGE_REPLACED`. The receiver must enqueue repository/scheduler work on `goAsync()` and always call `finish()`.

- [ ] **Step 4: Add manifest permissions and health reporting**

Declare `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `CAMERA`, `RECORD_AUDIO`, `VIBRATE`, and `WAKE_LOCK`. Health must distinguish `READY`, `ACTION_REQUIRED`, and `UNAVAILABLE`.

- [ ] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.AndroidAlarmSchedulerTest"
.\gradlew.bat lintDebug
git add android/app/src/main
git commit -m "feat(android): schedule exact alarms and recover after reboot"
```

## Task 5: Build the ringing session, foreground service, audio, vibration, and snooze

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/ringing/RingingSessionController.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ringing/RingingService.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ringing/AlarmAudioPlayer.kt`
- Create: `android/app/src/main/res/raw/default_alarm.ogg`
- Test: `android/app/src/test/java/com/wakemove/android/ringing/RingingSessionControllerTest.kt`

**Interfaces:**
- Produces `start(alarmId)`, `snooze()`, `complete()`, and `bypass()` operations.
- Exposes `StateFlow<RingingUiState>` with alarm, session, sound state, and remaining snoozes.

- [ ] **Step 1: Write failing session tests**

Verify start persists before playback, snooze increments to three then refuses, completion records `COMPLETED`, bypass records `BYPASSED`, and a repeating alarm is rescheduled exactly once.

- [ ] **Step 2: Confirm failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.RingingSessionControllerTest"
```

- [ ] **Step 3: Implement the controller**

Serialize state transitions with `Mutex`. Make terminal transitions idempotent by checking session status in the repository transaction. Use injected `Clock`, `AlarmAudioPlayer`, vibrator, and `AlarmScheduler`.

- [ ] **Step 4: Implement foreground service**

Create a high-importance alarm notification channel, acquire a bounded partial wake lock, call `startForeground` immediately, start looping alarm audio with alarm audio attributes, and launch a full-screen ringing intent. Release audio, vibration, and wake lock on terminal transition.

- [ ] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.RingingSessionControllerTest"
.\gradlew.bat lintDebug
git add android/app/src/main/java/com/wakemove/android/ringing android/app/src/main/res/raw
git commit -m "feat(android): add reliable ringing and snooze sessions"
```

## Task 6: Implement pure movement counters

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/challenge/PoseModels.kt`
- Create: `android/app/src/main/java/com/wakemove/android/challenge/MovementCounters.kt`
- Test: `android/app/src/test/java/com/wakemove/android/challenge/MovementCountersTest.kt`

**Interfaces:**
- Produces `PoseFrame(timestampMs, landmarks)`.
- Produces `MovementCounter.update(frame): ChallengeProgress`.
- Produces `SquatCounter`, `JumpingJackCounter`, and `HandsUpCounter`.

- [ ] **Step 1: Write failing synthetic-frame tests**

Use explicit standing, squat-bottom, jack-open, jack-closed, and hands-up landmark fixtures. Verify incomplete movement, landmark jitter, low visibility, and unrelated movement do not increment.

- [ ] **Step 2: Confirm failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.MovementCountersTest"
```

- [ ] **Step 3: Implement counters**

Calculate angles with a shared `angle(a, vertex, c)` function. Require visibility `>= 0.65`, three stable frames per phase, and a 350 ms cooldown. Squat transitions standing → bottom → standing; jumping jack transitions closed → open → closed; hands-up requires both wrists above the nose for 2,000 ms.

- [ ] **Step 4: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.MovementCountersTest"
```

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/wakemove/android/challenge/PoseModels.kt android/app/src/main/java/com/wakemove/android/challenge/MovementCounters.kt android/app/src/test/java/com/wakemove/android/challenge
git commit -m "feat(android): recognize dismissal movements"
```

## Task 7: Connect CameraX and MediaPipe to the movement counters

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/challenge/PoseLandmarkerAdapter.kt`
- Create: `android/app/src/main/java/com/wakemove/android/challenge/CameraChallengeController.kt`
- Create: `android/app/src/main/assets/pose_landmarker_lite.task`
- Test: `android/app/src/test/java/com/wakemove/android/challenge/CameraChallengeControllerTest.kt`

**Interfaces:**
- Produces `CameraChallengeController.start(type, targetCount)`, `progress: StateFlow<ChallengeProgress>`, and `close()`.

- [ ] **Step 1: Write failing adapter/controller tests**

Inject a fake landmark source; verify target completion, low-light/no-person guidance, 60-second fallback availability, and resource release.

- [ ] **Step 2: Confirm failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.CameraChallengeControllerTest"
```

- [ ] **Step 3: Implement live-stream inference**

Bind CameraX `Preview` and `ImageAnalysis` to the ringing lifecycle. Use `STRATEGY_KEEP_ONLY_LATEST`. Configure MediaPipe `RunningMode.LIVE_STREAM`, one pose, 0.5 detection/presence/tracking confidence, and no segmentation mask. Convert each result into `PoseFrame`; close every `ImageProxy` in `finally`.

- [ ] **Step 4: Add the official lite model and license record**

Download the official `pose_landmarker_lite.task`, record its source and license in `android/THIRD_PARTY_NOTICES.md`, and verify its SHA-256 in the commit message body.

- [ ] **Step 5: Verify on unit test and physical device, then commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.CameraChallengeControllerTest"
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
git add android/app/src/main android/THIRD_PARTY_NOTICES.md
git commit -m "feat(android): connect camera pose inference"
```

## Task 8: Add random Chinese speech challenges

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/challenge/PhraseProvider.kt`
- Create: `android/app/src/main/java/com/wakemove/android/challenge/SpeechNormalizer.kt`
- Create: `android/app/src/main/java/com/wakemove/android/challenge/SpeechChallengeController.kt`
- Test: `android/app/src/test/java/com/wakemove/android/challenge/SpeechNormalizerTest.kt`
- Test: `android/app/src/test/java/com/wakemove/android/challenge/SpeechChallengeControllerTest.kt`

**Interfaces:**
- Produces `SpeechNormalizer.normalize(text: String): String`.
- Produces `SpeechChallengeController.start(phrase)`, `state`, `retry()`, and `close()`.

- [ ] **Step 1: Write failing normalization and controller tests**

Verify spaces/punctuation removal, full-width conversion, Chinese/Arabic digit normalization, candidate-list matching, wrong phrase rejection, network error fallback, and resource release.

- [ ] **Step 2: Confirm failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*Speech*Test"
```

- [ ] **Step 3: Implement phrase loading and normalization**

Package the shared phrase file into Android assets during the build. Seed selection from `SecureRandom`. Normalize with Unicode NFKC, remove punctuation/whitespace, and map 零 through 九 to digits before equality matching.

- [ ] **Step 4: Wrap Android SpeechRecognizer**

Use `LANGUAGE_MODEL_FREE_FORM`, locale `zh-CN`, partial results for display, and final candidate results for matching. Map network, no-match, permission, and service-unavailable errors into explicit UI states. Destroy the recognizer in `close()`.

- [ ] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*Speech*Test"
git add android/app/src/main/java/com/wakemove/android/challenge android/app/src/test/java/com/wakemove/android/challenge android/app/build.gradle.kts
git commit -m "feat(android): add Chinese voice dismissal"
```

## Task 9: Build alarm list, editor, theme, and navigation

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/ui/theme/WakeMoveTheme.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/navigation/WakeMoveNavHost.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListScreen.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmEditorScreen.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmViewModels.kt`
- Test: `android/app/src/androidTest/java/com/wakemove/android/ui/AlarmEditorTest.kt`

**Interfaces:**
- Produces list/editor routes and view-model actions for create, update, enable, disable, and delete.

- [ ] **Step 1: Write failing Compose tests**

Test required time, weekday toggles, challenge selection, target count visibility, save enabling only when health requirements pass, and delete confirmation.

- [ ] **Step 2: Confirm tests fail**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

- [ ] **Step 3: Implement the approved UI**

Use Material 3 components with shared colors, 24 dp cards, large time typography, orange/coral primary actions, and simplified Chinese labels. Keep screens and view models separate; view models call repository/scheduler interfaces only.

- [ ] **Step 4: Run UI and lint checks**

```powershell
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/wakemove/android/ui android/app/src/androidTest
git commit -m "feat(android): add alarm management interface"
```

## Task 10: Build ringing, challenge, history, health, settings, and onboarding UI

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/ui/ringing/RingingScreen.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/ringing/CameraChallengeScreen.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/ringing/SpeechChallengeScreen.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/history/HistoryScreen.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/health/HealthScreen.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/settings/SettingsScreen.kt`
- Create: `android/app/src/main/java/com/wakemove/android/ui/onboarding/OnboardingScreen.kt`
- Test: `android/app/src/androidTest/java/com/wakemove/android/ui/RingingFlowTest.kt`

**Interfaces:**
- Consumes ringing, challenge, repository, and health state flows.
- Produces complete user flows, including 10-second emergency hold.

- [ ] **Step 1: Write failing flow tests**

Verify no ordinary dismiss control, three snoozes maximum, challenge completion, fallback after an unavailable sensor, emergency hold requiring 10 continuous seconds, history result labels, and health repair intents.

- [ ] **Step 2: Confirm failure**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

- [ ] **Step 3: Implement screens and accessibility**

Keep ringing time and progress visible. Use a high-contrast dark ringing background, live landmark overlay, explicit camera placement guidance, microphone listening state, and content descriptions. Cancel emergency hold progress on pointer-up or pointer cancellation.

- [ ] **Step 4: Verify complete UI**

```powershell
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat testDebugUnitTest lintDebug
```

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/wakemove/android/ui android/app/src/androidTest
git commit -m "feat(android): complete ringing and support screens"
```

## Task 11: Package and verify the Android MVP

**Files:**
- Create: `android/README.md`
- Create: `android/docs/permissions.md`
- Create: `android/docs/device-acceptance.md`
- Create: `android/keystore.properties.example`
- Modify: `android/app/build.gradle.kts`

**Interfaces:**
- Produces a development-signed APK and reproducible build/test instructions.

- [ ] **Step 1: Run the complete automated suite**

```powershell
Set-Location D:\WakeMove\android
.\gradlew.bat clean testDebugUnitTest lintDebug connectedDebugAndroidTest assembleDebug
```

Expected: every task succeeds and the debug APK exists.

- [ ] **Step 2: Execute physical-device alarm matrix**

Record actual timestamps and outcomes for unlocked, locked, app-swiped-away, rebooted, offline, revoked-camera, revoked-microphone, and low-light scenarios. Test all three movements 20 times and voice 20 times; require at least 18 correct completions per challenge and zero false dismissals from unrelated motion/wrong phrases.

- [ ] **Step 3: Build the installable artifact**

Create a project development keystore outside Git, reference it through ignored `keystore.properties`, and run:

```powershell
.\gradlew.bat assembleRelease
```

Expected: `android\app\build\outputs\apk\release\app-release.apk`.

- [ ] **Step 4: Verify clean install, upgrade, and uninstall**

Use `adb install`, `adb install -r`, and `adb uninstall com.wakemove.android`; confirm local data survives upgrade and is removed on uninstall.

- [ ] **Step 5: Commit**

```powershell
git add android/README.md android/docs android/keystore.properties.example android/app/build.gradle.kts
git commit -m "docs(android): add build and acceptance evidence"
```

## Official References

- Android Gradle Plugin 9.2 compatibility: https://developer.android.com/build/releases/agp-9-2-0-release-notes
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Room 2.8.4: https://developer.android.com/jetpack/androidx/releases/room
- CameraX releases: https://developer.android.com/jetpack/androidx/releases/camera
- MediaPipe Pose Landmarker for Android: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android
- Exact alarms: https://developer.android.com/develop/background-work/services/alarms/schedule
- Full-screen intents: https://developer.android.com/develop/ui/views/notifications/build-notification#urgent-message

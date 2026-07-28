# WakeMove Morning Sunrise UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the prototype-like alarm list and alarm editor with the approved warm “morning sunrise dashboard” design while preserving all alarm behavior and automation contracts.

**Architecture:** Keep navigation and view-model contracts intact. Add one pure presentation helper for selecting the next enabled alarm, split visual-only Compose elements into focused component files, and extend the existing theme with reusable warm color and shape tokens. Verify presentation logic with JVM tests and user interaction/semantics with the existing Compose instrumentation suite.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material 3 with Compose BOM 2026.06.00, Android Gradle Plugin 9.2.0, JUnit 4, Compose UI Test, Android emulator.

## Global Constraints

- Only the alarm home screen, create/edit alarm screen, visible bottom navigation styling, and their shared design tokens are in scope.
- Alarm scheduling, permissions, camera recognition, speech recognition, snooze, persistence, save, and delete rules must not change.
- Existing test tags must remain: `add_alarm`, `alarm_card_<id>`, `alarm_enabled_<id>`, `alarm_time`, `weekday_<DAY>`, `challenge_<TYPE>`, `target_count`, `save_alarm`, `submission_progress`, and `confirm_delete`.
- All primary touch targets must remain at least 48 dp.
- Selected state must not rely on color alone.
- The editor must remain vertically usable on small screens.
- Do not add network images, remote assets, dark mode, or a new navigation framework.
- Existing unrelated `.idea/` and `.tooling/` files must not be staged or committed.
- Run every `.\gradlew.bat` command from `D:\WakeMove\android`; run every `git` command from `D:\WakeMove`.

---

## File Structure

- Modify `android/gradle/libs.versions.toml` to expose the official Compose extended icon library.
- Modify `android/app/build.gradle.kts` to consume the icon library under the existing Compose BOM.
- Modify `android/app/src/main/java/com/wakemove/android/ui/theme/WakeMoveTheme.kt` to define the approved sunrise palette, typography, and rounded shapes.
- Create `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListPresentation.kt` for deterministic next-alarm selection.
- Create `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListComponents.kt` for the greeting header, next-alarm hero, alarm card, empty state, and add button.
- Modify `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListScreen.kt` to assemble the new home screen without owning detailed component styling.
- Create `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmEditorComponents.kt` for weekday circles, challenge cards, target stepper, alert card, and editor section containers.
- Modify `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmEditorScreen.kt` to assemble the redesigned editor while preserving callbacks and tags.
- Modify `android/app/src/main/java/com/wakemove/android/ui/navigation/WakeMoveNavHost.kt` to use Material icons and the selected orange navigation pill.
- Create `android/app/src/test/java/com/wakemove/android/ui/alarms/AlarmListPresentationTest.kt` for next-alarm calculation.
- Modify `android/app/src/androidTest/java/com/wakemove/android/ui/AlarmEditorTest.kt` for the new home/editor content and stepper behavior.
- Modify `android/app/src/androidTest/java/com/wakemove/android/ui/AlarmNavigationTest.kt` to protect create/edit/navigation flows after the visual refactor.

---

### Task 1: Sunrise Design Tokens and Navigation Icons

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/java/com/wakemove/android/ui/theme/WakeMoveTheme.kt`
- Modify: `android/app/src/main/java/com/wakemove/android/ui/navigation/WakeMoveNavHost.kt`
- Test: `android/app/src/androidTest/java/com/wakemove/android/ui/AlarmNavigationTest.kt`

**Interfaces:**
- Produces: named theme colors `WakeMoveBackground`, `WakeMoveSurface`, `WakeMoveSunrise`, `WakeMoveSunlight`, `WakeMovePeach`, `WakeMoveText`, `WakeMoveMutedText`, and `WakeMoveErrorContainer`.
- Preserves: `WakeMoveTheme(content: @Composable () -> Unit)` and every `MainDestination.route`.
- Consumes: official `androidx.compose.material.icons.Icons` symbols from `material-icons-extended`.

- [ ] **Step 1: Add a failing navigation semantics test**

Add a test to `AlarmNavigationTest.kt` that launches the normal `WakeMoveNavHost` fixture and checks icon content descriptions:

```kotlin
composeRule.onNodeWithContentDescription("闹钟").assertIsDisplayed()
composeRule.onNodeWithContentDescription("历史").assertIsDisplayed()
composeRule.onNodeWithContentDescription("健康检查").assertIsDisplayed()
```

The icon descriptions deliberately match the visible labels so TalkBack receives a stable destination name.

- [ ] **Step 2: Run the focused instrumentation test and confirm red**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.wakemove.android.ui.AlarmNavigationTest
```

Expected: FAIL because the current navigation icons are plain `Text` nodes without the required content descriptions.

- [ ] **Step 3: Add the icon dependency**

Add this catalog entry to `android/gradle/libs.versions.toml`:

```toml
androidx-compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
```

Add this dependency beside Material 3 in `android/app/build.gradle.kts`:

```kotlin
implementation(libs.androidx.compose.material.icons.extended)
```

- [ ] **Step 4: Define the approved theme tokens**

Update `WakeMoveTheme.kt` with this palette and component geometry:

```kotlin
val WakeMoveBackground = Color(0xFFFFF8F0)
val WakeMoveSurface = Color(0xFFFFFFFF)
val WakeMoveSunrise = Color(0xFFFF7A1A)
val WakeMoveSunlight = Color(0xFFFFC45C)
val WakeMovePeach = Color(0xFFFFE8D2)
val WakeMoveText = Color(0xFF2F261F)
val WakeMoveMutedText = Color(0xFF75675C)
val WakeMoveErrorContainer = Color(0xFFFFE8E6)
```

Map `primary`, `primaryContainer`, `secondary`, `background`, `surface`, `surfaceVariant`, `onSurfaceVariant`, and `errorContainer` from these tokens. Define Material typography with a restrained `headlineMedium`, a strong time-oriented `displayMedium`, and readable body styles. Set medium, large, and extra-large shapes to 16 dp, 24 dp, and 28 dp.

- [ ] **Step 5: Replace navigation glyph text with Material icons**

Change `MainDestination` to store an `ImageVector`:

```kotlin
private enum class MainDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    ALARMS(ROUTE_ALARMS, "闹钟", Icons.Outlined.Alarm),
    HISTORY(ROUTE_HISTORY, "历史", Icons.Outlined.History),
    HEALTH(ROUTE_HEALTH, "健康检查", Icons.Outlined.HealthAndSafety),
}
```

Render each icon as:

```kotlin
Icon(
    imageVector = item.icon,
    contentDescription = item.label,
)
```

Keep the existing navigation callbacks. Use `WakeMovePeach` as the selected indicator, `WakeMoveSunrise` for selected icon/text, and `WakeMoveMutedText` for unselected content.

- [ ] **Step 6: Run the focused test and compile checks**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.wakemove.android.ui.AlarmNavigationTest
.\gradlew.bat :app:compileDebugKotlin
```

Expected: the navigation test passes and Kotlin compilation succeeds.

- [ ] **Step 7: Commit the theme and navigation foundation**

```powershell
git add android/gradle/libs.versions.toml `
  android/app/build.gradle.kts `
  android/app/src/main/java/com/wakemove/android/ui/theme/WakeMoveTheme.kt `
  android/app/src/main/java/com/wakemove/android/ui/navigation/WakeMoveNavHost.kt `
  android/app/src/androidTest/java/com/wakemove/android/ui/AlarmNavigationTest.kt
git commit -m "style(android): add sunrise theme and navigation icons"
```

---

### Task 2: Deterministic Next-Alarm Presentation

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListPresentation.kt`
- Create: `android/app/src/test/java/com/wakemove/android/ui/alarms/AlarmListPresentationTest.kt`

**Interfaces:**
- Consumes: `ScheduleCalculator.nextOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime?`.
- Produces: `internal data class NextAlarmUiModel(val alarm: Alarm, val occurrence: ZonedDateTime)`.
- Produces: `internal fun findNextEnabledAlarm(alarms: List<Alarm>, now: ZonedDateTime): NextAlarmUiModel?`.

- [ ] **Step 1: Write failing unit tests for presentation selection**

Create `AlarmListPresentationTest.kt` with fixed `Asia/Shanghai` times and helpers that build alarms. Cover these cases:

```kotlin
@Test
fun `returns null when no alarm is enabled`() {
    val result = findNextEnabledAlarm(
        alarms = listOf(alarm("off", 7, 0, enabled = false)),
        now = ZonedDateTime.of(2026, 7, 27, 6, 0, 0, 0, zone),
    )
    assertNull(result)
}

@Test
fun `selects earliest real occurrence rather than earliest clock time`() {
    val now = ZonedDateTime.of(2026, 7, 27, 8, 0, 0, 0, zone)
    val laterToday = alarm("today", 9, 0, days = setOf(DayOfWeek.MONDAY))
    val earlierTomorrow = alarm("tomorrow", 7, 0, days = setOf(DayOfWeek.TUESDAY))

    val result = findNextEnabledAlarm(listOf(earlierTomorrow, laterToday), now)

    assertEquals("today", result?.alarm?.id)
    assertEquals(now.toLocalDate(), result?.occurrence?.toLocalDate())
}

@Test
fun `one shot past its time is excluded`() {
    val now = ZonedDateTime.of(2026, 7, 27, 8, 0, 0, 0, zone)
    val result = findNextEnabledAlarm(
        listOf(alarm("expired", 7, 0, days = emptySet())),
        now,
    )
    assertNull(result)
}
```

Also add a tie-break test that returns the alarm with the lexicographically smaller `id` when two occurrences are identical, ensuring stable UI.

- [ ] **Step 2: Run the unit test and confirm red**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests com.wakemove.android.ui.alarms.AlarmListPresentationTest
```

Expected: FAIL because `NextAlarmUiModel` and `findNextEnabledAlarm` do not exist.

- [ ] **Step 3: Implement the smallest pure selector**

Create `AlarmListPresentation.kt`:

```kotlin
internal data class NextAlarmUiModel(
    val alarm: Alarm,
    val occurrence: ZonedDateTime,
)

internal fun findNextEnabledAlarm(
    alarms: List<Alarm>,
    now: ZonedDateTime,
): NextAlarmUiModel? = alarms
    .asSequence()
    .filter(Alarm::enabled)
    .mapNotNull { alarm ->
        ScheduleCalculator.nextOccurrence(alarm, now)
            ?.let { occurrence -> NextAlarmUiModel(alarm, occurrence) }
    }
    .minWithOrNull(
        compareBy<NextAlarmUiModel> { it.occurrence }
            .thenBy { it.alarm.id },
    )
```

Keep formatting helpers separate from scheduling math; do not duplicate `ScheduleCalculator`.

- [ ] **Step 4: Run the focused and full JVM suites**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests com.wakemove.android.ui.alarms.AlarmListPresentationTest
.\gradlew.bat :app:testDebugUnitTest
```

Expected: both commands pass.

- [ ] **Step 5: Commit the presentation helper**

```powershell
git add android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListPresentation.kt `
  android/app/src/test/java/com/wakemove/android/ui/alarms/AlarmListPresentationTest.kt
git commit -m "feat(android): select the next enabled alarm for display"
```

---

### Task 3: Morning Dashboard Alarm Home

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListComponents.kt`
- Modify: `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListScreen.kt`
- Modify: `android/app/src/androidTest/java/com/wakemove/android/ui/AlarmEditorTest.kt`

**Interfaces:**
- Consumes: `findNextEnabledAlarm(alarms, now)`.
- Extends: `AlarmListScreen` with optional `nowProvider: () -> ZonedDateTime = { ZonedDateTime.now() }` for deterministic UI tests.
- Preserves: all existing screen callbacks and test tags.
- Produces: additional stable tags `next_alarm_card`, `empty_alarm_state`, and `settings_button`.

- [ ] **Step 1: Add failing Compose tests for the new dashboard states**

Extend `AlarmEditorTest.kt` with:

```kotlin
@Test
fun emptyAlarmListShowsSunriseCallToAction() {
    composeRule.setContent {
        WakeMoveTheme {
            AlarmListScreen(
                alarms = emptyList(),
                onCreateAlarm = {},
                onEditAlarm = {},
                onEnabledChange = { _, _ -> },
                onOpenSettings = {},
            )
        }
    }

    composeRule.onNodeWithTag("empty_alarm_state").assertIsDisplayed()
    composeRule.onNodeWithText("还没有闹钟").assertIsDisplayed()
    composeRule.onNodeWithText("设置第一个闹钟").assertIsDisplayed()
    composeRule.onNodeWithTag("next_alarm_card").assertDoesNotExist()
}
```

Add a deterministic enabled-alarm test:

```kotlin
composeRule.onNodeWithTag("next_alarm_card").assertIsDisplayed()
composeRule.onNodeWithText("下一次唤醒").assertIsDisplayed()
composeRule.onNodeWithText("07:30").assertIsDisplayed()
```

Pass a fixed Monday 06:00 `nowProvider`. Add an all-disabled test that asserts “开启一个闹钟，迎接新的早晨” and no `next_alarm_card`.

- [ ] **Step 2: Run the focused Compose class and confirm red**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.wakemove.android.ui.AlarmEditorTest
```

Expected: FAIL because the new tags and copy do not exist.

- [ ] **Step 3: Create focused home-screen components**

Create `AlarmListComponents.kt` with these private or internal composables:

```kotlin
@Composable internal fun MorningHeader(onOpenSettings: () -> Unit, enabled: Boolean)
@Composable internal fun NextAlarmHero(model: NextAlarmUiModel, onClick: () -> Unit)
@Composable internal fun DisabledAlarmHero()
@Composable internal fun SunriseEmptyState(onCreateAlarm: () -> Unit, enabled: Boolean)
@Composable internal fun SunriseAlarmCard(
    alarm: Alarm,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    enabled: Boolean,
)
@Composable internal fun AddAlarmButton(onClick: () -> Unit, enabled: Boolean)
```

Implementation requirements:

- Use `Brush.linearGradient(listOf(WakeMoveSunlight, WakeMoveSunrise))` for the hero.
- Draw the empty sunrise with `Canvas`: one warm half-circle and short rays; provide surrounding text semantics rather than making the decoration focusable.
- Use `IconButton` with `Icons.Outlined.Settings` and tag `settings_button`.
- Put tag `next_alarm_card` on the clickable hero.
- Put tag `empty_alarm_state` on the empty-state container.
- Put the existing `add_alarm` tag on exactly one visible call-to-action in each state.
- Keep `alarm_card_<id>` and `alarm_enabled_<id>` on the list cards.
- Show repeat text and challenge text using the existing Chinese description helpers moved into this component file as `internal` helpers.

- [ ] **Step 4: Recompose `AlarmListScreen` around the dashboard**

Replace the floating-action-button scaffold with a single `LazyColumn`:

```kotlin
val now = nowProvider()
val nextAlarm = remember(alarms, now) { findNextEnabledAlarm(alarms, now) }
```

Structure the list as:

1. `MorningHeader`
2. either `SunriseEmptyState`, `NextAlarmHero`, or `DisabledAlarmHero`
3. “我的闹钟” and count
4. alarm cards
5. `AddAlarmButton` when alarms are non-empty

Show operation errors in a warm error card. Keep bottom content padding large enough for the navigation bar, and prevent every create/edit/toggle callback while `operationState.isInFlight`.

- [ ] **Step 5: Run focused tests and correct semantics regressions**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.wakemove.android.ui.AlarmEditorTest
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.wakemove.android.ui.AlarmNavigationTest
```

Expected: both classes pass, including the existing create, edit, and toggle assertions.

- [ ] **Step 6: Commit the home redesign**

```powershell
git add android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListComponents.kt `
  android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmListScreen.kt `
  android/app/src/androidTest/java/com/wakemove/android/ui/AlarmEditorTest.kt
git commit -m "style(android): build the morning alarm dashboard"
```

---

### Task 4: Warm Alarm Editor Components

**Files:**
- Create: `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmEditorComponents.kt`
- Modify: `android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmEditorScreen.kt`
- Modify: `android/app/src/androidTest/java/com/wakemove/android/ui/AlarmEditorTest.kt`
- Modify: `android/app/src/androidTest/java/com/wakemove/android/ui/AlarmNavigationTest.kt`

**Interfaces:**
- Preserves: `AlarmEditorScreen` public parameters and every existing callback.
- Produces: target controls `target_decrease` and `target_increase`.
- Preserves: `target_count` on the stepper container and existing weekday/challenge tags and semantics.

- [ ] **Step 1: Add failing tests for the target stepper and challenge cards**

Add to `AlarmEditorTest.kt`:

```kotlin
@Test
fun targetStepperChangesCountAndStopsAtOne() {
    var state by mutableStateOf(
        AlarmEditorUiState(
            timeText = "07:30",
            targetCount = 2,
            health = readyHealth,
        ),
    )
    composeRule.setContent {
        WakeMoveTheme {
            AlarmEditorScreen(
                state = state,
                onTimeChange = {},
                onDayToggle = {},
                onChallengeSelected = { state = state.copy(challengeType = it) },
                onTargetCountChange = { state = state.copy(targetCount = it) },
                onSave = {},
                onDelete = {},
                onBack = {},
            )
        }
    }

    composeRule.onNodeWithTag("target_decrease").performScrollTo().performClick()
    composeRule.onNodeWithText("1").assertIsDisplayed()
    composeRule.onNodeWithTag("target_decrease").assertIsNotEnabled()
    composeRule.onNodeWithTag("target_increase").performClick()
    composeRule.onNodeWithText("2").assertIsDisplayed()
}
```

Add assertions that the selected challenge exposes selected semantics, its description is visible, and switching to `VOICE_PHRASE` removes the entire `target_count` stepper.

- [ ] **Step 2: Run the focused editor tests and confirm red**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.wakemove.android.ui.AlarmEditorTest
```

Expected: FAIL because `target_decrease`, `target_increase`, and challenge descriptions do not exist.

- [ ] **Step 3: Create reusable editor visual components**

Create `AlarmEditorComponents.kt` with:

```kotlin
@Composable internal fun SunriseTimeCard(...)
@Composable internal fun WeekdaySelector(...)
@Composable internal fun ChallengeSelector(...)
@Composable internal fun TargetStepper(count: Int, onCountChange: (Int) -> Unit)
@Composable internal fun EditorAlertCard(message: String)
@Composable internal fun EditorCard(title: String, content: @Composable ColumnScope.() -> Unit)
```

Implementation rules:

- `SunriseTimeCard` uses the same hero gradient and contains the existing `OutlinedTextField` tagged `alarm_time`.
- Weekday buttons are circular, 48 dp, tagged `weekday_<DAY>`, and expose `selected` plus `Role.Checkbox`.
- Challenge cards fill the width, contain an icon, title, description, orange border, and a check icon when selected. Keep `challenge_<TYPE>` and `selected` semantics.
- `TargetStepper` places `target_count` on the outer row, `target_decrease` and `target_increase` on 48 dp icon buttons, disables decrease at 1, and calls `onCountChange(count - 1)` or `onCountChange(count + 1)`.
- `EditorAlertCard` uses `WakeMoveErrorContainer` and an error icon.

- [ ] **Step 4: Recompose `AlarmEditorScreen`**

Use a `Scaffold` with:

- transparent/warm `TopAppBar`
- `Icons.AutoMirrored.Outlined.ArrowBack`
- scrollable content for time card, label, weekday selector, challenge selector, target stepper, error cards, and delete action
- a `bottomBar` surface containing the existing `save_alarm` button and `submission_progress`

The label field must show:

```kotlin
placeholder = { Text("例如：上班、晨跑、早课") }
```

Keep the delete confirmation dialog and `confirm_delete`. Add bottom content padding so the fixed save bar never obscures the final delete action.

- [ ] **Step 5: Run editor and end-to-end navigation tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.wakemove.android.ui.AlarmEditorTest
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.wakemove.android.ui.AlarmNavigationTest
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.wakemove.android.ui.AlarmRestorationTest
```

Expected: all three classes pass, proving create, edit, restore, save, delete, weekdays, challenge selection, and stepper behavior.

- [ ] **Step 6: Commit the editor redesign**

```powershell
git add android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmEditorComponents.kt `
  android/app/src/main/java/com/wakemove/android/ui/alarms/AlarmEditorScreen.kt `
  android/app/src/androidTest/java/com/wakemove/android/ui/AlarmEditorTest.kt `
  android/app/src/androidTest/java/com/wakemove/android/ui/AlarmNavigationTest.kt
git commit -m "style(android): redesign the alarm editor"
```

---

### Task 5: Full Verification and Emulator Visual Review

**Files:**
- Modify only files from Tasks 1–4 if verification reveals a scoped regression.
- Do not commit emulator screenshots unless explicitly requested.

**Interfaces:**
- Validates: JVM behavior, Compose interactions, lint, debug APK, release APK, and four approved visual states.
- Produces: refreshed debug and release APKs under the normal Gradle output directories.

- [ ] **Step 1: Run the complete automated verification**

From `D:\WakeMove\android`, run:

```powershell
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug assembleRelease
```

Expected: `BUILD SUCCESSFUL`, with zero failing unit tests, instrumentation tests, or lint errors.

- [ ] **Step 2: Install the debug build without deleting emulator data**

Run:

```powershell
$adb = 'C:\Users\13237\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb install -r 'D:\WakeMove\android\app\build\outputs\apk\debug\app-debug.apk'
& $adb shell monkey -p com.wakemove.android -c android.intent.category.LAUNCHER 1
```

Expected: installation prints `Success`, and the foreground activity is `com.wakemove.android/.MainActivity`.

- [ ] **Step 3: Manually review the approved visual states**

On the emulator, verify:

1. Empty home: sunrise graphic, compact header, first-alarm button, icon navigation.
2. Populated home: next-alarm hero, at least two alarm cards, challenge tags, enable switches, add button.
3. New alarm: sunrise time card, weekday circles, four challenge cards, stepper, fixed save button.
4. Existing alarm: populated fields, low-emphasis delete action, delete confirmation.

Also inspect at the emulator’s smallest available portrait size to ensure the editor scrolls and the bottom save bar does not cover content.

- [ ] **Step 4: Capture temporary visual evidence**

For each state, use:

```powershell
& $adb shell screencap -p /sdcard/wakemove-ui.png
& $adb pull /sdcard/wakemove-ui.png "$env:TEMP\wakemove-ui.png"
```

Open each capture at original resolution. If any spacing, clipping, contrast, or hierarchy issue is visible, make one scoped adjustment and rerun the affected Compose test plus `lintDebug`.

- [ ] **Step 5: Confirm release artifact and checksum**

Run:

```powershell
Get-FileHash `
  -LiteralPath 'D:\WakeMove\android\app\build\outputs\apk\release\app-release.apk' `
  -Algorithm SHA256
```

Record the fresh hash in the handoff response. Do not overwrite or expose the signing keystore.

- [ ] **Step 6: Review the final Git scope**

Run:

```powershell
git status --short
git diff --check
git log --oneline -6
```

Expected: only the user’s pre-existing untracked `.idea/` and `.tooling/` remain; no implementation file is unstaged; whitespace check is clean.

- [ ] **Step 7: Commit any final scoped visual correction**

Only when Step 3 required a correction:

```powershell
git add android/app/src/main android/app/src/test android/app/src/androidTest
git commit -m "fix(android): polish sunrise UI visual details"
```

If no correction was needed, do not create an empty commit.

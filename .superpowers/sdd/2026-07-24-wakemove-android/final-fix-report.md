# WakeMove Android final reliability fix wave

Baseline reviewed: `d2b61d9` (final branch review; the reviewer findings were
provided directly to this fix wave rather than stored in a separate file).

## Finding-to-fix map

| Finding | RED evidence | Implemented fix | Regression coverage |
| --- | --- | --- | --- |
| C1 overlap delivery was dropped | a second alarm returned `false` while another session was active | `replaceActiveSession` atomically marks the prior session `MISSED`, writes its event/one-shot update, creates the new session, and retries any prior pending repeat | `RingingSessionControllerTest` overlap (ringing and snoozed) plus `RoomAlarmRepositoryTest` transaction |
| C2 exact-alarm permission grants were ignored | the permission-state broadcast was not declared or handled | manifest registration plus API-31-aware receiver guard reschedule enabled alarms and pending sessions only after exact alarms are allowed | `RescheduleReceiverTest` grant and revocation cases |
| C3 past one-shot alarms could remain enabled but unscheduled | save/recovery allowed an expired intended date to roll forward or disappear | editor rejects past one-shots; scheduler atomically disables an already-expired one-shot and appends one `MISSED` event | `AlarmEditorReliabilityTest`, `AndroidAlarmSchedulerTest`, `RoomAlarmRepositoryTest` idempotency |
| I1 sensor permissions blocked alarm creation | editor health gate treated camera/microphone as mandatory scheduling capability | saving gates only mandatory delivery health; sensor availability selects challenge/fallback at ringing time | editor instrumentation reliability coverage |
| I2 notification repair skipped Android 13 runtime flow | repair jumped directly to settings | Activity Result permission launcher, purpose dialog, denial/rationale/settings policy | `NotificationPermissionPolicyTest`, `RingingFlowTest` repair flow |
| I3 snooze/repeat failures could silence an unscheduled alarm | scheduler failure was swallowed | snooze remains ringing and exposes a retryable error until registration is acknowledged; repeat keeps pending durable work for recovery | `RingingSessionControllerTest` failed snooze, acknowledgement race, and repeat exception |
| I4 Direct Boot expectation was ambiguous | encrypted Room data cannot be read before first unlock | Direct Boot is intentionally unsupported and consistently documented; no `LOCKED_BOOT_COMPLETED` claim or receiver is declared | README, permissions guide, and device acceptance matrix |
| I5 ringing lock-screen flags leaked after terminal state | show-when-locked/turn-screen-on were only set | terminal session state clears both flags and finishes a ringing-only activity | `MainActivityRingingIntentTest` |
| I6 backups missed sensitive domains | only root/device-root exclusions existed | cloud/transfer/legacy rules exclude file, database, shared preferences, external, and device-protected variants | `BackupRulesTest` |
| I7 channel was lazy and health ignored channel state | channel could be absent/disabled without a repair route | application initialization creates the high-importance channel; health exposes channel readiness and channel-specific settings repair | `RingingNotificationChannelTest`, `AndroidHealthServiceTest`, `HealthRepairLauncherTest` |
| I8 speech recognizer availability was not health-visible | unavailable platform recognizer had no separate fallback reason | health checks `SpeechRecognizer.isRecognitionAvailable`; ringing treats it as unavailable independently of microphone permission | `AndroidHealthServiceTest` and ringing health-route coverage |
| I9 pose analysis could accumulate allocations/in-flight work | every camera frame allocated RGBA bitmaps without an ownership limit | bounded 640x480 analysis, KEEP_ONLY_LATEST, one owned in-flight frame, deterministic close/release on completion/error/adapter close | `PoseLandmarkerAdapterTest` owned-frame and startup-failure coverage |
| I10 emergency hold was pointer-only | TalkBack/switch access had no continuous-hold interaction | button semantics expose role, live countdown, start/cancel actions, and still require the full ten seconds | `RingingFlowTest` accessibility action and interruption coverage |

## Deferred concerns

- Direct Boot remains intentionally unsupported: alarms recover after the first post-reboot unlock only. The product documents this explicitly rather than claiming pre-unlock delivery.
- Physical-device validation remains required for lock-screen delivery, vendor background restrictions, camera performance/thermal profile, weak-light pose recognition, and real `zh-CN` speech recognizer availability. The emulator cannot establish those results.
- The pose implementation bounds in-flight memory/work and has deterministic ownership tests. Device-specific performance profiling is deferred to the physical-device acceptance matrix.

## Verification

## Targeted recovery follow-up

- C1: a real Room regression first proved that a successful snooze registration
  erased its only durable target. Registration acknowledgement now retains that
  target while the session is `SNOOZED`; delivery consumes it only on the
  `SNOOZED` to `RINGING` transition. Regular reconciliation skips the active
  session's alarm, then pending recovery re-registers its target after restart.
- I1: a lock-screen launch with no session first cleared and finished the
  activity. The activity now waits until it has observed `RINGING` before any
  terminal cleanup.
- I2: a `DEFAULT` importance channel first reported ready; all importance below
  `HIGH` now requires repair.
- I3: a real Room stale-expiry test first disabled a newer alarm edit. Expiry
  now compares the persisted `updatedAt` snapshot atomically before writing the
  disable and `MISSED` event.
- Minor hardening: delivered alarms prune scheduler diagnostics, pose analysis
  uses the lower-only resolution fallback, and adaptive icons declare a
  monochrome layer.

### Real Room snooze recovery regression coverage

- Added two Robolectric integration tests backed by an actual in-memory Room
  database, the production `AndroidAlarmScheduler`, and
  `PendingScheduleRecovery`:
  - one-shot `SNOOZED` registration acknowledgement retains `snoozeAt`;
    simulated reboot regular rescheduling skips the active session without
    disabling it or adding `MISSED`; post-grant pending recovery re-registers
    `snoozeAt`; delivery/start transitions it to `RINGING` and then clears the
    target.
  - repeating `SNOOZED` follows the same path and restores its exact
    `snoozeAt`, rather than the normal Friday recurrence; delivery preserves
    the repeat policy and clears only the completed snooze target.
- RED evidence: temporarily making Room acknowledgement always clear the
  target caused all three relevant Room tests to fail, including both new
  reboot/recovery tests. Restoring the `SNOOZED`-only retention transaction
  made the focused `RoomAlarmRepositoryTest` suite pass: 17 tests, 0 failures.

- Fresh command (with `JAVA_HOME=D:\Android Studio\jbr` and the local Android SDK):
  `gradlew clean testDebugUnitTest lintDebug connectedDebugAndroidTest assembleDebug assembleRelease --no-daemon --console=plain`
  completed successfully in 2m59s.
- Unit reports: 134 tests, 0 failures, 0 errors, 0 skipped.
- Instrumentation: 47/47 tests passed on a cold-started `WakeMove_API_35` emulator.
- `apksigner verify --verbose --print-certs` validates the release APK with v2 signing. Development certificate SHA-256:
  `a6e774027e991c4319f4308cd2a5c17d8173366104ae74f67625ed494f3aff74`.
- APK SHA-256:
  - debug: `5677831A0537B59EF0EE7AA191F391E24F1738F798F49B42A5E325E53D2F8BC3`
  - release: `9C4A201779642BB6028360D1698BA13C91C4DA2C873D2236871FB1218B95AA0F`
- `git diff --check` passed. The staged source diff has no credential-pattern or
  machine-path matches. IDE metadata, Gradle daemon configuration, and generated
  Kotlin cache files remain untracked and are not part of this change.

## Targeted recovery verification refresh

- Fresh command after adding the Room recovery tests:
  `gradlew clean testDebugUnitTest lintDebug connectedDebugAndroidTest assembleDebug assembleRelease --no-daemon --console=plain`
  completed successfully.
- Unit reports: 139 tests, 0 failures, 0 errors, 0 skipped.
- Instrumentation report: 47 tests, 0 failures, 0 errors, 0 skipped on
  `WakeMove_API_35`.
- Release signature: v2 verified, development certificate SHA-256
  `a6e774027e991c4319f4308cd2a5c17d8173366104ae74f67625ed494f3aff74`.
- APK SHA-256:
  - debug: `759B266EA15CDDA091C697B022FFD2524BC2A9C8D99C0CC541E6FEEBE13FFC24`
  - release: `85DD4FFD9DBFE59278EBF9180E1871A1A7DB250FD48E591850A92D650E30C262`
- `git diff --check` passed and the tracked diff had no credential-pattern
  matches. IDE/cache/daemon configuration files remain untracked.

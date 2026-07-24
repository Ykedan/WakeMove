# WakeMove Windows MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Windows 10/11 64-bit WakeMove setup EXE that remains in the system tray and dismisses alarms through camera movement or Chinese speech challenges.

**Architecture:** Tauri owns the process lifecycle and hosts a React interface. Rust modules own SQLite, scheduling, ringing audio, notifications, tray/autostart behavior, power-resume recovery, and native speech; TypeScript modules own the UI, MediaPipe camera stream, and pure movement counters.

**Tech Stack:** Node.js 24 LTS, React 19.2.7, TypeScript, Vite 8.1.5, Vitest 4.1.10, Tauri CLI 2.11.4, stable MSVC Rust, rusqlite, tokio, rodio, MediaPipe Tasks Vision 0.10.35, Testing Library, NSIS.

**Execution Order:** Execute the Android plan first because its bootstrap task creates `shared/phrases/zh-CN.json` and `shared/design/tokens.json`; the Windows plan consumes those reviewed cross-platform resources.

## Global Constraints

- Support Windows 10/11 64-bit with Microsoft Edge WebView2.
- Use simplified Chinese copy and the approved warm-white/orange/coral “日出活力” visual system.
- Store alarms, settings, sessions, and history only on the computer.
- Never persist camera frames or microphone recordings.
- Start camera or microphone capture only after the user starts a challenge.
- Closing the main window hides it to the tray; only explicit tray “退出” terminates the process.
- A normal dismiss button must not exist; challenge success is the standard stop path.
- Snooze defaults to 5 minutes and is limited to 3 uses per ringing session.
- Resume from sleep must immediately ring alarms missed by no more than 30 minutes and record older alarms as `MISSED`.
- Both sensors unavailable must expose a press-and-hold 10-second emergency stop and record `BYPASSED`.
- Every task must run its focused tests plus `npm run test`, `npm run lint`, `npm run build`, and `cargo test --manifest-path src-tauri/Cargo.toml` before commit.

---

## Planned File Map

```text
desktop/
├─ package.json
├─ package-lock.json
├─ vite.config.ts
├─ src/
│  ├─ main.tsx
│  ├─ app/App.tsx
│  ├─ api/commands.ts
│  ├─ domain/models.ts
│  ├─ challenge/movementCounters.ts
│  ├─ challenge/poseLandmarker.ts
│  ├─ challenge/speechChallenge.ts
│  ├─ features/alarms/...
│  ├─ features/ringing/...
│  ├─ features/history/...
│  ├─ features/health/...
│  └─ styles/tokens.css
├─ src-tauri/
│  ├─ Cargo.toml
│  ├─ tauri.conf.json
│  ├─ capabilities/default.json
│  ├─ migrations/0001_initial.sql
│  ├─ resources/default_alarm.ogg
│  └─ src/
│     ├─ lib.rs
│     ├─ domain.rs
│     ├─ storage.rs
│     ├─ schedule.rs
│     ├─ scheduler.rs
│     ├─ ringing.rs
│     ├─ speech.rs
│     ├─ health.rs
│     └─ tray.rs
└─ tests/
```

## Task 1: Install prerequisites and bootstrap pinned Tauri/React application

**Files:**
- Create: `desktop/package.json`
- Create: `desktop/package-lock.json`
- Create: `desktop/vite.config.ts`
- Create: `desktop/src/main.tsx`
- Create: `desktop/src-tauri/Cargo.toml`
- Create: `desktop/src-tauri/tauri.conf.json`
- Create: `desktop/src-tauri/src/lib.rs`
- Modify: `.gitignore`

**Interfaces:**
- Produces one Tauri application named `WakeMove` with identifier `com.wakemove.desktop`.

- [ ] **Step 1: Verify and install the native toolchain**

Run:

```powershell
Get-Command node, npm, rustc, cargo -ErrorAction SilentlyContinue
winget install --id OpenJS.NodeJS.LTS --exact
winget install --id Rustlang.Rustup --exact
winget install --id Microsoft.VisualStudio.2022.BuildTools --exact --override "--wait --passive --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
rustup default stable-msvc
```

Restart PowerShell, then require `node --version` to report Node 24 LTS, `rustc -Vv` to report host `x86_64-pc-windows-msvc`, and WebView2 Runtime to appear in installed applications.

- [ ] **Step 2: Scaffold without dynamic dependencies**

Create the project with Tauri’s React/TypeScript template, then replace ranges in `package.json` with exact versions and run `npm install` to lock them. Pin at minimum React/React DOM 19.2.7, Vite 8.1.5, Vitest 4.1.10, Tauri CLI 2.11.4, and MediaPipe Tasks Vision 0.10.35.

- [ ] **Step 3: Add smoke tests and scripts**

`package.json` must define `dev`, `build`, `test`, `lint`, `tauri`, and `tauri:build`. Add a Vitest smoke test that renders `WakeMove`, then run:

```powershell
Set-Location D:\WakeMove\desktop
npm run test
npm run build
cargo test --manifest-path src-tauri\Cargo.toml
npm run tauri build -- --debug
```

Expected: tests/build succeed and a debug desktop bundle is produced.

- [ ] **Step 4: Commit**

```powershell
git add .gitignore desktop
git commit -m "build(windows): bootstrap Tauri desktop app"
```

## Task 2: Implement shared Rust alarm domain and schedule rules

**Files:**
- Create: `desktop/src-tauri/src/domain.rs`
- Create: `desktop/src-tauri/src/schedule.rs`
- Modify: `desktop/src-tauri/src/lib.rs`

**Interfaces:**
- Produces serializable `Alarm`, `RingingSession`, `AlarmEvent`, `ChallengeType`, and `SessionStatus`.
- Produces `next_occurrence(alarm: &Alarm, now: DateTime<Local>) -> Option<DateTime<Local>>`.

- [ ] **Step 1: Write failing Rust tests**

Add `#[cfg(test)]` tests beside `schedule.rs` for same-day future one-shot, expired one-shot, weekly rollover, and local-time conversion:

```rust
#[test]
fn weekly_alarm_rolls_to_monday() {
    let now = local_datetime(2026, 7, 24, 10, 0);
    let alarm = alarm_at(7, 30, &[Weekday::Mon]);
    assert_eq!(next_occurrence(&alarm, now), Some(local_datetime(2026, 7, 27, 7, 30)));
}
```

- [ ] **Step 2: Confirm failure**

```powershell
cargo test --manifest-path src-tauri\Cargo.toml schedule
```

- [ ] **Step 3: Implement domain and schedule**

Use UUID strings, `chrono::NaiveTime`, a seven-bit weekday mask, and UTC millisecond timestamps at persistence boundaries. Search at most eight local dates and only return candidates strictly later than `now`.

- [ ] **Step 4: Verify and commit**

```powershell
cargo test --manifest-path src-tauri\Cargo.toml schedule
git add desktop/src-tauri/src/domain.rs desktop/src-tauri/src/schedule.rs desktop/src-tauri/src/lib.rs
git commit -m "feat(windows): add alarm domain and schedule rules"
```

## Task 3: Add SQLite repository and Tauri commands

**Files:**
- Create: `desktop/src-tauri/migrations/0001_initial.sql`
- Create: `desktop/src-tauri/src/storage.rs`
- Create: `desktop/src/api/commands.ts`
- Create: `desktop/src/domain/models.ts`
- Modify: `desktop/src-tauri/src/lib.rs`

**Interfaces:**
- Produces Rust `AlarmRepository` methods `list_alarms`, `get_alarm`, `upsert_alarm`, `delete_alarm`, `save_session`, `active_session`, `append_event`, `recent_events`, and `clear_history`.
- Produces typed TypeScript wrappers with the same operations through `invoke`.

- [ ] **Step 1: Write failing in-memory repository tests**

Verify migration, round-trip mapping, sorted alarms, ringing-session recovery, event retention after alarm deletion, and a transaction that changes session status and inserts history atomically.

- [ ] **Step 2: Confirm failure**

```powershell
cargo test --manifest-path src-tauri\Cargo.toml storage
```

- [ ] **Step 3: Implement migration and repository**

Create `alarms`, `ringing_sessions`, `alarm_events`, and `app_settings`. Enable WAL and foreign keys. Use `rusqlite::Connection` behind `parking_lot::Mutex`; keep SQL inside `storage.rs`.

- [ ] **Step 4: Add serialized Tauri commands**

Expose commands through `tauri::generate_handler!`. TypeScript wrappers must return explicit domain types and translate command errors into `{ code, message }`.

- [ ] **Step 5: Verify and commit**

```powershell
cargo test --manifest-path src-tauri\Cargo.toml storage
npm run test
git add desktop/src-tauri/migrations desktop/src-tauri/src/storage.rs desktop/src-tauri/src/lib.rs desktop/src/api desktop/src/domain
git commit -m "feat(windows): persist alarms and history"
```

## Task 4: Build background scheduler, tray lifecycle, autostart, and resume recovery

**Files:**
- Create: `desktop/src-tauri/src/scheduler.rs`
- Create: `desktop/src-tauri/src/tray.rs`
- Create: `desktop/src-tauri/src/health.rs`
- Modify: `desktop/src-tauri/src/lib.rs`
- Modify: `desktop/src-tauri/tauri.conf.json`

**Interfaces:**
- Produces `Scheduler::start`, `notify_changed`, `process_due(now)`, and `process_resume(now)`.
- Produces `HealthSnapshot { tray_running, autostart_enabled, microphone_available, camera_available, speech_language_available, next_alarm }`.

- [ ] **Step 1: Write failing scheduler tests**

With a fake clock/repository/signal sink, verify one trigger only, changes wake the scheduler, resume within 30 minutes rings immediately, older occurrences become `MISSED`, and disabled alarms never fire.

- [ ] **Step 2: Confirm failure**

```powershell
cargo test --manifest-path src-tauri\Cargo.toml scheduler
```

- [ ] **Step 3: Implement scheduler loop**

Use a Tokio task that sleeps until the earlier of next due time or a `Notify` signal. Re-read SQLite after every signal and terminal transition. Persist a ringing session before emitting `alarm://ring`.

- [ ] **Step 4: Implement desktop lifecycle**

Create a tray menu with `打开 WakeMove` and `退出`. Intercept main-window close and hide it. Explicit exit shows confirmation in the UI before invoking `confirm_exit`. Configure single-instance behavior and Tauri autostart. Register Windows suspend/resume notification and call `process_resume`.

- [ ] **Step 5: Verify and commit**

```powershell
cargo test --manifest-path src-tauri\Cargo.toml scheduler
npm run tauri build -- --debug
git add desktop/src-tauri
git commit -m "feat(windows): run alarms from tray and recover after sleep"
```

## Task 5: Implement ringing audio, topmost window, snooze, and terminal transitions

**Files:**
- Create: `desktop/src-tauri/src/ringing.rs`
- Create: `desktop/src-tauri/resources/default_alarm.ogg`
- Modify: `desktop/src-tauri/src/lib.rs`
- Test: Rust tests inside `desktop/src-tauri/src/ringing.rs`

**Interfaces:**
- Produces commands `start_ringing`, `snooze_active`, `complete_active`, and `bypass_active`.
- Emits `ringing://state` events consumed by React.

- [ ] **Step 1: Write failing ringing-controller tests**

Verify looped playback starts after persistence, snooze succeeds three times and then fails with `SNOOZE_LIMIT`, completion/bypass are idempotent, and a repeating alarm reschedules exactly once.

- [ ] **Step 2: Confirm failure**

```powershell
cargo test --manifest-path src-tauri\Cargo.toml ringing
```

- [ ] **Step 3: Implement ringing**

Use `rodio` with alarm audio decoded once and repeated indefinitely. Show, maximize, focus, and set the ringing window always-on-top. Terminal transitions stop the sink, clear topmost state, write history, and signal the scheduler.

- [ ] **Step 4: Verify and commit**

```powershell
cargo test --manifest-path src-tauri\Cargo.toml ringing
npm run tauri build -- --debug
git add desktop/src-tauri/src/ringing.rs desktop/src-tauri/src/lib.rs desktop/src-tauri/resources
git commit -m "feat(windows): add ringing and snooze sessions"
```

## Task 6: Implement TypeScript movement counters and MediaPipe camera adapter

**Files:**
- Create: `desktop/src/challenge/poseModels.ts`
- Create: `desktop/src/challenge/movementCounters.ts`
- Create: `desktop/src/challenge/poseLandmarker.ts`
- Create: `desktop/src/challenge/cameraChallenge.ts`
- Test: `desktop/src/challenge/movementCounters.test.ts`
- Test: `desktop/src/challenge/cameraChallenge.test.ts`

**Interfaces:**
- Produces `MovementCounter.update(frame: PoseFrame): ChallengeProgress`.
- Produces `CameraChallenge.start(type, target)`, `subscribe(listener)`, and `close()`.

- [ ] **Step 1: Write failing synthetic-frame tests**

Use explicit landmark fixtures for complete/incomplete squats, jumping jacks, hands-up hold, jitter, low visibility, unrelated motion, target completion, 60-second fallback, and stream cleanup.

- [ ] **Step 2: Confirm failure**

```powershell
npm run test -- movementCounters cameraChallenge
```

- [ ] **Step 3: Implement pure counters**

Match Android thresholds: visibility 0.65, three stable frames, 350 ms cooldown, and 2,000 ms hands-up hold. Keep platform-independent math in `movementCounters.ts`.

- [ ] **Step 4: Implement MediaPipe stream**

Load locally bundled WASM and `pose_landmarker_lite.task`; do not use a CDN. Request camera only when `start` runs. Use VIDEO mode with one pose and 0.5 confidence thresholds. Stop every media track and close the landmarker in `close()`.

- [ ] **Step 5: Verify and commit**

```powershell
npm run test -- movementCounters cameraChallenge
npm run build
git add desktop/src/challenge desktop/public
git commit -m "feat(windows): recognize camera movements"
```

## Task 7: Prove and implement native Chinese speech recognition

**Files:**
- Create: `desktop/src-tauri/src/speech.rs`
- Create: `desktop/src/challenge/speechNormalizer.ts`
- Create: `desktop/src/challenge/speechChallenge.ts`
- Modify: `desktop/src-tauri/src/health.rs`
- Modify: `desktop/src-tauri/src/lib.rs`
- Test: `desktop/src/challenge/speechNormalizer.test.ts`
- Test: Rust tests inside `desktop/src-tauri/src/speech.rs`

**Interfaces:**
- Produces native commands `speech_health`, `recognize_phrase`, and `cancel_recognition`.
- Produces `normalizeSpeech(text: string): string`.

- [ ] **Step 1: Build a native capability spike**

Use the `windows` crate and Windows SAPI to enumerate installed recognizers, select `zh-CN`, create an in-memory grammar for one phrase, recognize once with a 20-second timeout, and release COM objects on the recognition thread. Run it manually before integrating UI.

Expected: the spoken target phrase returns recognized text; missing Chinese speech components returns `ZH_CN_LANGUAGE_MISSING` rather than crashing.

- [ ] **Step 2: Write failing automated tests**

Rust tests use a fake recognizer to verify match, timeout, cancellation, service unavailable, and cleanup. TypeScript tests cover NFKC normalization, punctuation/space removal, Chinese/Arabic digits, candidate matching, and wrong-phrase rejection.

- [ ] **Step 3: Implement the bridge and frontend controller**

Run SAPI on a dedicated single-threaded COM apartment. Never write audio to disk. Load phrases from `shared/phrases/zh-CN.json`, use cryptographic random selection, and require normalized full-string equality.

- [ ] **Step 4: Verify and commit**

```powershell
cargo test --manifest-path src-tauri\Cargo.toml speech
npm run test -- speech
git add desktop/src-tauri/src/speech.rs desktop/src-tauri/src/health.rs desktop/src-tauri/src/lib.rs desktop/src/challenge
git commit -m "feat(windows): add Chinese voice dismissal"
```

## Task 8: Build the approved desktop interface

**Files:**
- Create: `desktop/src/styles/tokens.css`
- Create: `desktop/src/app/App.tsx`
- Create: `desktop/src/features/alarms/AlarmList.tsx`
- Create: `desktop/src/features/alarms/AlarmEditor.tsx`
- Create: `desktop/src/features/ringing/RingingView.tsx`
- Create: `desktop/src/features/ringing/CameraChallengeView.tsx`
- Create: `desktop/src/features/ringing/SpeechChallengeView.tsx`
- Create: `desktop/src/features/history/HistoryView.tsx`
- Create: `desktop/src/features/health/HealthView.tsx`
- Create: `desktop/src/features/settings/SettingsView.tsx`
- Test: matching `*.test.tsx` files

**Interfaces:**
- Consumes typed Tauri commands and events.
- Produces alarm management, ringing/challenge, history, health, settings, and exit-confirmation flows.

- [ ] **Step 1: Write failing component/flow tests**

Test create/edit/enable/delete, weekday selection, challenge count, no ordinary dismiss button, three-snooze limit, fallback switching, 10-second uninterrupted emergency hold, history labels, health fixes, and exit confirmation.

- [ ] **Step 2: Confirm failure**

```powershell
npm run test
```

- [ ] **Step 3: Implement layout and theme**

Use the approved dark-brown sidebar, warm-white content, orange/coral gradient actions, large time typography, rounded cards, and dark high-contrast ringing view. Keep feature state in focused hooks; do not duplicate repository state in unrelated components.

- [ ] **Step 4: Add accessibility and privacy copy**

All controls must be keyboard reachable and labeled. Display camera framing/light guidance and microphone state. State that frames and audio are not saved. Cancel emergency-hold progress on pointer-up, pointer-cancel, blur, and Escape.

- [ ] **Step 5: Verify and commit**

```powershell
npm run test
npm run lint
npm run build
git add desktop/src
git commit -m "feat(windows): complete desktop interface"
```

## Task 9: Package and verify the Windows MVP

**Files:**
- Create: `desktop/README.md`
- Create: `desktop/docs/permissions.md`
- Create: `desktop/docs/device-acceptance.md`
- Create: `desktop/THIRD_PARTY_NOTICES.md`
- Modify: `desktop/src-tauri/tauri.conf.json`

**Interfaces:**
- Produces an unsigned NSIS setup EXE and reproducible build/test instructions.

- [ ] **Step 1: Run complete automated verification**

```powershell
Set-Location D:\WakeMove\desktop
npm ci
npm run test
npm run lint
npm run build
cargo test --manifest-path src-tauri\Cargo.toml
cargo clippy --manifest-path src-tauri\Cargo.toml -- -D warnings
```

Expected: every command succeeds with zero warnings promoted by Clippy.

- [ ] **Step 2: Execute the desktop alarm matrix**

Record timestamps/outcomes for open window, closed-to-tray, locked session, reboot/login autostart, resume within 30 minutes, resume after 30 minutes, offline, camera occupied, microphone disabled, and Chinese language missing. Run each movement and voice challenge 20 times; require at least 18 correct completions and zero false dismissals.

- [ ] **Step 3: Build NSIS installer**

Configure bundle target `nsis`, 64-bit architecture, icons, bundled alarm/model/WASM/shared phrases, and upgrade-compatible product GUID. Run:

```powershell
npm run tauri build
```

Expected: `desktop\src-tauri\target\release\bundle\nsis\WakeMove_*_x64-setup.exe`.

- [ ] **Step 4: Verify install, upgrade, and uninstall**

Test on a clean Windows 10/11 user profile. Confirm upgrade retains SQLite data, uninstall removes application binaries/autostart entry, and user data removal behavior is documented.

- [ ] **Step 5: Commit**

```powershell
git add desktop/README.md desktop/docs desktop/THIRD_PARTY_NOTICES.md desktop/src-tauri/tauri.conf.json
git commit -m "docs(windows): add installer and acceptance evidence"
```

## Official References

- Tauri prerequisites: https://v2.tauri.app/start/prerequisites/
- Tauri project creation: https://v2.tauri.app/start/create-project/
- Tauri autostart: https://v2.tauri.app/plugin/autostart/
- Tauri Windows installer: https://v2.tauri.app/distribute/windows-installer/
- React stable versions: https://react.dev/versions
- Vite releases: https://vite.dev/releases
- MediaPipe Pose Landmarker for Web: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/web_js
- MediaPipe Tasks Vision package: https://www.npmjs.com/package/@mediapipe/tasks-vision

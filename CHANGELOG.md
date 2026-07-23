# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Changed (breaking)

- **Alarm events are now edge-triggered.** `onAlarmStateChanged` (and the other
  lifecycle events) fire only on real state transitions — started, snoozed,
  unsnoozed, ringing, paused/resumed, stopped — instead of roughly once per
  second while a timer or countdown is active. Its `remainingSeconds` field is a
  snapshot captured at the transition, not a live per-second tick.

  Earlier versions emitted `onAlarmStateChanged` every second during an active
  countdown/snooze/ringing (about 28,800 events over an 8-hour session). Apps
  that forwarded these events to an analytics pipeline saw very large, costly
  event volume. This release removes the per-second emission at the source on
  both platforms:
  - iOS (`ios/ReactNativeAlarmModule.swift`): the `checkAndSendAlarmStateEvents`
    state machine keeps running and persisting a snapshot every pass (so
    transition detection stays correct), but the "remaining decreased" arms no
    longer emit `onAlarmStateChanged`.
  - Android (`ForegroundTimerService`, `ReactNativeAlarmModule`): the 1-second
    `tick()` / `updateNotification()` loop keeps refreshing the notification and
    detecting expiry, but no longer bridges a per-second event to JS. Pause and
    resume now emit exactly one transition event each.

  **Migration.** To render a live in-app countdown, seed a local timer from the
  `remainingSeconds` delivered on `onAlarmStarted` and recalibrate on later
  events, e.g.:

  ```ts
  addAlarmStartedListener(({ remainingSeconds }) => {
    const firesAt = Date.now() + remainingSeconds * 1000;
    const t = setInterval(() => {
      const left = Math.max(0, Math.ceil((firesAt - Date.now()) / 1000));
      setRemaining(left);
      if (left === 0) clearInterval(t);
    }, 1000);
  });
  ```

  The lock-screen / Dynamic Island (iOS) and notification (Android) countdowns
  are rendered by the OS and are unaffected. Do not forward alarm events
  directly to an analytics pipeline.

  Recommended release: **0.2.0** (pre-1.0, so a breaking change is a minor bump).

### Notes

- iOS transition detection still relies on the periodic `checkAlarmStates()`
  call while the app is foregrounded (unchanged here); it is now inexpensive
  because it emits only on transitions. Removing the JS poll entirely by
  starting native `alarmUpdates` observation at module init is deferred to a
  follow-up (it needs on-device verification that observation covers snooze
  inference across app restart / background / ringing / pause-resume).

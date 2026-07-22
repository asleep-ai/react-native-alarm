## @asleep-ai/react-native-alarm — Architecture and Capabilities (Agent Notes)

This document summarizes what the module does today, the platform-specific behavior, permissions, JS API, and extension points for future customization. Target OS versions: iOS 26+ (AlarmKit), Android 13+ (API 33+).

### Overview

- iOS (26+ only)
  - Uses AlarmKit to request authorization, schedule alarms and timers, and reflect AlarmKit state back to JS.
  - When scheduling by fixed date, we also start a countdown presentation so a Live Activity is visible until the fire time.
  - No UserNotifications fallback; this lib is explicitly for iOS 26+.
- Android (13+ only)
  - Exact alarms via AlarmManager.setAlarmClock (falls back to inexact if permission denied).
  - Countdown timers via Foreground Service with ongoing notification (Live Activity–like).
  - At fire time: full-screen Activity for lockscreen or when the device is not interactive; overlay UI when unlocked and interactive.
  - Reboot-resilience: scheduled alarms re-post and countdown notification is restored.

### JS API (src/index.ts)

Synchronous

- `isAvailable(): boolean` — iOS 26+ or Android 13+.
- `canScheduleExactAlarms(): boolean` — Android only.
- `hasOverlayPermission(): boolean` — Android only.
- `isIgnoringBatteryOptimizations(): boolean` — Android only.

Async

- `requestPermission(): Promise<boolean>` — iOS: AlarmKit auth; Android: notifications permission (13+).
- `openExactAlarmSettings(): Promise<boolean>` — Android system settings to allow exact alarms.
- `openOverlayPermissionSettings(): Promise<boolean>` — Android overlay permission screen.
- `openBatteryOptimizationSettings(): Promise<boolean>` — Android DOZE/battery exemptions.
- `scheduleAlarm(options: ScheduleAlarmOptions): Promise<Alarm>`
- `cancelAlarm(id: string): Promise<void>`
- `cancelAll(): Promise<void>`
- `getAlarms(): Promise<Alarm[]>`

Types (src/ReactNativeAlarm.types.ts)

- `type Alarm = { id: string; dateISO: string; label?: string; enabled: boolean }`
- `type ScheduleAlarmOptions = { dateISO?: string; label?: string; countdownSeconds?: number; }`

### iOS (26+) Behavior

- `scheduleAlarm({ dateISO, countdownSeconds, label })`
  - If `countdownSeconds` present, Live Activity (countdown + paused + alert) is shown immediately.
  - If only `dateISO` is given, a default countdown is automatically created until that time so the Live Activity shows instantly.
  - All alarms are mirrored to `UserDefaults` for simple JS reads (`getAlarms`).

### Android (13+) Behavior

- Scheduling
  - `dateISO` → exact alarm via `AlarmManager.setAlarmClock`. If SecurityException occurs (exact permission not granted), we fall back to `set(RTC_WAKEUP, ...)`.
  - `countdownSeconds` → `ForegroundTimerService` starts immediately and posts an ongoing countdown notification (high-importance channel, lockscreen-visible).
  - Both present → show countdown now and also schedule the exact time.
- At fire time
  - `AlarmRingingService` starts as a foreground service (mediaPlayback), cancels any pre-alarm countdown notification, loops the default alarm sound.
  - UI selection (to avoid conflicts):
    - If device locked or not interactive → Full-screen Activity (`AlarmActivity`) only.
    - If unlocked and interactive → Overlay UI (if overlay permission granted); otherwise fall back to `AlarmActivity`.
  - Dismiss/Stop from Activity or overlay stops the service/sound and marks the alarm disabled in storage.
- Reboot handling
  - `BootReceiver` re-schedules pending alarms and re-posts a countdown notification.

### Permissions

- iOS: AlarmKit permissions via `AlarmManager.shared.requestAuthorization()`.
- Android:
  - Notifications (POST_NOTIFICATIONS).
  - Foreground services (FOREGROUND_SERVICE, FOREGROUND_SERVICE_SHORT_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK).
  - Exact alarms (SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM) — optional, with graceful fallback.
  - Full-screen intent (USE_FULL_SCREEN_INTENT).
  - Reboot (RECEIVE_BOOT_COMPLETED).
  - Wake and vibrate (WAKE_LOCK, VIBRATE).
  - Overlay (SYSTEM_ALERT_WINDOW) — optional, for in-app overlay on unlocked screens.
  - Battery optimization exemption (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) — optional.

### Notification Channels (Android)

- Timers High: `react_native_alarm_timers_high` (IMPORTANCE_HIGH, lockscreen-visible) for countdown/live notifications.
- Alerts: `react_native_alarm_alerts` (IMPORTANCE_HIGH, alarm sound/vibration) for ring-time alerts and full-screen intents.

### Storage

- iOS: `UserDefaults` mirror with key `ReactNativeAlarm.scheduled`.
- Android: `SharedPreferences` mirror via `AlarmStorage`, same key. Used for `getAlarms` and reboot restore.

### Known constraints / Notes

- iOS: Requires iOS 26+; no UNUserNotificationCenter fallback.
- Android: Manufacturer battery policies can still interfere; we expose helpers to guide users through settings.
- Overlay: Requires user consent in Settings; if denied, we fall back to full-screen Activity.

---

## Customization Plan (next steps)

We can support user-level customization across UI/behavior without forking native code by introducing optional configuration and option fields. The following is a proposal (non-breaking if optional).

### Proposed new TS types

```ts
export type AndroidNotificationStyle = {
  timerChannelId?: string; // default 'react_native_alarm_timers_high'
  alertChannelId?: string; // default 'react_native_alarm_alerts'
  smallIconName?: string; // Android drawable name for small icon
  accentColor?: string; // hex color for notification accents
  useChronometer?: boolean; // default true
  showOverlayWhenUnlocked?: boolean; // default true
};

export type IOSAlarmStyle = {
  tintColorHex?: string; // e.g., '#007AFF' → Color.blue fallback
  usePausedPresentation?: boolean; // default true
  alertStopText?: string; // default 'Done'
  countdownPauseText?: string; // default 'Pause'
  pausedResumeText?: string; // default 'Start'
};

export type ReactNativeAlarmConfig = {
  android?: AndroidNotificationStyle;
  ios?: IOSAlarmStyle;
};
```

### Proposed new JS API

```ts
// Global, process-wide configuration (can be called anytime before scheduling)
export function configure(options: ReactNativeAlarmConfig): void;

// Extended schedule options
export type ScheduleAlarmOptionsV2 = ScheduleAlarmOptions & {
  android?: AndroidNotificationStyle;
  ios?: IOSAlarmStyle;
};
```

### What will be customizable

- Android
  - Notification channel ids (allows integrators to pre-create channels with branding sound/vibration).
  - Notification small icon name and accent color.
  - Whether to show overlay on unlocked screens or always use full-screen.
  - Per-alarm overrides via `scheduleAlarm` options.
- iOS
  - Tint color and localized button labels for Alert/Countdown/Paused.

### Permission UX helpers (Android)

We already expose:

- `canScheduleExactAlarms`, `openExactAlarmSettings`
- `hasOverlayPermission`, `openOverlayPermissionSettings`
- `isIgnoringBatteryOptimizations`, `openBatteryOptimizationSettings`

We can add a combined checker:

```ts
export type AndroidPermissionStatus = {
  notifications: boolean;
  exactAlarmAllowed: boolean;
  overlayAllowed: boolean;
  ignoringBatteryOptimizations: boolean;
};
export async function getAndroidPermissionStatus(): Promise<AndroidPermissionStatus>;
```

### Implementation notes

- Keep defaults identical to current behavior to avoid breaking changes.
- Native side: read optional config once (module-scoped), and per-call overrides from `scheduleAlarm`.
- Channel changes: if callers provide a custom channel id, they must ensure the channel exists (we can lazily create a sane default if missing).

### Testing Checklist

- iOS 26+ device: Live Activity shows immediately for both date and countdown paths; alert appears with custom labels/colors if provided.
- Android 13+ device:
  - Countdown notification chronometer visible on lock screen and home screen.
  - Ringing logic: full-screen on locked/not-interactive; overlay or activity on unlocked.
  - No duplicate audio; stopping clears overlay and notification.
  - Reboot restores scheduled alarms and countdowns.
  - Custom icons/colors/channels render as configured.

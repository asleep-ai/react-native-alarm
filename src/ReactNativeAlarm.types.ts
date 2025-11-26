export type AlarmId = string;

export type Alarm = {
  id: AlarmId;
  dateISO: string; // ISO 8601 date string
  label?: string;
  enabled: boolean;
  // State information (optional, may not be present in all contexts)
  isRinging?: boolean;
  isSnoozed?: boolean;
  remainingSeconds?: number;
  snoozeUntilISO?: string;
};

export type ScheduleAlarmOptions = {
  dateISO?: string;
  label?: string;
  allowSnooze?: boolean;
  sound?: string;
  /**
   * When provided, schedules a timer that counts down immediately.
   * If both dateISO and countdownSeconds are provided, the alarm will include both
   * a countdown and a one-time alert date where supported by the platform.
   */
  countdownSeconds?: number;
  /**
   * Optional per-alarm styles. If omitted, global configure() or defaults are used.
   */
  android?: AndroidNotificationStyle;
  ios?: IOSAlarmStyle;
};

export type AlarmState = {
  id: AlarmId;
  label?: string;
  isRinging: boolean; // 현재 알람이 울리고 있는지
  isSnoozed: boolean; // 현재 스누즈 중인지
  remainingSeconds: number; // 남은 시간 (초)
  stoppedAtISO?: string; // 알람이 정지된 시간 (ISO 8601)
  snoozeUntilISO?: string; // 스누즈가 끝나는 시간 (ISO 8601)
};

export type ReactNativeAlarmEvents = {
  onAlarmFired: (event: { id: AlarmId }) => void;
  onAlarmStarted: (event: {
    id: AlarmId;
    label?: string;
    remainingSeconds: number;
  }) => void;
  onAlarmSnoozed: (event: {
    id: AlarmId;
    label?: string;
    snoozeUntilISO: string;
  }) => void;
  onAlarmStopped: (event: {
    id: AlarmId;
    label?: string;
    stoppedAtISO: string;
  }) => void;
  onAlarmStateChanged: (event: AlarmState) => void;
};

// ---- Customization types

export type AndroidNotificationStyle = {
  timerChannelId?: string; // default: 'react_native_alarm_timers_high'
  alertChannelId?: string; // default: 'react_native_alarm_alerts'
  smallIconName?: string; // Android drawable name
  accentColor?: string; // hex color string, e.g. '#FF4081'
  useChronometer?: boolean; // default true
  showOverlayWhenUnlocked?: boolean; // default true
  overlayBackgroundColor?: string; // hex, overlay panel background
  overlayTextColor?: string; // hex, overlay title text color
  overlayButtonBackgroundColor?: string; // hex, overlay button background
  overlayButtonTextColor?: string; // hex, overlay button text color
  snoozeMinutes?: number; // default 5
};

export type IOSAlarmStyle = {
  tintColorHex?: string; // e.g. '#007AFF'
  alertStopText?: string; // default 'Done'
  countdownPauseText?: string; // default 'Pause'
  pausedResumeText?: string; // default 'Start'
  snoozeMinutes?: number; // default 5 - snooze duration in minutes
};

export type ReactNativeAlarmConfig = {
  android?: AndroidNotificationStyle;
  ios?: IOSAlarmStyle;
};

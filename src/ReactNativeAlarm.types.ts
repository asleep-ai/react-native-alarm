export type AlarmId = string;

export type Alarm = {
  id: AlarmId;
  dateISO: string; // ISO 8601 date string
  label?: string;
  enabled: boolean;
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
};

export type ReactNativeAlarmEvents = {
  onAlarmFired: (event: { id: AlarmId }) => void;
};

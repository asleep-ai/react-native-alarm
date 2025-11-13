import ReactNativeAlarmModule from "./ReactNativeAlarmModule";
export * from "./ReactNativeAlarm.types";
import type { Alarm, ScheduleAlarmOptions } from "./ReactNativeAlarm.types";

export function isAvailable(): boolean {
  return ReactNativeAlarmModule.isAlarmKitAvailable();
}

export function canScheduleExactAlarms(): boolean {
  return ReactNativeAlarmModule.canScheduleExactAlarms();
}

export async function requestPermission(): Promise<boolean> {
  return ReactNativeAlarmModule.requestPermission();
}

export async function openExactAlarmSettings(): Promise<boolean> {
  return ReactNativeAlarmModule.openExactAlarmSettings();
}

export async function scheduleAlarm(
  options: ScheduleAlarmOptions
): Promise<Alarm> {
  return ReactNativeAlarmModule.scheduleAlarm(options);
}

export async function cancelAlarm(id: string): Promise<void> {
  return ReactNativeAlarmModule.cancelAlarm(id);
}

export async function cancelAll(): Promise<void> {
  return ReactNativeAlarmModule.cancelAll();
}

export async function getAlarms(): Promise<Alarm[]> {
  return ReactNativeAlarmModule.getAlarms();
}

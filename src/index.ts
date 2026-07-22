import ReactNativeAlarmModule from "./ReactNativeAlarmModule";
export * from "./ReactNativeAlarm.types";
import type { Alarm, ScheduleAlarmOptions, AlarmState, ReactNativeAlarmEvents } from "./ReactNativeAlarm.types";
import type { ReactNativeAlarmConfig } from "./ReactNativeAlarm.types";
import { EventEmitter } from "expo-modules-core";

export function isAvailable(): boolean {
  return ReactNativeAlarmModule.isAlarmKitAvailable();
}

export function canScheduleExactAlarms(): boolean {
  return ReactNativeAlarmModule.canScheduleExactAlarms();
}

export function hasOverlayPermission(): boolean {
  return ReactNativeAlarmModule.hasOverlayPermission();
}

export function isIgnoringBatteryOptimizations(): boolean {
  return ReactNativeAlarmModule.isIgnoringBatteryOptimizations();
}

export async function requestPermission(): Promise<{ granted: boolean; status: string }> {
  return ReactNativeAlarmModule.requestPermission();
}

export async function getAuthorizationStatus(): Promise<string> {
  return ReactNativeAlarmModule.getAuthorizationStatus();
}

export async function openSettings(): Promise<void> {
  return ReactNativeAlarmModule.openSettings();
}

export async function openExactAlarmSettings(): Promise<boolean> {
  return ReactNativeAlarmModule.openExactAlarmSettings();
}

export async function openOverlayPermissionSettings(): Promise<boolean> {
  return ReactNativeAlarmModule.openOverlayPermissionSettings();
}

export async function openBatteryOptimizationSettings(): Promise<boolean> {
  return ReactNativeAlarmModule.openBatteryOptimizationSettings();
}

export async function configure(config: ReactNativeAlarmConfig): Promise<void> {
  const anyMod: any = ReactNativeAlarmModule as any;
  try {
    // Prefer 2-arg signature on Android to avoid map bridging issues
    return await anyMod.setConfig(config.android ?? null, config.ios ?? null);
  } catch {
    // Fallback to single-arg signature (iOS)
    return ReactNativeAlarmModule.setConfig(config);
  }
}

export async function getAndroidPermissionStatus(): Promise<{
  notifications: boolean;
  exactAlarmAllowed: boolean;
  overlayAllowed: boolean;
  ignoringBatteryOptimizations: boolean;
}> {
  return ReactNativeAlarmModule.getAndroidPermissionStatus();
}

export async function scheduleAlarm(options: ScheduleAlarmOptions): Promise<Alarm> {
  // Workaround: Android bridge may fail on nested objects in a single-arg call.
  // If style overrides are provided, apply them via configure() first, then
  // call native scheduleAlarm without the nested android object.
  const anyOpts: any = options as any;
  if (anyOpts?.android) {
    await configure({ android: anyOpts.android });
    const { android: _ignored, ...rest } = anyOpts;
    return ReactNativeAlarmModule.scheduleAlarm(rest);
  }
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

export async function checkAlarmStates(): Promise<void> {
  return ReactNativeAlarmModule.checkAlarmStates();
}

// Event listeners
const emitter = new EventEmitter<ReactNativeAlarmEvents>(ReactNativeAlarmModule);

// Subscription type for event listeners
export type Subscription = {
  remove: () => void;
};

export function addAlarmStartedListener(
  listener: (event: { id: string; label?: string; remainingSeconds: number }) => void,
): Subscription {
  return emitter.addListener("onAlarmStarted", listener);
}

export function addAlarmSnoozedListener(
  listener: (event: { id: string; label?: string; snoozeUntilISO: string }) => void,
): Subscription {
  return emitter.addListener("onAlarmSnoozed", listener);
}

export function addAlarmStoppedListener(
  listener: (event: { id: string; label?: string; stoppedAtISO: string }) => void,
): Subscription {
  return emitter.addListener("onAlarmStopped", listener);
}

export function addAlarmStateChangedListener(listener: (event: AlarmState) => void): Subscription {
  return emitter.addListener("onAlarmStateChanged", listener);
}

export function removeAllListeners(eventName?: keyof ReactNativeAlarmEvents): void {
  if (eventName) {
    emitter.removeAllListeners(eventName);
  } else {
    // Remove all event listeners
    emitter.removeAllListeners("onAlarmFired");
    emitter.removeAllListeners("onAlarmStarted");
    emitter.removeAllListeners("onAlarmSnoozed");
    emitter.removeAllListeners("onAlarmStopped");
    emitter.removeAllListeners("onAlarmStateChanged");
  }
}

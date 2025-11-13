import { NativeModule, requireNativeModule } from "expo-modules-core";
import type {
  ReactNativeAlarmEvents,
  Alarm,
  ScheduleAlarmOptions,
} from "./ReactNativeAlarm.types";
import type { ReactNativeAlarmConfig } from "./ReactNativeAlarm.types";

declare class ReactNativeAlarmModule extends NativeModule<ReactNativeAlarmEvents> {
  isAlarmKitAvailable(): boolean;
  canScheduleExactAlarms(): boolean;
  hasOverlayPermission(): boolean;
  isIgnoringBatteryOptimizations(): boolean;
  requestPermission(): Promise<boolean>;
  openExactAlarmSettings(): Promise<boolean>;
  openOverlayPermissionSettings(): Promise<boolean>;
  openBatteryOptimizationSettings(): Promise<boolean>;
  setConfig(config: ReactNativeAlarmConfig): Promise<void>;
  getAndroidPermissionStatus(): Promise<{
    notifications: boolean;
    exactAlarmAllowed: boolean;
    overlayAllowed: boolean;
    ignoringBatteryOptimizations: boolean;
  }>;
  scheduleAlarm(options: ScheduleAlarmOptions): Promise<Alarm>;
  cancelAlarm(id: string): Promise<void>;
  cancelAll(): Promise<void>;
  getAlarms(): Promise<Alarm[]>;
}

// This loads the native module object from the JSI.
export default requireNativeModule<ReactNativeAlarmModule>("ReactNativeAlarm");

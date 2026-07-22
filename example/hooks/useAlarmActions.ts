import { useState, useCallback, useEffect } from "react";
import { Platform, Alert } from "react-native";
import { scheduleAlarm, getAlarms, cancelAlarm, cancelAll, type Alarm } from "@asleep-ai/react-native-alarm";
import type { AlarmConfig } from "./useAlarmConfig";

interface UseAlarmActionsProps {
  config: AlarmConfig;
}

export function useAlarmActions({ config }: UseAlarmActionsProps) {
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [lastScheduled, setLastScheduled] = useState<Alarm | null>(null);

  const refresh = useCallback(async () => {
    try {
      const list = await getAlarms();
      setAlarms(list);
    } catch (e: any) {
      Alert.alert("Error", String(e?.message ?? e));
    }
  }, []);

  // Load alarms on mount
  useEffect(() => {
    refresh();
  }, [refresh]);

  const onScheduleIn = useCallback(
    async (seconds: number) => {
      try {
        // Don't cancel all - allow multiple alarms
        const dateISO = new Date(Date.now() + seconds * 1000).toISOString();
        const a = await scheduleAlarm({
          dateISO,
          label: `Test Alarm (${seconds}s)`,
          countdownSeconds: seconds,
          android:
            Platform.OS === "android"
              ? {
                  smallIconName: config.androidSmallIcon || undefined,
                  accentColor: config.androidAccentColor,
                  useChronometer: config.androidUseChrono,
                  showOverlayWhenUnlocked: config.androidOverlayUnlocked,
                  overlayBackgroundColor: config.androidOverlayBg,
                  overlayTextColor: config.androidOverlayText,
                  overlayButtonBackgroundColor: config.androidOverlayBtnBg,
                  overlayButtonTextColor: config.androidOverlayBtnText,
                  snoozeMinutes: 3,
                }
              : undefined,
          ios:
            Platform.OS === "ios"
              ? {
                  snoozeMinutes: 3,
                }
              : undefined,
        });
        setLastScheduled(a);
        await refresh();
      } catch (e: any) {
        Alert.alert("Error", String(e?.message ?? e));
      }
    },
    [config, refresh],
  );

  const onStartTimer = useCallback(
    async (seconds: number) => {
      try {
        // Don't cancel all - allow multiple alarms
        const a = await scheduleAlarm({
          label: `Timer (${seconds}s)`,
          countdownSeconds: seconds,
          android:
            Platform.OS === "android"
              ? {
                  smallIconName: config.androidSmallIcon || undefined,
                  accentColor: config.androidAccentColor,
                  useChronometer: config.androidUseChrono,
                  showOverlayWhenUnlocked: config.androidOverlayUnlocked,
                  overlayBackgroundColor: config.androidOverlayBg,
                  overlayTextColor: config.androidOverlayText,
                  overlayButtonBackgroundColor: config.androidOverlayBtnBg,
                  overlayButtonTextColor: config.androidOverlayBtnText,
                  snoozeMinutes: 3,
                }
              : undefined,
          ios:
            Platform.OS === "ios"
              ? {
                  snoozeMinutes: 3,
                }
              : undefined,
        });
        setLastScheduled(a);
        await refresh();
      } catch (e: any) {
        Alert.alert("Error", String(e?.message ?? e));
      }
    },
    [config, refresh],
  );

  const onScheduleAtTime = useCallback(
    async (hour: number, minute: number) => {
      try {
        const now = new Date();
        const targetDate = new Date();
        targetDate.setHours(hour, minute, 0, 0);

        // If the time has passed today, schedule for tomorrow
        if (targetDate <= now) {
          targetDate.setDate(targetDate.getDate() + 1);
        }

        const dateISO = targetDate.toISOString();
        const timeStr = `${hour.toString().padStart(2, "0")}:${minute.toString().padStart(2, "0")}`;
        const a = await scheduleAlarm({
          dateISO,
          label: `Alarm ${timeStr}`,
          android:
            Platform.OS === "android"
              ? {
                  smallIconName: config.androidSmallIcon || undefined,
                  accentColor: config.androidAccentColor,
                  useChronometer: config.androidUseChrono,
                  showOverlayWhenUnlocked: config.androidOverlayUnlocked,
                  overlayBackgroundColor: config.androidOverlayBg,
                  overlayTextColor: config.androidOverlayText,
                  overlayButtonBackgroundColor: config.androidOverlayBtnBg,
                  overlayButtonTextColor: config.androidOverlayBtnText,
                  snoozeMinutes: 3,
                }
              : undefined,
          ios:
            Platform.OS === "ios"
              ? {
                  snoozeMinutes: 3,
                }
              : undefined,
        });
        setLastScheduled(a);
        await refresh();
      } catch (e: any) {
        Alert.alert("Error", String(e?.message ?? e));
      }
    },
    [config, refresh],
  );

  const onCancelLast = useCallback(async () => {
    try {
      const target = lastScheduled ?? alarms[alarms.length - 1];
      if (!target) {
        Alert.alert("Info", "No alarm to cancel");
        return;
      }
      await cancelAlarm(target.id);
      if (lastScheduled?.id === target.id) setLastScheduled(null);
      await refresh();
    } catch (e: any) {
      Alert.alert("Error", String(e?.message ?? e));
    }
  }, [alarms, lastScheduled, refresh]);

  const onCancelAlarm = useCallback(
    async (alarmId: string) => {
      try {
        await cancelAlarm(alarmId);
        if (lastScheduled?.id === alarmId) setLastScheduled(null);
        await refresh();
      } catch (e: any) {
        Alert.alert("Error", String(e?.message ?? e));
      }
    },
    [lastScheduled, refresh],
  );

  const onCancelAll = useCallback(async () => {
    try {
      await cancelAll();
      setLastScheduled(null);
      await refresh();
    } catch (e: any) {
      Alert.alert("Error", String(e?.message ?? e));
    }
  }, [refresh]);

  return {
    alarms,
    lastScheduled,
    refreshAlarms: refresh,
    onScheduleIn,
    onStartTimer,
    onScheduleAtTime,
    onCancelLast,
    onCancelAlarm,
    onCancelAll,
  };
}

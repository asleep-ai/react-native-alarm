import { useState, useEffect } from "react";
import { Platform } from "react-native";
import { isAvailable, checkAlarmStates, type AlarmState } from "@asleep-ai/react-native-alarm";

export function useAlarmState() {
  const [available, setAvailable] = useState<boolean>(false);
  const [alarmState, setAlarmState] = useState<AlarmState | null>(null);
  const [alarmHistory, setAlarmHistory] = useState<AlarmState | null>(null);

  useEffect(() => {
    setAvailable(isAvailable());
  }, []);

  // Periodically check alarm states (especially for iOS)
  useEffect(() => {
    if (Platform.OS === "ios" && available) {
      const id = setInterval(async () => {
        try {
          await checkAlarmStates();
        } catch {
          // Ignore errors
        }
      }, 1000); // Check every second
      return () => clearInterval(id);
    }
  }, [available]);

  const saveToHistory = () => {
    if (alarmState) {
      setAlarmHistory(alarmState);
    }
  };

  const clearState = () => {
    setAlarmState(null);
  };

  return {
    available,
    alarmState,
    alarmHistory,
    setAlarmState,
    saveToHistory,
    clearState,
  };
}

import { useState, useEffect, useCallback } from "react";
import { Platform, Alert } from "react-native";
import {
  requestPermission,
  openSettings,
  canScheduleExactAlarms,
  openExactAlarmSettings,
  openOverlayPermissionSettings,
  hasOverlayPermission,
} from "@asleep-ai/react-native-alarm";

export function usePermissions() {
  const [exactAllowed, setExactAllowed] = useState<boolean>(false);
  const [overlayAllowed, setOverlayAllowed] = useState<boolean>(false);

  useEffect(() => {
    if (Platform.OS === "android") {
      try {
        setExactAllowed(canScheduleExactAlarms());
        setOverlayAllowed(hasOverlayPermission());
      } catch {
        setExactAllowed(false);
        setOverlayAllowed(false);
      }
    }
  }, []);

  const onRequestPermission = useCallback(async () => {
    try {
      const result = await requestPermission();
      if (result.granted) {
        Alert.alert("Permission", "granted");
      } else {
        if (result.status === "denied") {
          Alert.alert(
            "Permission Denied",
            "Alarm permission has been denied. Please enable it in Settings.",
            [
              { text: "Cancel", style: "cancel" },
              {
                text: "Open Settings",
                onPress: async () => {
                  await openSettings();
                },
              },
            ]
          );
        } else {
          Alert.alert("Permission", "denied");
        }
      }
    } catch (e: any) {
      Alert.alert("Error", String(e?.message ?? e));
    }
  }, []);

  const onOpenExactAlarmSettings = useCallback(async () => {
    try {
      const ok = await openExactAlarmSettings();
      if (!ok) {
        Alert.alert(
          "Info",
          "Unable to open settings. You can allow exact alarms in system settings."
        );
      }
    } catch {
      Alert.alert("Error", "Failed to open exact alarm settings.");
    } finally {
      setTimeout(() => {
        try {
          setExactAllowed(canScheduleExactAlarms());
          setOverlayAllowed(hasOverlayPermission());
        } catch {
          setExactAllowed(false);
          setOverlayAllowed(false);
        }
      }, 1000);
    }
  }, []);

  const onOpenOverlayPermissionSettings = useCallback(async () => {
    try {
      const ok = await openOverlayPermissionSettings();
      if (!ok) {
        Alert.alert(
          "Info",
          "Unable to open overlay settings. You can allow overlays in system settings."
        );
      }
    } catch {
      Alert.alert("Error", "Failed to open overlay settings.");
    } finally {
      setTimeout(() => {
        try {
          setOverlayAllowed(hasOverlayPermission());
        } catch {
          setOverlayAllowed(false);
        }
      }, 1000);
    }
  }, []);

  return {
    exactAllowed,
    overlayAllowed,
    onRequestPermission,
    onOpenExactAlarmSettings,
    onOpenOverlayPermissionSettings,
  };
}

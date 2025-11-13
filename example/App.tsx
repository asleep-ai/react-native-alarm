import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  isAvailable,
  requestPermission,
  scheduleAlarm,
  getAlarms,
  cancelAlarm,
  cancelAll,
  type Alarm,
} from "@asleep-ai/react-native-alarm";
import {
  Button,
  SafeAreaView,
  ScrollView,
  Text,
  View,
  Alert,
  TextInput,
  Switch,
  TouchableOpacity,
} from "react-native";
import { Platform } from "react-native";
import {
  canScheduleExactAlarms,
  openExactAlarmSettings,
  openOverlayPermissionSettings,
  hasOverlayPermission,
  configure,
} from "@asleep-ai/react-native-alarm";

export default function App() {
  const [available, setAvailable] = useState<boolean>(false);
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [lastScheduled, setLastScheduled] = useState<Alarm | null>(null);
  const [nowISO, setNowISO] = useState<string>(new Date().toISOString());
  const [exactAllowed, setExactAllowed] = useState<boolean>(false);
  const [overlayAllowed, setOverlayAllowed] = useState<boolean>(false);
  // Customization state
  const [androidSmallIcon, setAndroidSmallIcon] = useState<string>("");
  const [androidAccentColor, setAndroidAccentColor] =
    useState<string>("#4CAF50");
  const [androidUseChrono, setAndroidUseChrono] = useState<boolean>(true);
  const [androidOverlayUnlocked, setAndroidOverlayUnlocked] =
    useState<boolean>(true);
  const [androidOverlayBg, setAndroidOverlayBg] = useState<string>("#000000");
  const [androidOverlayText, setAndroidOverlayText] =
    useState<string>("#FFFFFF");
  const [androidSnoozeMin, setAndroidSnoozeMin] = useState<string>("5");
  const [androidOverlayBtnBg, setAndroidOverlayBtnBg] =
    useState<string>("#3b82f6");
  const [androidOverlayBtnText, setAndroidOverlayBtnText] =
    useState<string>("#FFFFFF");
  const [iosTint, setIosTint] = useState<string>("#007AFF");
  const [iosStopText, setIosStopText] = useState<string>("Done");
  const [iosPauseText, setIosPauseText] = useState<string>("Pause");
  const [iosResumeText, setIosResumeText] = useState<string>("Start");

  useEffect(() => {
    setAvailable(isAvailable());
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

  useEffect(() => {
    const id = setInterval(() => {
      setNowISO(new Date().toISOString());
    }, 1000);
    return () => clearInterval(id);
  }, []);

  const refreshAlarms = useCallback(async () => {
    try {
      const list = await getAlarms();
      setAlarms(list);
    } catch (e: any) {
      Alert.alert("Error", String(e?.message ?? e));
    }
  }, []);

  const onRequestPermission = useCallback(async () => {
    try {
      const granted = await requestPermission();
      Alert.alert("Permission", granted ? "granted" : "denied");
    } catch (e: any) {
      Alert.alert("Error", String(e?.message ?? e));
    }
  }, []);

  const onScheduleIn = useCallback(
    async (seconds: number) => {
      try {
        // Ensure only one AlarmKit activity at a time for predictable demo behavior.
        await cancelAll();
        const dateISO = new Date(Date.now() + seconds * 1000).toISOString();
        const a = await scheduleAlarm({
          dateISO,
          label: `Test Alarm (${seconds}s)`,
          // Ensure Live Activity (countdown) appears immediately until the scheduled time.
          countdownSeconds: seconds,
          android:
            Platform.OS === "android"
              ? {
                  smallIconName: androidSmallIcon || undefined,
                  accentColor: androidAccentColor,
                  useChronometer: androidUseChrono,
                  showOverlayWhenUnlocked: androidOverlayUnlocked,
                  overlayBackgroundColor: androidOverlayBg,
                  overlayTextColor: androidOverlayText,
                  overlayButtonBackgroundColor: androidOverlayBtnBg,
                  overlayButtonTextColor: androidOverlayBtnText,
                  snoozeMinutes: Number(androidSnoozeMin) || 5,
                }
              : undefined,
        });
        setLastScheduled(a);
        await refreshAlarms();
      } catch (e: any) {
        Alert.alert("Error", String(e?.message ?? e));
      }
    },
    [
      refreshAlarms,
      androidSmallIcon,
      androidAccentColor,
      androidUseChrono,
      androidOverlayUnlocked,
      androidOverlayBg,
      androidOverlayText,
    ]
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
      await refreshAlarms();
    } catch (e: any) {
      Alert.alert("Error", String(e?.message ?? e));
    }
  }, [alarms, lastScheduled, refreshAlarms]);

  const onStartTimer = useCallback(
    async (seconds: number) => {
      try {
        // Ensure only one AlarmKit activity at a time for predictable demo behavior.
        await cancelAll();
        const a = await scheduleAlarm({
          label: `Timer (${seconds}s)`,
          countdownSeconds: seconds,
          android:
            Platform.OS === "android"
              ? {
                  smallIconName: androidSmallIcon || undefined,
                  accentColor: androidAccentColor,
                  useChronometer: androidUseChrono,
                  showOverlayWhenUnlocked: androidOverlayUnlocked,
                  overlayBackgroundColor: androidOverlayBg,
                  overlayTextColor: androidOverlayText,
                  overlayButtonBackgroundColor: androidOverlayBtnBg,
                  overlayButtonTextColor: androidOverlayBtnText,
                  snoozeMinutes: Number(androidSnoozeMin) || 5,
                }
              : undefined,
        });
        setLastScheduled(a);
        await refreshAlarms();
      } catch (e: any) {
        Alert.alert("Error", String(e?.message ?? e));
      }
    },
    [
      refreshAlarms,
      androidSmallIcon,
      androidAccentColor,
      androidUseChrono,
      androidOverlayUnlocked,
      androidOverlayBg,
      androidOverlayText,
    ]
  );

  const availabilityText = useMemo(
    () => (available ? "Available (iOS 26+)" : "Not available on this device"),
    [available]
  );

  const onApplyConfig = useCallback(async () => {
    try {
      await configure({
        android: {
          smallIconName: androidSmallIcon || undefined,
          accentColor: androidAccentColor,
          useChronometer: androidUseChrono,
          showOverlayWhenUnlocked: androidOverlayUnlocked,
          overlayBackgroundColor: androidOverlayBg,
          overlayTextColor: androidOverlayText,
          overlayButtonBackgroundColor: androidOverlayBtnBg,
          overlayButtonTextColor: androidOverlayBtnText,
          snoozeMinutes: Number(androidSnoozeMin) || 5,
        },
        ios: {
          tintColorHex: iosTint,
          alertStopText: iosStopText,
          countdownPauseText: iosPauseText,
          pausedResumeText: iosResumeText,
        },
      });
      Alert.alert("Config", "Applied");
    } catch (e: any) {
      Alert.alert("Error", String(e?.message ?? e));
    }
  }, [
    androidSmallIcon,
    androidAccentColor,
    androidUseChrono,
    androidOverlayUnlocked,
    androidOverlayBg,
    androidOverlayText,
    androidOverlayBtnBg,
    androidOverlayBtnText,
    androidSnoozeMin,
    iosTint,
    iosStopText,
    iosPauseText,
    iosResumeText,
  ]);

  const ColorSwatch = (props: {
    color: string;
    onPress: () => void;
    selected?: boolean;
  }) => (
    <TouchableOpacity
      onPress={props.onPress}
      style={{
        width: 36,
        height: 36,
        borderRadius: 18,
        backgroundColor: props.color,
        borderWidth: props.selected ? 3 : 1,
        borderColor: props.selected ? "#000" : "#999",
      }}
    />
  );

  const IconChip = (props: {
    name: string;
    selected: boolean;
    onPress: () => void;
  }) => (
    <TouchableOpacity
      onPress={props.onPress}
      style={{
        paddingVertical: 6,
        paddingHorizontal: 10,
        borderRadius: 14,
        backgroundColor: props.selected ? "#3b82f6" : "#e5e7eb",
        marginRight: 8,
      }}
    >
      <Text style={{ color: props.selected ? "#fff" : "#111" }}>
        {props.name}
      </Text>
    </TouchableOpacity>
  );

  const iconOptions = ["", "ic_stat_alarm", "ic_stat_timer", "ic_alarm_small"];

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>@asleep-ai/react-native-alarm</Text>

        <Group name="Availability">
          <Text>{availabilityText}</Text>
          {!available ? (
            <Text style={{ marginTop: 8 }}>
              AlarmKit requires iOS 26+. On Android this example is a no-op.
            </Text>
          ) : null}
        </Group>

        <Group name="Actions">
          <View style={{ gap: 12 }}>
            <Button title="Request Permission" onPress={onRequestPermission} />
            {Platform.OS === "android" ? (
              <>
                <Text>Exact alarms allowed: {String(exactAllowed)}</Text>
                <Text>Overlay allowed: {String(overlayAllowed)}</Text>
                <Button
                  title="Open Exact Alarm Settings"
                  onPress={async () => {
                    try {
                      const ok = await openExactAlarmSettings();
                      if (!ok) {
                        Alert.alert(
                          "Info",
                          "Unable to open settings. You can allow exact alarms in system settings."
                        );
                      }
                    } catch {
                      Alert.alert(
                        "Error",
                        "Failed to open exact alarm settings."
                      );
                    } finally {
                      // Refresh flag after a short delay
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
                  }}
                />
                <Button
                  title="Open Overlay Permission Settings"
                  onPress={async () => {
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
                  }}
                />
              </>
            ) : null}
            <Button
              title="Schedule Alarm in 5s"
              onPress={() => onScheduleIn(5)}
            />
            <Button
              title="Schedule Alarm in 10s"
              onPress={() => onScheduleIn(10)}
            />
            <Button
              title="Schedule Alarm in 30s"
              onPress={() => onScheduleIn(30)}
            />
            <Button title="Refresh Alarms" onPress={refreshAlarms} />
            <Button title="Cancel Last Alarm" onPress={onCancelLast} />
            <View style={{ height: 12 }} />
            <Button title="Start 10s Timer" onPress={() => onStartTimer(10)} />
          </View>
          <Text style={{ marginTop: 12 }}>Now: {nowISO}</Text>
          {lastScheduled ? (
            <Text style={{ marginTop: 12 }}>
              Last scheduled: {lastScheduled.label ?? "(no label)"} —{" "}
              {lastScheduled.dateISO}
            </Text>
          ) : null}
        </Group>

        <Group name="Customization (Global)">
          <View style={{ gap: 12 }}>
            {Platform.OS === "android" ? (
              <>
                <Text style={{ fontWeight: "600" }}>Android</Text>
                <Text>Small icon drawable name (optional)</Text>
                <TextInput
                  value={androidSmallIcon}
                  onChangeText={setAndroidSmallIcon}
                  placeholder="e.g. ic_stat_alarm"
                  style={{
                    backgroundColor: "#fff",
                    padding: 8,
                    borderRadius: 6,
                  }}
                />
                <Text>Accent color (hex)</Text>
                <View style={{ flexDirection: "row", gap: 12 }}>
                  {["#4CAF50", "#2196F3", "#E91E63", "#FF9800"].map((c) => (
                    <ColorSwatch
                      key={c}
                      color={c}
                      selected={androidAccentColor === c}
                      onPress={() => setAndroidAccentColor(c)}
                    />
                  ))}
                </View>
                <Text>Snooze minutes</Text>
                <TextInput
                  value={androidSnoozeMin}
                  onChangeText={setAndroidSnoozeMin}
                  placeholder="e.g. 5"
                  keyboardType="number-pad"
                  style={{
                    backgroundColor: "#fff",
                    padding: 8,
                    borderRadius: 6,
                  }}
                />
                <TextInput
                  value={androidAccentColor}
                  onChangeText={setAndroidAccentColor}
                  placeholder="#RRGGBB"
                  style={{
                    backgroundColor: "#fff",
                    padding: 8,
                    borderRadius: 6,
                  }}
                />
                <Text>Small icon</Text>
                <View
                  style={{
                    flexDirection: "row",
                    flexWrap: "wrap",
                    marginVertical: 4,
                  }}
                >
                  {iconOptions.map((n) => (
                    <IconChip
                      key={n || "(default)"}
                      name={n || "(default)"}
                      selected={(n || "") === (androidSmallIcon || "")}
                      onPress={() => setAndroidSmallIcon(n)}
                    />
                  ))}
                </View>
                <View
                  style={{ flexDirection: "row", alignItems: "center", gap: 8 }}
                >
                  <Text>Use chronometer</Text>
                  <Switch
                    value={androidUseChrono}
                    onValueChange={setAndroidUseChrono}
                  />
                </View>
                <View
                  style={{ flexDirection: "row", alignItems: "center", gap: 8 }}
                >
                  <Text>Overlay when unlocked</Text>
                  <Switch
                    value={androidOverlayUnlocked}
                    onValueChange={setAndroidOverlayUnlocked}
                  />
                </View>
                <Text>Overlay background</Text>
                <View style={{ flexDirection: "row", gap: 12 }}>
                  {["#000000", "#222222", "#880000", "#004488"].map((c) => (
                    <ColorSwatch
                      key={c}
                      color={c}
                      selected={androidOverlayBg === c}
                      onPress={() => setAndroidOverlayBg(c)}
                    />
                  ))}
                </View>
                <Text>Overlay text</Text>
                <View style={{ flexDirection: "row", gap: 12 }}>
                  {["#FFFFFF", "#FFD700", "#00E5FF", "#FFCDD2"].map((c) => (
                    <ColorSwatch
                      key={c}
                      color={c}
                      selected={androidOverlayText === c}
                      onPress={() => setAndroidOverlayText(c)}
                    />
                  ))}
                </View>
                <Text>Overlay button background</Text>
                <View style={{ flexDirection: "row", gap: 12 }}>
                  {["#3b82f6", "#10b981", "#ef4444", "#6b7280"].map((c) => (
                    <ColorSwatch
                      key={c}
                      color={c}
                      selected={androidOverlayBtnBg === c}
                      onPress={() => setAndroidOverlayBtnBg(c)}
                    />
                  ))}
                </View>
                <Text>Overlay button text</Text>
                <View style={{ flexDirection: "row", gap: 12 }}>
                  {["#FFFFFF", "#111111", "#FFD700", "#00E5FF"].map((c) => (
                    <ColorSwatch
                      key={c}
                      color={c}
                      selected={androidOverlayBtnText === c}
                      onPress={() => setAndroidOverlayBtnText(c)}
                    />
                  ))}
                </View>
              </>
            ) : (
              <>
                <Text style={{ fontWeight: "600" }}>iOS</Text>
                <Text>tintColor (hex)</Text>
                <View style={{ flexDirection: "row", gap: 12 }}>
                  {["#007AFF", "#4A90E2", "#E91E63", "#4CAF50"].map((c) => (
                    <ColorSwatch
                      key={c}
                      color={c}
                      selected={iosTint === c}
                      onPress={() => setIosTint(c)}
                    />
                  ))}
                </View>
                <TextInput
                  value={iosTint}
                  onChangeText={setIosTint}
                  placeholder="#RRGGBB"
                  style={{
                    backgroundColor: "#fff",
                    padding: 8,
                    borderRadius: 6,
                  }}
                />
                <Text>Stop button text</Text>
                <TextInput
                  value={iosStopText}
                  onChangeText={setIosStopText}
                  style={{
                    backgroundColor: "#fff",
                    padding: 8,
                    borderRadius: 6,
                  }}
                />
                <Text>Pause button text</Text>
                <TextInput
                  value={iosPauseText}
                  onChangeText={setIosPauseText}
                  style={{
                    backgroundColor: "#fff",
                    padding: 8,
                    borderRadius: 6,
                  }}
                />
                <Text>Resume button text</Text>
                <TextInput
                  value={iosResumeText}
                  onChangeText={setIosResumeText}
                  style={{
                    backgroundColor: "#fff",
                    padding: 8,
                    borderRadius: 6,
                  }}
                />
              </>
            )}
            <Button title="Apply Config" onPress={onApplyConfig} />
          </View>
        </Group>

        <Group name="Current Alarms">
          {alarms.length === 0 ? (
            <Text>None</Text>
          ) : (
            alarms.map((a) => (
              <View key={a.id} style={{ marginBottom: 12 }}>
                <Text>ID: {a.id}</Text>
                <Text>Date: {a.dateISO}</Text>
                <Text>Label: {a.label ?? "-"}</Text>
                <Text>Enabled: {String(a.enabled)}</Text>
              </View>
            ))
          )}
        </Group>
      </ScrollView>
    </SafeAreaView>
  );
}

function Group(props: { name: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{props.name}</Text>
      {props.children}
    </View>
  );
}

const styles = {
  header: {
    fontSize: 30,
    margin: 20,
  },
  groupHeader: {
    fontSize: 20,
    marginBottom: 20,
  },
  group: {
    margin: 20,
    backgroundColor: "#fff",
    borderRadius: 10,
    padding: 20,
  },
  container: {
    flex: 1,
    backgroundColor: "#eee",
  },
  view: {
    flex: 1,
    height: 200,
  },
};

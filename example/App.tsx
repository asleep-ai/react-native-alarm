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
} from "react-native";
import { Platform } from "react-native";
import {
  canScheduleExactAlarms,
  openExactAlarmSettings,
} from "@asleep-ai/react-native-alarm";

export default function App() {
  const [available, setAvailable] = useState<boolean>(false);
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [lastScheduled, setLastScheduled] = useState<Alarm | null>(null);
  const [nowISO, setNowISO] = useState<string>(new Date().toISOString());
  const [exactAllowed, setExactAllowed] = useState<boolean>(false);

  useEffect(() => {
    setAvailable(isAvailable());
    if (Platform.OS === "android") {
      try {
        setExactAllowed(canScheduleExactAlarms());
      } catch {
        setExactAllowed(false);
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
        });
        setLastScheduled(a);
        await refreshAlarms();
      } catch (e: any) {
        Alert.alert("Error", String(e?.message ?? e));
      }
    },
    [refreshAlarms]
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
        });
        setLastScheduled(a);
        await refreshAlarms();
      } catch (e: any) {
        Alert.alert("Error", String(e?.message ?? e));
      }
    },
    [refreshAlarms]
  );

  const availabilityText = useMemo(
    () => (available ? "Available (iOS 26+)" : "Not available on this device"),
    [available]
  );

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
                        } catch {
                          setExactAllowed(false);
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

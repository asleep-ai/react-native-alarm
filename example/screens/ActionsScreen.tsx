import React, { useState, useEffect } from "react";
import {
  ScrollView,
  Text,
  View,
  Button,
  StyleSheet,
  Platform,
} from "react-native";
import { Group } from "../components/Group";
import { AlarmStateView } from "../components/AlarmStateView";
import { AlarmCard } from "../components/AlarmCard";
import type { AlarmState } from "@asleep-ai/react-native-alarm";

interface ActionsScreenProps {
  alarmState: AlarmState | null;
  alarmHistory: AlarmState | null;
  alarms: any[];
  eventLog: string[];
  lastScheduled: any;
  nowISO: string;
  onRequestPermission: () => void;
  exactAllowed: boolean;
  overlayAllowed: boolean;
  onOpenExactAlarmSettings: () => void;
  onOpenOverlayPermissionSettings: () => void;
  onScheduleIn: (seconds: number) => void;
  onStartTimer: (seconds: number) => void;
  onCancelLast: () => void;
  onCancelAlarm: (alarmId: string) => void;
  onCancelAll: () => void;
  refreshAlarms: () => void;
}

export function ActionsScreen({
  alarmState,
  alarmHistory,
  alarms,
  eventLog,
  lastScheduled,
  nowISO,
  onRequestPermission,
  exactAllowed,
  overlayAllowed,
  onOpenExactAlarmSettings,
  onOpenOverlayPermissionSettings,
  onScheduleIn,
  onStartTimer,
  onCancelLast,
  onCancelAlarm,
  onCancelAll,
  refreshAlarms,
}: ActionsScreenProps) {
  return (
    <ScrollView style={styles.container}>
      <Text style={styles.header}>Actions</Text>

      <Group name="Real-time State">
        <AlarmStateView alarmState={alarmState} alarmHistory={alarmHistory} />
      </Group>

      <Group name="Quick Actions">
        <View style={styles.actionsContainer}>
          <Button title="Request Permission" onPress={onRequestPermission} />
          {Platform.OS === "android" ? (
            <>
              <Text>Exact alarms allowed: {String(exactAllowed)}</Text>
              <Text>Overlay allowed: {String(overlayAllowed)}</Text>
              <Button
                title="Open Exact Alarm Settings"
                onPress={onOpenExactAlarmSettings}
              />
              <Button
                title="Open Overlay Permission Settings"
                onPress={onOpenOverlayPermissionSettings}
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
          <Button title="Cancel All Alarms" onPress={onCancelAll} />
          <View style={{ height: 12 }} />
          <Button title="Start 10s Timer" onPress={() => onStartTimer(10)} />
        </View>
        <Text style={styles.nowText}>Now: {nowISO}</Text>
        {lastScheduled ? (
          <Text style={styles.lastScheduledText}>
            Last scheduled: {lastScheduled.label ?? "(no label)"} —{" "}
            {lastScheduled.dateISO}
          </Text>
        ) : null}
      </Group>

      <Group name="Current Alarms">
        {alarms.length === 0 ? (
          <Text style={styles.emptyText}>No alarms scheduled</Text>
        ) : (
          <View style={styles.alarmsContainer}>
            {alarms.map((a) => (
              <AlarmCard key={a.id} alarm={a} onCancel={onCancelAlarm} />
            ))}
          </View>
        )}
      </Group>

      <Group name="Event Log">
        {eventLog.length === 0 ? (
          <Text style={styles.emptyText}>No events yet</Text>
        ) : (
          <View style={styles.eventLogContainer}>
            {eventLog.map((log, index) => (
              <Text key={index} style={styles.eventLogText}>
                {log}
              </Text>
            ))}
          </View>
        )}
      </Group>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#eee",
    paddingBottom: 80,
  },
  header: {
    fontSize: 24,
    fontWeight: "600",
    margin: 20,
    marginBottom: 10,
  },
  actionsContainer: {
    gap: 12,
  },
  nowText: {
    marginTop: 12,
  },
  lastScheduledText: {
    marginTop: 12,
  },
  alarmsContainer: {
    gap: 12,
  },
  emptyText: {
    color: "#6b7280",
  },
  eventLogContainer: {
    gap: 4,
  },
  eventLogText: {
    fontSize: 12,
    fontFamily: Platform.OS === "ios" ? "Menlo" : "monospace",
    color: "#374151",
    paddingVertical: 2,
  },
});

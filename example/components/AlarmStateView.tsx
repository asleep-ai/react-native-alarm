import React from "react";
import { View, Text, StyleSheet } from "react-native";
import type { AlarmState } from "@asleep-ai/react-native-alarm";

interface AlarmStateViewProps {
  alarmState: AlarmState | null;
  alarmHistory: AlarmState | null;
}

export function AlarmStateView({
  alarmState,
  alarmHistory,
}: AlarmStateViewProps) {
  if (alarmState) {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <View
            style={[
              styles.indicator,
              {
                backgroundColor: alarmState.isRinging
                  ? "#ef4444"
                  : alarmState.isSnoozed
                    ? "#f59e0b"
                    : "#10b981",
              },
            ]}
          />
          <Text style={styles.title}>{alarmState.label || alarmState.id}</Text>
        </View>
        <Text>
          Status:{" "}
          {alarmState.isRinging
            ? "🔔 Ringing"
            : alarmState.isSnoozed
              ? "⏰ Snoozed"
              : "⏱️ Countdown"}
        </Text>
        {alarmState.remainingSeconds > 0 && (
          <Text>
            Remaining: {Math.floor(alarmState.remainingSeconds / 60)}m{" "}
            {alarmState.remainingSeconds % 60}s
          </Text>
        )}
        {alarmState.isSnoozed && alarmState.snoozeUntilISO && (
          <Text>
            Snooze until: {new Date(alarmState.snoozeUntilISO).toLocaleString()}
          </Text>
        )}
        {alarmState.stoppedAtISO && (
          <Text style={styles.muted}>
            Stopped at: {new Date(alarmState.stoppedAtISO).toLocaleString()}
          </Text>
        )}
      </View>
    );
  }

  if (alarmHistory) {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <View style={[styles.indicator, { backgroundColor: "#9ca3af" }]} />
          <Text style={styles.title}>
            {alarmHistory.label || alarmHistory.id}
          </Text>
          <Text style={styles.historyLabel}>(History)</Text>
        </View>
        <Text style={styles.muted}>
          Status:{" "}
          {alarmHistory.isRinging
            ? "🔔 Ringing"
            : alarmHistory.isSnoozed
              ? "⏰ Snoozed"
              : "⏱️ Countdown"}
        </Text>
        {alarmHistory.stoppedAtISO && (
          <Text style={styles.muted}>
            Stopped at: {new Date(alarmHistory.stoppedAtISO).toLocaleString()}
          </Text>
        )}
      </View>
    );
  }

  return <Text style={styles.muted}>No active alarm</Text>;
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  indicator: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  title: {
    fontWeight: "600",
    fontSize: 16,
  },
  historyLabel: {
    color: "#6b7280",
    fontSize: 12,
  },
  muted: {
    color: "#6b7280",
  },
});


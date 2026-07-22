import React from "react";
import { View, Text, Button, StyleSheet } from "react-native";
import type { Alarm } from "@asleep-ai/react-native-alarm";

interface AlarmCardProps {
  alarm: Alarm;
  onCancel: (alarmId: string) => void;
}

export function AlarmCard({ alarm, onCancel }: AlarmCardProps) {
  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <View style={styles.info}>
          <Text style={styles.label}>{alarm.label || "(No label)"}</Text>
          <Text style={styles.id}>ID: {alarm.id.substring(0, 8)}...</Text>
        </View>
        <Button title="Cancel" onPress={() => onCancel(alarm.id)} />
      </View>
      <View style={styles.details}>
        <Text style={styles.detailText}>
          <Text style={styles.detailLabel}>Date: </Text>
          {new Date(alarm.dateISO).toLocaleString()}
        </Text>
        <Text style={styles.detailText}>
          <Text style={styles.detailLabel}>Status: </Text>
          <Text
            style={[
              styles.statusText,
              {
                color: alarm.isRinging
                  ? "#ef4444"
                  : alarm.isSnoozed
                    ? "#f59e0b"
                    : alarm.enabled
                      ? "#10b981"
                      : "#ef4444",
              },
            ]}
          >
            {alarm.isRinging
              ? "🔔 Ringing"
              : alarm.isSnoozed
                ? "⏰ Snoozed"
                : alarm.enabled
                  ? "⏱️ Scheduled"
                  : "❌ Disabled"}
          </Text>
        </Text>
        {alarm.isSnoozed && alarm.snoozeUntilISO && (
          <Text style={styles.detailText}>
            <Text style={styles.detailLabel}>Snooze until: </Text>
            {new Date(alarm.snoozeUntilISO).toLocaleString()}
          </Text>
        )}
        {alarm.remainingSeconds !== undefined && alarm.remainingSeconds > 0 && (
          <Text style={styles.detailText}>
            <Text style={styles.detailLabel}>Remaining: </Text>
            {Math.floor(alarm.remainingSeconds / 60)}m {alarm.remainingSeconds % 60}s
          </Text>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    padding: 12,
    backgroundColor: "#f9fafb",
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#e5e7eb",
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    marginBottom: 8,
  },
  info: {
    flex: 1,
  },
  label: {
    fontWeight: "600",
    fontSize: 16,
    marginBottom: 4,
  },
  id: {
    fontSize: 12,
    color: "#6b7280",
  },
  details: {
    gap: 4,
  },
  detailText: {
    fontSize: 14,
  },
  detailLabel: {
    fontWeight: "600",
  },
  statusText: {
    fontWeight: "500",
  },
});

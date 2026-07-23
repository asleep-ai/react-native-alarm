# @asleep-ai/react-native-alarm

[![CI](https://github.com/asleep-ai/react-native-alarm/actions/workflows/ci.yml/badge.svg)](https://github.com/asleep-ai/react-native-alarm/actions/workflows/ci.yml)
[![npm version](https://img.shields.io/npm/v/%40asleep-ai%2Freact-native-alarm.svg)](https://www.npmjs.com/package/@asleep-ai/react-native-alarm)
[![license](https://img.shields.io/npm/l/%40asleep-ai%2Freact-native-alarm.svg)](LICENSE)

React Native alarm utilities powered by iOS AlarmKit (iOS 26+), built with Expo Modules API.

## Overview

This library provides a cross-platform API for scheduling alarms and timers in React Native applications. On iOS, it leverages the native AlarmKit framework (iOS 26+) to provide system-level alarm functionality with Live Activities and native alarm UI. On Android, it uses AlarmManager with customizable notification overlays.

## Requirements

- **React Native**: >= 0.74
- **React**: >= 18
- **Expo**: >= 51
- **iOS**: iOS 26.0 or later (AlarmKit requirement)
- **Android**: API level 23+ (Android 6.0+)

## Installation

### Step 1: Install the package

```bash
yarn add @asleep-ai/react-native-alarm
```

### Step 2: Install iOS dependencies

```bash
cd ios
npx pod-install
```

### Step 3: Configure iOS Info.plist

Add the `NSAlarmKitUsageDescription` key to your app's `Info.plist` file. This is required for AlarmKit to request authorization and display the permission dialog.

**For Expo apps**, add to `app.json`:

```json
{
  "expo": {
    "ios": {
      "infoPlist": {
        "NSAlarmKitUsageDescription": "We'll schedule alerts for alarms you create within our app."
      }
    }
  }
}
```

**For bare React Native apps**, add to `ios/YourApp/Info.plist`:

```xml
<key>NSAlarmKitUsageDescription</key>
<string>We'll schedule alerts for alarms you create within our app.</string>
```

**Important**: If `NSAlarmKitUsageDescription` is missing or empty, AlarmKit cannot request authorization and alarms won't be scheduled.

### Step 4: Configure Android (if needed)

The library automatically configures Android permissions. Ensure your `AndroidManifest.xml` includes notification permissions (usually handled by Expo).

## Quick Start

### Basic Usage

```typescript
import { isAvailable, requestPermission, scheduleAlarm, getAlarms, cancelAlarm } from "@asleep-ai/react-native-alarm";

async function setupAlarm() {
  // 1. Check availability (iOS 26+)
  if (!isAvailable()) {
    console.warn("AlarmKit not available on this device");
    return;
  }

  // 2. Request permission
  const result = await requestPermission();
  if (!result.granted) {
    console.error("Permission denied");
    return;
  }

  // 3. Schedule an alarm
  const alarm = await scheduleAlarm({
    dateISO: new Date(Date.now() + 60_000).toISOString(), // 1 minute from now
    label: "Wake up",
  });

  console.log("Scheduled alarm:", alarm);

  // 4. List all alarms
  const alarms = await getAlarms();
  console.log("All alarms:", alarms);

  // 5. Cancel an alarm
  await cancelAlarm(alarm.id);
}
```

## Complete Usage Guide

This guide is based on the actual implementation in the `example` folder. Follow these patterns to integrate alarms into your React Native app.

### 1. Setting Up Permissions

First, create a custom hook to manage permissions:

```typescript
// hooks/usePermissions.ts
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
        Alert.alert("Permission", "Granted");
      } else {
        if (result.status === "denied") {
          Alert.alert("Permission Denied", "Alarm permission has been denied. Please enable it in Settings.", [
            { text: "Cancel", style: "cancel" },
            {
              text: "Open Settings",
              onPress: async () => {
                await openSettings();
              },
            },
          ]);
        } else {
          Alert.alert("Permission", "Denied");
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
        Alert.alert("Info", "Unable to open settings. You can allow exact alarms in system settings.");
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
        Alert.alert("Info", "Unable to open overlay settings. You can allow overlays in system settings.");
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
```

### 2. Managing Alarm State

Create a hook to track alarm state:

```typescript
// hooks/useAlarmState.ts
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
        } catch (e) {
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
```

### 3. Setting Up Event Listeners

Create a hook to handle alarm events:

```typescript
// hooks/useAlarmListeners.ts
import { useEffect, useState } from "react";
import {
  addAlarmStartedListener,
  addAlarmSnoozedListener,
  addAlarmStoppedListener,
  addAlarmStateChangedListener,
  type AlarmState,
} from "@asleep-ai/react-native-alarm";

interface UseAlarmListenersProps {
  onStateChanged: (state: AlarmState) => void;
  onStopped: () => void;
}

export function useAlarmListeners({ onStateChanged, onStopped }: UseAlarmListenersProps) {
  const [eventLog, setEventLog] = useState<string[]>([]);

  useEffect(() => {
    const subscriptions = [
      addAlarmStartedListener((event) => {
        const log = `[${new Date().toLocaleTimeString()}] Alarm Started: ${event.label || event.id} (${event.remainingSeconds}s remaining)`;
        setEventLog((prev) => [log, ...prev].slice(0, 20)); // Keep last 20 events
        console.log("onAlarmStarted", event);
      }),
      addAlarmSnoozedListener((event) => {
        const log = `[${new Date().toLocaleTimeString()}] Alarm Snoozed: ${event.label || event.id} (until ${event.snoozeUntilISO})`;
        setEventLog((prev) => [log, ...prev].slice(0, 20));
        console.log("onAlarmSnoozed", event);
      }),
      addAlarmStoppedListener((event) => {
        const log = `[${new Date().toLocaleTimeString()}] Alarm Stopped: ${event.label || event.id} (at ${event.stoppedAtISO})`;
        setEventLog((prev) => [log, ...prev].slice(0, 20));
        console.log("onAlarmStopped", event);
        onStopped();
      }),
      addAlarmStateChangedListener((state) => {
        onStateChanged(state);
        console.log("onAlarmStateChanged", state);
      }),
    ];

    return () => {
      subscriptions.forEach((sub) => sub.remove());
    };
  }, [onStateChanged, onStopped]);

  return { eventLog };
}
```

### 4. Managing Alarms

Create a hook to handle alarm operations:

```typescript
// hooks/useAlarmActions.ts
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
    onCancelLast,
    onCancelAlarm,
    onCancelAll,
  };
}
```

### 5. Configuring Alarm Styles

Create a hook to manage alarm configuration:

```typescript
// hooks/useAlarmConfig.ts
import { useState, useCallback } from "react";
import { Alert } from "react-native";
import { configure } from "@asleep-ai/react-native-alarm";

export interface AlarmConfig {
  androidSmallIcon: string;
  androidAccentColor: string;
  androidUseChrono: boolean;
  androidOverlayUnlocked: boolean;
  androidOverlayBg: string;
  androidOverlayText: string;
  androidOverlayBtnBg: string;
  androidOverlayBtnText: string;
  iosTint: string;
  iosStopText: string;
  iosPauseText: string;
  iosResumeText: string;
}

export function useAlarmConfig() {
  const [androidSmallIcon, setAndroidSmallIcon] = useState<string>("");
  const [androidAccentColor, setAndroidAccentColor] = useState<string>("#4CAF50");
  const [androidUseChrono, setAndroidUseChrono] = useState<boolean>(true);
  const [androidOverlayUnlocked, setAndroidOverlayUnlocked] = useState<boolean>(true);
  const [androidOverlayBg, setAndroidOverlayBg] = useState<string>("#000000");
  const [androidOverlayText, setAndroidOverlayText] = useState<string>("#FFFFFF");
  const [androidOverlayBtnBg, setAndroidOverlayBtnBg] = useState<string>("#3b82f6");
  const [androidOverlayBtnText, setAndroidOverlayBtnText] = useState<string>("#FFFFFF");
  const [iosTint, setIosTint] = useState<string>("#007AFF");
  const [iosStopText, setIosStopText] = useState<string>("Done");
  const [iosPauseText, setIosPauseText] = useState<string>("Pause");
  const [iosResumeText, setIosResumeText] = useState<string>("Start");

  const config: AlarmConfig = {
    androidSmallIcon,
    androidAccentColor,
    androidUseChrono,
    androidOverlayUnlocked,
    androidOverlayBg,
    androidOverlayText,
    androidOverlayBtnBg,
    androidOverlayBtnText,
    iosTint,
    iosStopText,
    iosPauseText,
    iosResumeText,
  };

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
    iosTint,
    iosStopText,
    iosPauseText,
    iosResumeText,
  ]);

  return {
    config,
    setters: {
      setAndroidSmallIcon,
      setAndroidAccentColor,
      setAndroidUseChrono,
      setAndroidOverlayUnlocked,
      setAndroidOverlayBg,
      setAndroidOverlayText,
      setAndroidOverlayBtnBg,
      setAndroidOverlayBtnText,
      setIosTint,
      setIosStopText,
      setIosPauseText,
      setIosResumeText,
    },
    onApplyConfig,
  };
}
```

### 6. Creating UI Components

Create components to display alarm state and alarms:

```typescript
// components/AlarmStateView.tsx
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { AlarmState } from '@asleep-ai/react-native-alarm';

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
                  ? '#ef4444'
                  : alarmState.isSnoozed
                    ? '#f59e0b'
                    : '#10b981',
              },
            ]}
          />
          <Text style={styles.title}>{alarmState.label || alarmState.id}</Text>
        </View>
        <Text>
          Status:{' '}
          {alarmState.isRinging
            ? '🔔 Ringing'
            : alarmState.isSnoozed
              ? '⏰ Snoozed'
              : '⏱️ Countdown'}
        </Text>
        {alarmState.remainingSeconds > 0 && (
          <Text>
            Remaining: {Math.floor(alarmState.remainingSeconds / 60)}m{' '}
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
          <View style={[styles.indicator, { backgroundColor: '#9ca3af' }]} />
          <Text style={styles.title}>
            {alarmHistory.label || alarmHistory.id}
          </Text>
          <Text style={styles.historyLabel}>(History)</Text>
        </View>
        <Text style={styles.muted}>
          Status:{' '}
          {alarmHistory.isRinging
            ? '🔔 Ringing'
            : alarmHistory.isSnoozed
              ? '⏰ Snoozed'
              : '⏱️ Countdown'}
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
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  indicator: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  title: {
    fontWeight: '600',
    fontSize: 16,
  },
  historyLabel: {
    color: '#6b7280',
    fontSize: 12,
  },
  muted: {
    color: '#6b7280',
  },
});
```

```typescript
// components/AlarmCard.tsx
import React from 'react';
import { View, Text, Button, StyleSheet } from 'react-native';
import type { Alarm } from '@asleep-ai/react-native-alarm';

interface AlarmCardProps {
  alarm: Alarm;
  onCancel: (alarmId: string) => void;
}

export function AlarmCard({ alarm, onCancel }: AlarmCardProps) {
  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <View style={styles.info}>
          <Text style={styles.label}>{alarm.label || '(No label)'}</Text>
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
                  ? '#ef4444'
                  : alarm.isSnoozed
                    ? '#f59e0b'
                    : alarm.enabled
                      ? '#10b981'
                      : '#ef4444',
              },
            ]}
          >
            {alarm.isRinging
              ? '🔔 Ringing'
              : alarm.isSnoozed
                ? '⏰ Snoozed'
                : alarm.enabled
                  ? '⏱️ Scheduled'
                  : '❌ Disabled'}
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
            {Math.floor(alarm.remainingSeconds / 60)}m{' '}
            {alarm.remainingSeconds % 60}s
          </Text>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    padding: 12,
    backgroundColor: '#f9fafb',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 8,
  },
  info: {
    flex: 1,
  },
  label: {
    fontWeight: '600',
    fontSize: 16,
    marginBottom: 4,
  },
  id: {
    fontSize: 12,
    color: '#6b7280',
  },
  details: {
    gap: 4,
  },
  detailText: {
    fontSize: 14,
  },
  detailLabel: {
    fontWeight: '600',
  },
  statusText: {
    fontWeight: '500',
  },
});
```

### 7. Putting It All Together

Here's how to use all these hooks in your main App component:

```typescript
// App.tsx
import React, { useState, useEffect } from 'react';
import { StyleSheet } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { useAlarmState } from './hooks/useAlarmState';
import { useAlarmListeners } from './hooks/useAlarmListeners';
import { useAlarmActions } from './hooks/useAlarmActions';
import { useAlarmConfig } from './hooks/useAlarmConfig';
import { usePermissions } from './hooks/usePermissions';
import { ActionsScreen } from './screens/ActionsScreen';

export default function App() {
  const [nowISO, setNowISO] = useState<string>(new Date().toISOString());

  const {
    available,
    alarmState,
    alarmHistory,
    setAlarmState,
    saveToHistory,
    clearState,
  } = useAlarmState();

  const { eventLog } = useAlarmListeners({
    onStateChanged: setAlarmState,
    onStopped: () => {
      saveToHistory();
      clearState();
      refreshAlarmsList();
    },
  });

  const { config } = useAlarmConfig();

  const {
    alarms,
    lastScheduled,
    refreshAlarms: refreshAlarmsList,
    onScheduleIn,
    onStartTimer,
    onCancelLast,
    onCancelAlarm,
    onCancelAll,
  } = useAlarmActions({
    config,
  });

  const {
    exactAllowed,
    overlayAllowed,
    onRequestPermission,
    onOpenExactAlarmSettings,
    onOpenOverlayPermissionSettings,
  } = usePermissions();

  useEffect(() => {
    const id = setInterval(() => {
      setNowISO(new Date().toISOString());
    }, 1000);
    return () => clearInterval(id);
  }, []);

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.container} edges={['top']}>
        <ActionsScreen
          alarmState={alarmState}
          alarmHistory={alarmHistory}
          alarms={alarms}
          eventLog={eventLog}
          lastScheduled={lastScheduled}
          nowISO={nowISO}
          onRequestPermission={onRequestPermission}
          exactAllowed={exactAllowed}
          overlayAllowed={overlayAllowed}
          onOpenExactAlarmSettings={onOpenExactAlarmSettings}
          onOpenOverlayPermissionSettings={onOpenOverlayPermissionSettings}
          onScheduleIn={onScheduleIn}
          onStartTimer={onStartTimer}
          onCancelLast={onCancelLast}
          onCancelAlarm={onCancelAlarm}
          onCancelAll={onCancelAll}
          refreshAlarms={refreshAlarmsList}
        />
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#eee',
  },
});
```

## API Reference

### Availability & Permissions

#### `isAvailable(): boolean`

Checks if AlarmKit is available on the current device. Returns `true` on iOS 26+ devices, `false` otherwise.

```typescript
if (isAvailable()) {
  // Proceed with alarm operations
}
```

#### `requestPermission(): Promise<{ granted: boolean; status: string }>`

Requests alarm permission from the user. On iOS, this requests AlarmKit authorization. On Android, this requests notification permission.

**Returns:** `Promise<{ granted: boolean; status: string }>` - Object with `granted` boolean and `status` string.

```typescript
const result = await requestPermission();
if (result.granted) {
  // Permission granted
} else if (result.status === "denied") {
  // Permission denied, guide user to settings
}
```

#### `canScheduleExactAlarms(): boolean` (Android only)

Checks if the app can schedule exact alarms on Android. Exact alarms are required for precise timing.

```typescript
if (Platform.OS === "android") {
  const canSchedule = canScheduleExactAlarms();
  if (!canSchedule) {
    // Guide user to enable exact alarms in settings
  }
}
```

#### `hasOverlayPermission(): boolean` (Android only)

Checks if the app has permission to display overlay windows (for alarm UI).

```typescript
if (Platform.OS === "android") {
  const hasOverlay = hasOverlayPermission();
}
```

#### `openExactAlarmSettings(): Promise<boolean>` (Android only)

Opens the system settings page for exact alarm permissions.

```typescript
const opened = await openExactAlarmSettings();
```

#### `openOverlayPermissionSettings(): Promise<boolean>` (Android only)

Opens the system settings page for overlay permissions.

```typescript
const opened = await openOverlayPermissionSettings();
```

#### `openSettings(): Promise<void>`

Opens the app's system settings page (iOS).

```typescript
await openSettings();
```

#### `getAndroidPermissionStatus(): Promise<{...}>` (Android only)

Returns the current status of all Android permissions.

```typescript
const status = await getAndroidPermissionStatus();
// {
//   notifications: boolean,
//   exactAlarmAllowed: boolean,
//   overlayAllowed: boolean,
//   ignoringBatteryOptimizations: boolean
// }
```

### Alarm Management

#### `scheduleAlarm(options: ScheduleAlarmOptions): Promise<Alarm>`

Schedules a new alarm or timer.

**Parameters:**

```typescript
type ScheduleAlarmOptions = {
  // ISO 8601 date string for when the alarm should fire
  dateISO?: string;

  // Label/name for the alarm
  label?: string;

  // Whether to allow snooze (iOS only)
  allowSnooze?: boolean;

  // Sound identifier (platform-specific)
  sound?: string;

  // Countdown duration in seconds (starts immediately)
  // If both dateISO and countdownSeconds are provided,
  // the alarm includes both a countdown and a scheduled alert
  countdownSeconds?: number;

  // Platform-specific styling (optional)
  android?: AndroidNotificationStyle;
  ios?: IOSAlarmStyle;
};
```

**Returns:** `Promise<Alarm>` - The created alarm object.

**Example 1: Schedule an alarm for a specific time**

```typescript
const alarm = await scheduleAlarm({
  dateISO: new Date("2024-12-25T08:00:00Z").toISOString(),
  label: "Christmas Morning",
});
```

**Example 2: Start a countdown timer**

```typescript
const timer = await scheduleAlarm({
  label: "Pomodoro Timer",
  countdownSeconds: 1500, // 25 minutes
});
```

**Example 3: Schedule alarm with countdown**

```typescript
// This creates a countdown that starts immediately and fires an alert at the scheduled time
const alarm = await scheduleAlarm({
  dateISO: new Date(Date.now() + 3600_000).toISOString(), // 1 hour from now
  label: "Meeting Reminder",
  countdownSeconds: 3600, // Countdown for 1 hour
});
```

**Example 4: With Android customization**

```typescript
const alarm = await scheduleAlarm({
  dateISO: new Date(Date.now() + 60_000).toISOString(),
  label: "Wake Up",
  android: {
    smallIconName: "ic_stat_alarm",
    accentColor: "#4CAF50",
    useChronometer: true,
    showOverlayWhenUnlocked: true,
    overlayBackgroundColor: "#000000",
    overlayTextColor: "#FFFFFF",
    overlayButtonBackgroundColor: "#3b82f6",
    overlayButtonTextColor: "#FFFFFF",
    snoozeMinutes: 5,
  },
});
```

**Example 5: With iOS customization**

```typescript
const alarm = await scheduleAlarm({
  dateISO: new Date(Date.now() + 60_000).toISOString(),
  label: "Wake Up",
  ios: {
    tintColorHex: "#007AFF",
    alertStopText: "Done",
    countdownPauseText: "Pause",
    pausedResumeText: "Start",
    snoozeMinutes: 5,
  },
});
```

#### `getAlarms(): Promise<Alarm[]>`

Retrieves all scheduled alarms.

**Returns:** `Promise<Alarm[]>` - Array of alarm objects.

```typescript
type Alarm = {
  id: string;
  dateISO: string; // ISO 8601 date string
  label?: string;
  enabled: boolean;
  // State information (optional, may not be present in all contexts)
  isRinging?: boolean;
  isSnoozed?: boolean;
  remainingSeconds?: number;
  snoozeUntilISO?: string;
};
```

**Example:**

```typescript
const alarms = await getAlarms();
alarms.forEach((alarm) => {
  console.log(`${alarm.label}: ${alarm.dateISO}`);
});
```

#### `cancelAlarm(id: string): Promise<void>`

Cancels a specific alarm by ID.

**Parameters:**

- `id: string` - The alarm ID returned from `scheduleAlarm()`

**Example:**

```typescript
const alarm = await scheduleAlarm({...});
// Later...
await cancelAlarm(alarm.id);
```

#### `cancelAll(): Promise<void>`

Cancels all scheduled alarms.

**Example:**

```typescript
await cancelAll();
```

#### `checkAlarmStates(): Promise<void>`

Re-checks alarm state on iOS and emits any transition events found. Since v0.2.0 it emits only on real transitions, so the recommended periodic call (below) no longer produces the old per-second event stream — it is cheap. Keep calling it periodically on iOS (or on app foreground) so snooze/stop performed from the system alarm UI are detected while your app is running.

**Example:**

```typescript
// Check every second (iOS)
useEffect(() => {
  if (Platform.OS === "ios" && available) {
    const id = setInterval(async () => {
      await checkAlarmStates();
    }, 1000);
    return () => clearInterval(id);
  }
}, [available]);
```

### Event Listeners

The library provides event listeners to track alarm state changes. This is useful for updating your app's UI when alarms start, stop, or are snoozed.

> **Edge-triggered since v0.2.0.** Events fire only on real state transitions
> (started, snoozed, unsnoozed, ringing, paused/resumed, stopped) — **not once
> per second**. In particular `onAlarmStateChanged` no longer emits a per-second
> "remaining ticked down" update, and its `remainingSeconds` is a snapshot taken
> at the transition. Earlier versions emitted ~1 event/second while a timer ran.
>
> **Do not forward these events straight to analytics**, and do not drive a
> per-second UI off their frequency. To show a live in-app countdown, seed a
> local timer from the `remainingSeconds` on `onAlarmStarted` (the lock-screen /
> Dynamic Island / notification countdown is rendered by the OS and needs
> nothing from JS):
>
> ```typescript
> import { addAlarmStartedListener } from "@asleep-ai/react-native-alarm";
>
> addAlarmStartedListener(({ remainingSeconds }) => {
>   const firesAt = Date.now() + remainingSeconds * 1000;
>   const timer = setInterval(() => {
>     const left = Math.max(0, Math.ceil((firesAt - Date.now()) / 1000));
>     setRemaining(left);
>     if (left === 0) clearInterval(timer);
>   }, 1000);
> });
> ```
>
> If you support pause/resume, also subscribe with `addAlarmStateChangedListener`
> and stop the local timer while `state.isPaused` is `true`, restarting from the
> `remainingSeconds` of the next event that arrives without it (resume clears it).

#### `addAlarmStartedListener(listener): Subscription`

Listens for when an alarm starts (countdown begins or alarm starts ringing).

```typescript
import { addAlarmStartedListener } from "@asleep-ai/react-native-alarm";

const subscription = addAlarmStartedListener((event) => {
  console.log("Alarm started:", event.id);
  console.log("Label:", event.label);
  console.log("Remaining seconds:", event.remainingSeconds);
  // Update your UI, show modal, etc.
});

// Don't forget to remove the listener when done
subscription.remove();
```

#### `addAlarmSnoozedListener(listener): Subscription`

Listens for when an alarm is snoozed.

```typescript
import { addAlarmSnoozedListener } from "@asleep-ai/react-native-alarm";

const subscription = addAlarmSnoozedListener((event) => {
  console.log("Alarm snoozed:", event.id);
  console.log("Snooze until:", event.snoozeUntilISO);
  // Update your UI
});

subscription.remove();
```

#### `addAlarmStoppedListener(listener): Subscription`

Listens for when an alarm is stopped.

```typescript
import { addAlarmStoppedListener } from "@asleep-ai/react-native-alarm";

const subscription = addAlarmStoppedListener((event) => {
  console.log("Alarm stopped:", event.id);
  console.log("Stopped at:", event.stoppedAtISO);
  // Update your UI, hide modal, etc.
});

subscription.remove();
```

#### `addAlarmStateChangedListener(listener): Subscription`

Listens for any alarm state changes. This provides comprehensive state information including whether the alarm is ringing, snoozed, and remaining time.

```typescript
import { addAlarmStateChangedListener, type AlarmState } from "@asleep-ai/react-native-alarm";

const subscription = addAlarmStateChangedListener((state: AlarmState) => {
  console.log("Alarm state changed:", state.id);
  console.log("Is ringing:", state.isRinging);
  console.log("Is snoozed:", state.isSnoozed);
  console.log("Remaining seconds:", state.remainingSeconds);
  console.log("Stopped at:", state.stoppedAtISO);
  console.log("Snooze until:", state.snoozeUntilISO);

  // Use this to update your app's UI
  if (state.isRinging) {
    // Show alarm modal in your app
  } else if (state.isSnoozed) {
    // Show snooze status
  }
});

subscription.remove();
```

**Event Types:**

```typescript
type AlarmState = {
  id: string;
  label?: string;
  isRinging: boolean; // 현재 알람이 울리고 있는지
  isSnoozed: boolean; // 현재 스누즈 중인지
  remainingSeconds: number; // 남은 시간 (초)
  stoppedAtISO?: string; // 알람이 정지된 시간 (ISO 8601)
  snoozeUntilISO?: string; // 스누즈가 끝나는 시간 (ISO 8601)
};
```

**Example: Using events with React hooks**

```typescript
import { useEffect, useState } from 'react';
import {
  addAlarmStateChangedListener,
  type AlarmState,
} from '@asleep-ai/react-native-alarm';

function AlarmMonitor() {
  const [currentAlarm, setCurrentAlarm] = useState<AlarmState | null>(null);

  useEffect(() => {
    const subscription = addAlarmStateChangedListener((state) => {
      setCurrentAlarm(state);
    });

    return () => {
      subscription.remove();
    };
  }, []);

  if (!currentAlarm) {
    return null;
  }

  return (
    <View>
      {currentAlarm.isRinging && (
        <Text>Alarm is ringing: {currentAlarm.label}</Text>
      )}
      {currentAlarm.isSnoozed && (
        <Text>Snoozed until: {currentAlarm.snoozeUntilISO}</Text>
      )}
      {currentAlarm.remainingSeconds > 0 && (
        <Text>Time remaining: {currentAlarm.remainingSeconds}s</Text>
      )}
    </View>
  );
}
```

#### `removeAllListeners(): void`

Removes all event listeners at once.

```typescript
import { removeAllListeners } from "@asleep-ai/react-native-alarm";

removeAllListeners();
```

### Configuration

#### `configure(config: ReactNativeAlarmConfig): Promise<void>`

Sets global configuration for alarms. This configuration applies to all subsequently scheduled alarms unless overridden per-alarm.

**Parameters:**

```typescript
type ReactNativeAlarmConfig = {
  android?: AndroidNotificationStyle;
  ios?: IOSAlarmStyle;
};

type AndroidNotificationStyle = {
  timerChannelId?: string; // default: 'react_native_alarm_timers_high'
  alertChannelId?: string; // default: 'react_native_alarm_alerts'
  smallIconName?: string; // Android drawable name (e.g., 'ic_stat_alarm')
  accentColor?: string; // Hex color (e.g., '#FF4081')
  useChronometer?: boolean; // default: true
  showOverlayWhenUnlocked?: boolean; // default: true
  overlayBackgroundColor?: string; // Hex color for overlay background
  overlayTextColor?: string; // Hex color for overlay text
  overlayButtonBackgroundColor?: string; // Hex color for button background
  overlayButtonTextColor?: string; // Hex color for button text
  snoozeMinutes?: number; // default: 5
};

type IOSAlarmStyle = {
  tintColorHex?: string; // Hex color (e.g., '#007AFF')
  alertStopText?: string; // default: 'Done'
  countdownPauseText?: string; // default: 'Pause'
  pausedResumeText?: string; // default: 'Start'
  snoozeMinutes?: number; // default: 5
};
```

**Example:**

```typescript
await configure({
  android: {
    accentColor: "#4CAF50",
    smallIconName: "ic_stat_alarm",
    useChronometer: true,
    showOverlayWhenUnlocked: true,
    overlayBackgroundColor: "#000000",
    overlayTextColor: "#FFFFFF",
    overlayButtonBackgroundColor: "#3b82f6",
    overlayButtonTextColor: "#FFFFFF",
    snoozeMinutes: 5,
  },
  ios: {
    tintColorHex: "#007AFF",
    alertStopText: "Done",
    countdownPauseText: "Pause",
    pausedResumeText: "Start",
    snoozeMinutes: 5,
  },
});
```

## Platform-Specific Features

### iOS (AlarmKit)

- **Live Activities**: Countdown timers appear as Live Activities on the Lock Screen and Dynamic Island
- **Native Alarm UI**: System-level alarm interface with pause/resume/stop controls
- **Snooze Support**: Built-in snooze functionality
- **Dynamic Island Integration**: Timers appear in the Dynamic Island on supported devices

**Important Notes:**

- Requires iOS 26.0 or later
- AlarmKit is weak-linked, so the library checks availability at runtime
- Alarms persist across app restarts
- System manages alarm presentation and user interactions
- Periodic state checks may be needed using `checkAlarmStates()`

### Android

- **Notification Channels**: Separate channels for timers and alerts
- **Overlay UI**: Customizable overlay window for alarm dismissal
- **Exact Alarms**: Requires user permission for precise timing
- **Battery Optimization**: May need to be disabled for reliable alarms

**Important Notes:**

- Exact alarm permission must be granted by the user
- Overlay permission required for full-screen alarm UI
- Battery optimization may affect alarm reliability
- Custom notification icons can be added to `android/app/src/main/res/drawable/`

## Android Customization

### Adding Custom Icons

To use custom notification icons on Android:

1. Add your icon drawable files to `android/app/src/main/res/drawable/`
2. Reference them by name in your configuration:

```typescript
await configure({
  android: {
    smallIconName: "ic_stat_alarm", // Your drawable name without extension
  },
});
```

Common icon names:

- `ic_stat_alarm`
- `ic_stat_timer`
- `ic_alarm_small`

### Notification Channels

The library creates two notification channels:

- **Timers**: `react_native_alarm_timers_high` (high priority)
- **Alerts**: `react_native_alarm_alerts` (default priority)

You can customize these channel IDs in the configuration.

## Troubleshooting

### iOS Issues

**"AlarmKit not available"**

- Ensure you're running on iOS 26.0 or later
- Check that AlarmKit framework is available (weak-linked)

**"Permission denied"**

- User must grant alarm permission in system settings
- Check permission status before scheduling alarms
- Use `openSettings()` to guide users to settings

**Alarms not firing**

- Verify the device is not in Do Not Disturb mode
- Check that alarms are enabled in system settings
- Ensure `checkAlarmStates()` is called periodically for iOS

### Android Issues

**Alarms not firing on time**

- Ensure exact alarm permission is granted
- Check battery optimization settings
- Verify overlay permission if using overlay UI

**Overlay not showing**

- Request overlay permission: `openOverlayPermissionSettings()`
- Check that `showOverlayWhenUnlocked` is enabled

**Custom icons not showing**

- Verify icon files are in `android/app/src/main/res/drawable/`
- Check that icon names match exactly (case-sensitive)
- Rebuild the app after adding icons

## Best Practices

1. **Always check availability** before using alarm features:

   ```typescript
   if (!isAvailable()) {
     // Handle gracefully
     return;
   }
   ```

2. **Request permission early** in your app flow:

   ```typescript
   useEffect(() => {
     requestPermission();
   }, []);
   ```

3. **Handle errors gracefully**:

   ```typescript
   try {
     await scheduleAlarm({...});
   } catch (error) {
     console.error('Failed to schedule alarm:', error);
     // Show user-friendly error message
   }
   ```

4. **Refresh alarm list** after scheduling/canceling:

   ```typescript
   await scheduleAlarm({...});
   const updated = await getAlarms();
   ```

5. **Configure globally** at app startup for consistent styling:

   ```typescript
   useEffect(() => {
     configure({
       android: { accentColor: "#4CAF50" },
       ios: { tintColorHex: "#007AFF" },
     });
   }, []);
   ```

6. **Use event listeners** to keep UI in sync with alarm state:

   ```typescript
   useEffect(() => {
     const subscription = addAlarmStateChangedListener((state) => {
       setAlarmState(state);
     });
     return () => subscription.remove();
   }, []);
   ```

7. **Periodically check states on iOS**:
   ```typescript
   useEffect(() => {
     if (Platform.OS === "ios" && available) {
       const id = setInterval(async () => {
         await checkAlarmStates();
       }, 1000);
       return () => clearInterval(id);
     }
   }, [available]);
   ```

## Example App

A complete example app is available in the `example` folder. It demonstrates:

- Permission handling for both iOS and Android
- Alarm scheduling and cancellation
- Timer functionality
- Event listeners for real-time updates
- Configuration management
- UI components for displaying alarm state

To run the example:

```bash
pnpm install
cd example
npx expo start
```

## Local Development

### Building the Library

```bash
pnpm build
```

### Running the Example App

```bash
cd example
npx expo start
```

## Publishing

Merging to `main` never publishes. To release:

1. Bump `version` in `package.json` in a PR (e.g. `npm version patch --no-git-tag-version`) and merge it
2. Push the matching tag (`git tag vX.Y.Z && git push origin vX.Y.Z`), or run the "Publish to npm" workflow from the Actions tab
3. The workflow verifies the tag matches `package.json` and the version is not already on npm, publishes via OIDC trusted publishing, and creates the GitHub Release (and tag, when run manually)

## Branch Strategy

See [BRANCH_STRATEGY.md](.github/BRANCH_STRATEGY.md) for branch workflow and release process.

## Links

- Expo Modules API: [Get started](https://docs.expo.dev/modules/get-started/)
- Expo Modules API: [Native module tutorial](https://docs.expo.dev/modules/native-module-tutorial/)
- iOS AlarmKit Documentation: [Apple Developer](https://developer.apple.com/documentation/alarmkit)

## License

MIT

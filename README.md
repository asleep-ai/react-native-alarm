# @asleep-ai/react-native-alarm

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
import {
  isAvailable,
  requestPermission,
  scheduleAlarm,
  getAlarms,
  cancelAlarm,
} from '@asleep-ai/react-native-alarm';

async function setupAlarm() {
  // 1. Check availability (iOS 26+)
  if (!isAvailable()) {
    console.warn('AlarmKit not available on this device');
    return;
  }

  // 2. Request permission
  const granted = await requestPermission();
  if (!granted) {
    console.error('Permission denied');
    return;
  }

  // 3. Schedule an alarm
  const alarm = await scheduleAlarm({
    dateISO: new Date(Date.now() + 60_000).toISOString(), // 1 minute from now
    label: 'Wake up',
  });

  console.log('Scheduled alarm:', alarm);

  // 4. List all alarms
  const alarms = await getAlarms();
  console.log('All alarms:', alarms);

  // 5. Cancel an alarm
  await cancelAlarm(alarm.id);
}
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

#### `requestPermission(): Promise<boolean>`

Requests alarm permission from the user. On iOS, this requests AlarmKit authorization. On Android, this requests notification permission.

**Returns:** `Promise<boolean>` - `true` if permission was granted, `false` otherwise.

```typescript
const granted = await requestPermission();
if (!granted) {
  // Handle permission denial
}
```

#### `canScheduleExactAlarms(): boolean` (Android only)

Checks if the app can schedule exact alarms on Android. Exact alarms are required for precise timing.

```typescript
if (Platform.OS === 'android') {
  const canSchedule = canScheduleExactAlarms();
  if (!canSchedule) {
    // Guide user to enable exact alarms in settings
  }
}
```

#### `hasOverlayPermission(): boolean` (Android only)

Checks if the app has permission to display overlay windows (for alarm UI).

```typescript
if (Platform.OS === 'android') {
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
  dateISO: new Date('2024-12-25T08:00:00Z').toISOString(),
  label: 'Christmas Morning',
});
```

**Example 2: Start a countdown timer**

```typescript
const timer = await scheduleAlarm({
  label: 'Pomodoro Timer',
  countdownSeconds: 1500, // 25 minutes
});
```

**Example 3: Schedule alarm with countdown**

```typescript
// This creates a countdown that starts immediately and fires an alert at the scheduled time
const alarm = await scheduleAlarm({
  dateISO: new Date(Date.now() + 3600_000).toISOString(), // 1 hour from now
  label: 'Meeting Reminder',
  countdownSeconds: 3600, // Countdown for 1 hour
});
```

**Example 4: With Android customization**

```typescript
const alarm = await scheduleAlarm({
  dateISO: new Date(Date.now() + 60_000).toISOString(),
  label: 'Wake Up',
  android: {
    smallIconName: 'ic_stat_alarm',
    accentColor: '#4CAF50',
    useChronometer: true,
    showOverlayWhenUnlocked: true,
    overlayBackgroundColor: '#000000',
    overlayTextColor: '#FFFFFF',
    overlayButtonBackgroundColor: '#3b82f6',
    overlayButtonTextColor: '#FFFFFF',
    snoozeMinutes: 5,
  },
});
```

**Example 5: With iOS customization**

```typescript
const alarm = await scheduleAlarm({
  dateISO: new Date(Date.now() + 60_000).toISOString(),
  label: 'Wake Up',
  ios: {
    tintColorHex: '#007AFF',
    alertStopText: 'Done',
    countdownPauseText: 'Pause',
    pausedResumeText: 'Start',
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
};
```

**Example:**

```typescript
const alarms = await getAlarms();
alarms.forEach(alarm => {
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
};
```

**Example:**

```typescript
await configure({
  android: {
    accentColor: '#4CAF50',
    smallIconName: 'ic_stat_alarm',
    useChronometer: true,
    showOverlayWhenUnlocked: true,
    overlayBackgroundColor: '#000000',
    overlayTextColor: '#FFFFFF',
    overlayButtonBackgroundColor: '#3b82f6',
    overlayButtonTextColor: '#FFFFFF',
    snoozeMinutes: 5,
  },
  ios: {
    tintColorHex: '#007AFF',
    alertStopText: 'Done',
    countdownPauseText: 'Pause',
    pausedResumeText: 'Start',
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

## Complete Example

Here's a complete example demonstrating common use cases:

```typescript
import React, { useEffect, useState } from 'react';
import { View, Button, Alert } from 'react-native';
import {
  isAvailable,
  requestPermission,
  scheduleAlarm,
  getAlarms,
  cancelAlarm,
  configure,
  type Alarm,
} from '@asleep-ai/react-native-alarm';
import { Platform } from 'react-native';

export default function AlarmScreen() {
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [permissionGranted, setPermissionGranted] = useState(false);

  useEffect(() => {
    checkAvailability();
    loadAlarms();
  }, []);

  const checkAvailability = async () => {
    if (!isAvailable()) {
      Alert.alert('Not Available', 'AlarmKit requires iOS 26+');
      return;
    }

    const granted = await requestPermission();
    setPermissionGranted(granted);
    
    if (!granted) {
      Alert.alert('Permission Required', 'Please grant alarm permission');
    }
  };

  const loadAlarms = async () => {
    try {
      const list = await getAlarms();
      setAlarms(list);
    } catch (error) {
      console.error('Failed to load alarms:', error);
    }
  };

  const scheduleWakeUpAlarm = async () => {
    if (!permissionGranted) {
      Alert.alert('Permission Required', 'Please grant alarm permission first');
      return;
    }

    try {
      // Schedule alarm for 8:00 AM tomorrow
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      tomorrow.setHours(8, 0, 0, 0);

      const alarm = await scheduleAlarm({
        dateISO: tomorrow.toISOString(),
        label: 'Wake Up',
        android: {
          accentColor: '#4CAF50',
          smallIconName: 'ic_stat_alarm',
        },
        ios: {
          tintColorHex: '#007AFF',
        },
      });

      Alert.alert('Success', `Alarm scheduled: ${alarm.label}`);
      await loadAlarms();
    } catch (error) {
      Alert.alert('Error', `Failed to schedule alarm: ${error}`);
    }
  };

  const startTimer = async (minutes: number) => {
    if (!permissionGranted) {
      Alert.alert('Permission Required', 'Please grant alarm permission first');
      return;
    }

    try {
      const alarm = await scheduleAlarm({
        label: `${minutes} Minute Timer`,
        countdownSeconds: minutes * 60,
      });

      Alert.alert('Timer Started', `${minutes} minute timer started`);
      await loadAlarms();
    } catch (error) {
      Alert.alert('Error', `Failed to start timer: ${error}`);
    }
  };

  const cancelAlarmById = async (id: string) => {
    try {
      await cancelAlarm(id);
      Alert.alert('Cancelled', 'Alarm cancelled');
      await loadAlarms();
    } catch (error) {
      Alert.alert('Error', `Failed to cancel alarm: ${error}`);
    }
  };

  return (
    <View style={{ padding: 20 }}>
      <Button title="Request Permission" onPress={checkAvailability} />
      <Button title="Schedule Wake Up Alarm" onPress={scheduleWakeUpAlarm} />
      <Button title="Start 5 Min Timer" onPress={() => startTimer(5)} />
      <Button title="Start 25 Min Timer" onPress={() => startTimer(25)} />
      <Button title="Refresh Alarms" onPress={loadAlarms} />

      <View style={{ marginTop: 20 }}>
        <Text style={{ fontSize: 18, fontWeight: 'bold' }}>Scheduled Alarms:</Text>
        {alarms.map(alarm => (
          <View key={alarm.id} style={{ marginVertical: 10 }}>
            <Text>{alarm.label || 'Unnamed Alarm'}</Text>
            <Text>{new Date(alarm.dateISO).toLocaleString()}</Text>
            <Button
              title="Cancel"
              onPress={() => cancelAlarmById(alarm.id)}
            />
          </View>
        ))}
      </View>
    </View>
  );
}
```

## Android Customization

### Adding Custom Icons

To use custom notification icons on Android:

1. Add your icon drawable files to `android/app/src/main/res/drawable/`
2. Reference them by name in your configuration:

```typescript
await configure({
  android: {
    smallIconName: 'ic_stat_alarm', // Your drawable name without extension
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

**Alarms not firing**
- Verify the device is not in Do Not Disturb mode
- Check that alarms are enabled in system settings

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
       android: { accentColor: '#4CAF50' },
       ios: { tintColorHex: '#007AFF' },
     });
   }, []);
   ```

## Local Development

### Building the Library

```bash
npm run build
```

### Running the Example App

```bash
cd example
npx expo start
```

## Publishing

- Manual publish: `npm publish --access public`
- Version bump: `npm run version:patch|minor|major`
- Automated: Push a tag `v*.*.*` or use GitHub Actions workflow_dispatch

## Branch Strategy

See [BRANCH_STRATEGY.md](.github/BRANCH_STRATEGY.md) for branch workflow and release process.

## Links

- Expo Modules API: [Get started](https://docs.expo.dev/modules/get-started/)
- Expo Modules API: [Native module tutorial](https://docs.expo.dev/modules/native-module-tutorial/)
- iOS AlarmKit Documentation: [Apple Developer](https://developer.apple.com/documentation/alarmkit)

## License

MIT

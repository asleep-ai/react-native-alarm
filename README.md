# @asleep-ai/react-native-alarm

React Native alarm utilities powered by iOS AlarmKit (iOS 26+), built with Expo Modules API.

Important
- iOS: AlarmKit requires iOS 26 or later. This library weak-links AlarmKit and provides runtime availability checks.
- Android: API is stubbed (no-op) for now.

Installation (in your app)
- Using yarn: `yarn add @asleep-ai/react-native-alarm`
- iOS pods: `npx pod-install`

Usage
```ts
import {
  isAvailable,
  scheduleAlarm,
  getAlarms,
  cancelAlarm,
  requestPermission,
} from '@asleep-ai/react-native-alarm';

async function demo() {
  if (!isAvailable()) {
    console.warn('AlarmKit not available on this device');
    return;
  }
  await requestPermission();
  const alarm = await scheduleAlarm({
    dateISO: new Date(Date.now() + 60_000).toISOString(),
    label: 'Wake up',
  });
  console.log('Scheduled alarm:', alarm);
  const alarms = await getAlarms();
  console.log('All alarms:', alarms);
  // await cancelAlarm(alarm.id);
}
```

Local development (library)
- Build TypeScript: `npm run build`
- Example app: `cd example && npx expo start`

Links
- Expo Modules API: Get started — https://docs.expo.dev/modules/get-started/
- Expo Modules API: Native module tutorial — https://docs.expo.dev/modules/native-module-tutorial/
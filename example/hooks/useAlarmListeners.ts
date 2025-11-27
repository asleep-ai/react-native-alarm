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

export function useAlarmListeners({
  onStateChanged,
  onStopped,
}: UseAlarmListenersProps) {
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


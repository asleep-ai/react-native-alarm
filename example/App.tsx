import React, { useState, useEffect } from "react";
import { StyleSheet } from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import { BottomTabBar } from "./components/BottomTabBar";
import { ActionsScreen } from "./screens/ActionsScreen";
import { SettingsScreen } from "./screens/SettingsScreen";
import { useAlarmState } from "./hooks/useAlarmState";
import { useAlarmListeners } from "./hooks/useAlarmListeners";
import { useAlarmActions } from "./hooks/useAlarmActions";
import { useAlarmConfig } from "./hooks/useAlarmConfig";
import { usePermissions } from "./hooks/usePermissions";

type Tab = "actions" | "settings";

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>("actions");
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
      // Refresh alarms list when alarm is stopped
      refreshAlarmsList();
    },
  });

  const { config, setters, onApplyConfig } = useAlarmConfig();

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

  const renderTabContent = () => {
    switch (activeTab) {
      case "actions":
        return (
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
        );
      case "settings":
        return (
          <SettingsScreen
            available={available}
            config={config}
            setters={setters}
            onApplyConfig={onApplyConfig}
          />
        );
      default:
        return null;
    }
  };

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.container} edges={["top"]}>
        {renderTabContent()}
        <BottomTabBar activeTab={activeTab} onTabChange={setActiveTab} />
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#eee",
  },
});

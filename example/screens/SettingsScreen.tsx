import React from "react";
import { ScrollView, Text, View, TextInput, Switch, Button, StyleSheet, Platform } from "react-native";
import { Group } from "../components/Group";
import { ColorSwatch } from "../components/ColorSwatch";
import { IconChip } from "../components/IconChip";
import type { AlarmConfig } from "../hooks/useAlarmConfig";

interface SettingsScreenProps {
  available: boolean;
  config: AlarmConfig;
  setters: {
    setAndroidSmallIcon: (value: string) => void;
    setAndroidAccentColor: (value: string) => void;
    setAndroidUseChrono: (value: boolean) => void;
    setAndroidOverlayUnlocked: (value: boolean) => void;
    setAndroidOverlayBg: (value: string) => void;
    setAndroidOverlayText: (value: string) => void;
    setAndroidOverlayBtnBg: (value: string) => void;
    setAndroidOverlayBtnText: (value: string) => void;
    setIosTint: (value: string) => void;
    setIosStopText: (value: string) => void;
    setIosPauseText: (value: string) => void;
    setIosResumeText: (value: string) => void;
  };
  onApplyConfig: () => void;
}

const iconOptions = ["", "ic_stat_alarm", "ic_stat_timer", "ic_alarm_small"];

export function SettingsScreen({ available, config, setters, onApplyConfig }: SettingsScreenProps) {
  const availabilityText = available ? "Available (iOS 26+)" : "Not available on this device";

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.header}>Settings</Text>

      <Group name="Availability">
        <Text>{availabilityText}</Text>
        {!available ? (
          <Text style={styles.infoText}>AlarmKit requires iOS 26+. On Android this example is a no-op.</Text>
        ) : null}
      </Group>

      <Group name="Customization (Global)">
        <View style={styles.configContainer}>
          {Platform.OS === "android" ? (
            <>
              <Text style={styles.sectionTitle}>Android</Text>
              <Text>Small icon drawable name (optional)</Text>
              <TextInput
                value={config.androidSmallIcon}
                onChangeText={setters.setAndroidSmallIcon}
                placeholder="e.g. ic_stat_alarm"
                style={styles.input}
              />
              <Text>Accent color (hex)</Text>
              <View style={styles.colorSwatches}>
                {["#4CAF50", "#2196F3", "#E91E63", "#FF9800"].map((c) => (
                  <ColorSwatch
                    key={c}
                    color={c}
                    selected={config.androidAccentColor === c}
                    onPress={() => setters.setAndroidAccentColor(c)}
                  />
                ))}
              </View>
              <TextInput
                value={config.androidAccentColor}
                onChangeText={setters.setAndroidAccentColor}
                placeholder="#RRGGBB"
                style={styles.input}
              />
              <Text>Small icon</Text>
              <View style={styles.iconChips}>
                {iconOptions.map((n) => (
                  <IconChip
                    key={n || "(default)"}
                    name={n || "(default)"}
                    selected={(n || "") === (config.androidSmallIcon || "")}
                    onPress={() => setters.setAndroidSmallIcon(n)}
                  />
                ))}
              </View>
              <View style={styles.switchRow}>
                <Text>Use chronometer</Text>
                <Switch value={config.androidUseChrono} onValueChange={setters.setAndroidUseChrono} />
              </View>
              <View style={styles.switchRow}>
                <Text>Overlay when unlocked</Text>
                <Switch value={config.androidOverlayUnlocked} onValueChange={setters.setAndroidOverlayUnlocked} />
              </View>
              <Text>Overlay background</Text>
              <View style={styles.colorSwatches}>
                {["#000000", "#222222", "#880000", "#004488"].map((c) => (
                  <ColorSwatch
                    key={c}
                    color={c}
                    selected={config.androidOverlayBg === c}
                    onPress={() => setters.setAndroidOverlayBg(c)}
                  />
                ))}
              </View>
              <Text>Overlay text</Text>
              <View style={styles.colorSwatches}>
                {["#FFFFFF", "#FFD700", "#00E5FF", "#FFCDD2"].map((c) => (
                  <ColorSwatch
                    key={c}
                    color={c}
                    selected={config.androidOverlayText === c}
                    onPress={() => setters.setAndroidOverlayText(c)}
                  />
                ))}
              </View>
              <Text>Overlay button background</Text>
              <View style={styles.colorSwatches}>
                {["#3b82f6", "#10b981", "#ef4444", "#6b7280"].map((c) => (
                  <ColorSwatch
                    key={c}
                    color={c}
                    selected={config.androidOverlayBtnBg === c}
                    onPress={() => setters.setAndroidOverlayBtnBg(c)}
                  />
                ))}
              </View>
              <Text>Overlay button text</Text>
              <View style={styles.colorSwatches}>
                {["#FFFFFF", "#111111", "#FFD700", "#00E5FF"].map((c) => (
                  <ColorSwatch
                    key={c}
                    color={c}
                    selected={config.androidOverlayBtnText === c}
                    onPress={() => setters.setAndroidOverlayBtnText(c)}
                  />
                ))}
              </View>
            </>
          ) : (
            <>
              <Text style={styles.sectionTitle}>iOS</Text>
              <Text>tintColor (hex)</Text>
              <View style={styles.colorSwatches}>
                {["#007AFF", "#4A90E2", "#E91E63", "#4CAF50"].map((c) => (
                  <ColorSwatch
                    key={c}
                    color={c}
                    selected={config.iosTint === c}
                    onPress={() => setters.setIosTint(c)}
                  />
                ))}
              </View>
              <TextInput
                value={config.iosTint}
                onChangeText={setters.setIosTint}
                placeholder="#RRGGBB"
                style={styles.input}
              />
              <Text>Stop button text</Text>
              <TextInput value={config.iosStopText} onChangeText={setters.setIosStopText} style={styles.input} />
              <Text>Pause button text</Text>
              <TextInput value={config.iosPauseText} onChangeText={setters.setIosPauseText} style={styles.input} />
              <Text>Resume button text</Text>
              <TextInput value={config.iosResumeText} onChangeText={setters.setIosResumeText} style={styles.input} />
            </>
          )}
          <Button title="Apply Config" onPress={onApplyConfig} />
        </View>
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
  infoText: {
    marginTop: 8,
  },
  configContainer: {
    gap: 12,
  },
  sectionTitle: {
    fontWeight: "600",
  },
  input: {
    backgroundColor: "#fff",
    padding: 8,
    borderRadius: 6,
  },
  colorSwatches: {
    flexDirection: "row",
    gap: 12,
  },
  iconChips: {
    flexDirection: "row",
    flexWrap: "wrap",
    marginVertical: 4,
  },
  switchRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
});

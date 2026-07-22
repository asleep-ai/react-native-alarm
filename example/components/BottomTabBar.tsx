import React from "react";
import { View, Text, TouchableOpacity, StyleSheet, Platform } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

type Tab = "actions" | "settings";

interface BottomTabBarProps {
  activeTab: Tab;
  onTabChange: (tab: Tab) => void;
}

export function BottomTabBar({ activeTab, onTabChange }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  const bottomPadding = Math.max(insets.bottom, Platform.OS === "ios" ? 20 : 10);

  return (
    <View style={[styles.container, { paddingBottom: bottomPadding }]}>
      <TouchableOpacity
        style={[styles.tabItem, activeTab === "actions" && styles.tabItemActive]}
        onPress={() => onTabChange("actions")}
      >
        <Text style={[styles.tabText, activeTab === "actions" && styles.tabTextActive]}>Actions</Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={[styles.tabItem, activeTab === "settings" && styles.tabItemActive]}
        onPress={() => onTabChange("settings")}
      >
        <Text style={[styles.tabText, activeTab === "settings" && styles.tabTextActive]}>Settings</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    flexDirection: "row",
    backgroundColor: "#fff",
    borderTopWidth: 1,
    borderTopColor: "#e5e7eb",
    paddingBottom: 0, // Set dynamically in component
    paddingTop: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 5,
  },
  tabItem: {
    flex: 1,
    alignItems: "center",
    paddingVertical: 8,
  },
  tabItemActive: {
    borderTopWidth: 2,
    borderTopColor: "#3b82f6",
  },
  tabText: {
    fontSize: 12,
    color: "#6b7280",
    fontWeight: "500",
  },
  tabTextActive: {
    color: "#3b82f6",
    fontWeight: "600",
  },
});

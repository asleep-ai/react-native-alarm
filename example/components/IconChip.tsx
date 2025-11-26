import React from "react";
import { TouchableOpacity, Text, StyleSheet } from "react-native";

interface IconChipProps {
  name: string;
  selected: boolean;
  onPress: () => void;
}

export function IconChip({ name, selected, onPress }: IconChipProps) {
  return (
    <TouchableOpacity
      onPress={onPress}
      style={[styles.chip, selected && styles.chipSelected]}
    >
      <Text style={[styles.chipText, selected && styles.chipTextSelected]}>
        {name}
      </Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  chip: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 14,
    backgroundColor: "#e5e7eb",
    marginRight: 8,
  },
  chipSelected: {
    backgroundColor: "#3b82f6",
  },
  chipText: {
    color: "#111",
  },
  chipTextSelected: {
    color: "#fff",
  },
});

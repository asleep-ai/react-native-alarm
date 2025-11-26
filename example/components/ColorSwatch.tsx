import React from "react";
import { TouchableOpacity, StyleSheet } from "react-native";

interface ColorSwatchProps {
  color: string;
  onPress: () => void;
  selected?: boolean;
}

export function ColorSwatch({ color, onPress, selected }: ColorSwatchProps) {
  return (
    <TouchableOpacity
      onPress={onPress}
      style={[
        styles.swatch,
        { backgroundColor: color },
        selected && styles.selected,
      ]}
    />
  );
}

const styles = StyleSheet.create({
  swatch: {
    width: 36,
    height: 36,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: "#999",
  },
  selected: {
    borderWidth: 3,
    borderColor: "#000",
  },
});

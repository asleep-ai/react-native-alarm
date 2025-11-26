import React from "react";
import { View, Text, StyleSheet } from "react-native";

interface GroupProps {
  name: string;
  children: React.ReactNode;
}

export function Group({ name, children }: GroupProps) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{name}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  group: {
    margin: 20,
    backgroundColor: "#fff",
    borderRadius: 10,
    padding: 20,
  },
  groupHeader: {
    fontSize: 20,
    marginBottom: 20,
  },
});

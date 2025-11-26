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
  const [androidAccentColor, setAndroidAccentColor] =
    useState<string>("#4CAF50");
  const [androidUseChrono, setAndroidUseChrono] = useState<boolean>(true);
  const [androidOverlayUnlocked, setAndroidOverlayUnlocked] =
    useState<boolean>(true);
  const [androidOverlayBg, setAndroidOverlayBg] = useState<string>("#000000");
  const [androidOverlayText, setAndroidOverlayText] =
    useState<string>("#FFFFFF");
  const [androidOverlayBtnBg, setAndroidOverlayBtnBg] =
    useState<string>("#3b82f6");
  const [androidOverlayBtnText, setAndroidOverlayBtnText] =
    useState<string>("#FFFFFF");
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

import ExpoModulesCore
import SwiftUI
import UIKit
#if canImport(AlarmKit)
import AlarmKit
#endif

public class ReactNativeAlarmModule: Module {
  private let storageKey = "ReactNativeAlarm.scheduled"
  private let isoFormatter: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f
  }()
  // Global style config (iOS)
  private var iosTintHex: String? = nil
  private var iosAlertStopText: String? = nil
  private var iosCountdownPauseText: String? = nil
  private var iosPausedResumeText: String? = nil
  // iOS 26+ only. AlarmKit usage only; no UserNotifications fallback.
  #if canImport(AlarmKit)
  @available(iOS 26.0, *)
  struct RNMetadata: AlarmMetadata, Codable {}
  @available(iOS 26.0, *)
  private func isoForAlarm(_ alarm: Alarm) -> String {
    // Prefer explicit schedule date when available; otherwise derive from countdown.
    if let schedule = alarm.schedule {
      switch schedule {
      case .fixed(let date):
        return isoFormatter.string(from: date)
      case .relative(let rel):
        var comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: Date())
        comps.hour = rel.time.hour
        comps.minute = rel.time.minute
        if let d = Calendar.current.date(from: comps) {
          return isoFormatter.string(from: d)
        }
      @unknown default:
        break
      }
    }
    if let pre = alarm.countdownDuration?.preAlert {
      return isoFormatter.string(from: Date().addingTimeInterval(pre))
    }
    return ""
  }
  #endif
  #if canImport(AlarmKit)
  @available(iOS 26.0, *)
  struct RNAlarmMetadata: AlarmMetadata, Codable {}
  #endif

  private func scheduleLocalNotification(label: String, targetDate: Date?, countdownSeconds: Double?) -> [String: Any] {
    // Removed: No UserNotifications fallback in iOS 26+ only build.
    return [:]
  }

  private func loadScheduled() -> [[String: Any]] {
    let defaults = UserDefaults.standard
    return defaults.array(forKey: storageKey) as? [[String: Any]] ?? []
  }

  private func saveScheduled(_ items: [[String: Any]]) {
    let defaults = UserDefaults.standard
    defaults.set(items, forKey: storageKey)
  }

  public func definition() -> ModuleDefinition {
    Name("ReactNativeAlarm")

    // No UserNotifications integration in iOS 26+ only build.

    Function("isAlarmKitAvailable") {
      if #available(iOS 26.0, *) { return true }
      return false
    }

    AsyncFunction("setConfig") { (config: [String: Any]) in
      if let ios = config["ios"] as? [String: Any] {
        self.iosTintHex = ios["tintColorHex"] as? String
        self.iosAlertStopText = ios["alertStopText"] as? String
        self.iosCountdownPauseText = ios["countdownPauseText"] as? String
        self.iosPausedResumeText = ios["pausedResumeText"] as? String
      }
    }

    AsyncFunction("requestPermission") { () async -> [String: Any] in
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        do {
          // Request authorization - this will return current status if already determined
          let state = try await AlarmManager.shared.requestAuthorization()
          let isAuthorized = state == .authorized
          var statusString = "unknown"
          switch state {
          case .notDetermined:
            statusString = "notDetermined"
          case .denied:
            statusString = "denied"
          case .authorized:
            statusString = "authorized"
          @unknown default:
            statusString = "unknown"
          }
          return ["granted": isAuthorized, "status": statusString]
        } catch {
          return ["granted": false, "status": "unknown"]
        }
      }
      #endif
      return ["granted": false, "status": "notAvailable"]
    }
    
    AsyncFunction("getAuthorizationStatus") { () async -> String in
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        do {
          // Note: requestAuthorization() will show dialog only if status is .notDetermined
          // If status is already determined (.denied or .authorized), it returns immediately
          // without showing dialog. This is the only way to check status in AlarmKit.
          let state = try await AlarmManager.shared.requestAuthorization()
          switch state {
          case .notDetermined:
            return "notDetermined"
          case .denied:
            return "denied"
          case .authorized:
            return "authorized"
          @unknown default:
            return "unknown"
          }
        } catch {
          return "unknown"
        }
      }
      #endif
      return "notAvailable"
    }
    
    AsyncFunction("openSettings") {
      if let url = URL(string: UIApplication.openSettingsURLString) {
        await UIApplication.shared.open(url)
      }
    }

    AsyncFunction("scheduleAlarm") { (options: [String: Any]) -> [String: Any] in
      let label = options["label"] as? String ?? "Alarm"
      let dateISO = options["dateISO"] as? String ?? ""
      let countdownSeconds = options["countdownSeconds"] as? Double
      let iosOpts = options["ios"] as? [String: Any]
      let stopText = (iosOpts?["alertStopText"] as? String) ?? self.iosAlertStopText ?? "Done"
      let pauseText = (iosOpts?["countdownPauseText"] as? String) ?? self.iosCountdownPauseText ?? "Pause"
      let resumeText = (iosOpts?["pausedResumeText"] as? String) ?? self.iosPausedResumeText ?? "Start"
      let tintHex = (iosOpts?["tintColorHex"] as? String) ?? self.iosTintHex
      var targetDate: Date? = nil
      if let d = isoFormatter.date(from: dateISO), d.timeIntervalSinceNow > 0 {
        targetDate = d
      }
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        // Title for presentations
        let title: LocalizedStringResource = label.isEmpty ? LocalizedStringResource("Alarm") : LocalizedStringResource(stringLiteral: label)
        // Buttons and countdown/paused presentations
        let stopBtn = AlarmButton(text: LocalizedStringResource(stringLiteral: stopText), textColor: .white, systemImageName: "stop.circle")
        let pauseBtn = AlarmButton(text: LocalizedStringResource(stringLiteral: pauseText), textColor: .black, systemImageName: "pause.fill")
        let countdownUI = AlarmPresentation.Countdown(
          title: title,
          pauseButton: pauseBtn
        )
        let resumeBtn = AlarmButton(text: LocalizedStringResource(stringLiteral: resumeText), textColor: .black, systemImageName: "play.fill")
        let pausedUI = AlarmPresentation.Paused(
          title: LocalizedStringResource("Paused"),
          resumeButton: resumeBtn
        )
        // Optional one-time schedule using fixed date if provided
        var schedule: Alarm.Schedule? = nil
        if let target = targetDate {
          schedule = .fixed(target)
        }
        // Determine countdown behavior:
        // - If countdownSeconds provided, use it
        // - Else if schedule provided, default to preAlert that counts down until the scheduled time (Live Activity shows immediately)
        var effectivePreAlert: Double? = nil
        if let seconds = countdownSeconds, seconds > 0 {
          effectivePreAlert = seconds
        } else if let target = targetDate {
          let diff = target.timeIntervalSinceNow
          if diff > 0 {
            effectivePreAlert = diff
          }
        }
        // Build alert with optional Snooze (repeat) button that returns to countdown
        let repeatBtn = AlarmButton(text: LocalizedStringResource("Snooze"), textColor: .black, systemImageName: "repeat.circle")
        let alert = AlarmPresentation.Alert(
          title: title,
          stopButton: stopBtn,
          secondaryButton: (effectivePreAlert != nil ? repeatBtn : nil),
          secondaryButtonBehavior: (effectivePreAlert != nil ? .countdown : nil)
        )
        let presentation = AlarmPresentation(alert: alert, countdown: countdownUI, paused: pausedUI)
        // Attributes (after presentation is built)
        let attributes = AlarmAttributes<RNMetadata>(
          presentation: presentation,
          metadata: RNMetadata(),
          tintColor: (tintHex != nil ? ReactNativeAlarmModule.colorFromHex(tintHex!) : Color.blue)
        )
        // Configuration
        let configuration: AlarmManager.AlarmConfiguration<RNMetadata>
        if let pre = effectivePreAlert, let sch = schedule {
          configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
            countdownDuration: Alarm.CountdownDuration(preAlert: pre, postAlert: 300),
            schedule: sch,
            attributes: attributes
          )
        } else if let pre = effectivePreAlert {
          configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
            countdownDuration: Alarm.CountdownDuration(preAlert: pre, postAlert: 300),
            attributes: attributes
          )
        } else if let sch = schedule {
          configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
            schedule: sch,
            attributes: attributes
          )
        } else {
          throw NSError(domain: "ReactNativeAlarm", code: 2, userInfo: [NSLocalizedDescriptionKey: "Provide countdownSeconds or dateISO"])
        }
        let id = UUID()
        _ = try await AlarmManager.shared.schedule(id: id, configuration: configuration)
        // Persist simple mirror for JS
        let outISO: String
        if let target = targetDate {
          outISO = isoFormatter.string(from: target)
        } else if let pre = effectivePreAlert, pre > 0 {
          outISO = isoFormatter.string(from: Date().addingTimeInterval(pre))
        } else {
          outISO = ""
        }
        var items = loadScheduled()
        items.append(["id": id.uuidString, "dateISO": outISO, "label": label, "enabled": true])
        saveScheduled(items)
        return ["id": id.uuidString, "dateISO": outISO, "label": label, "enabled": true]
      }
      #endif
      throw NSError(domain: "ReactNativeAlarm", code: 3, userInfo: [NSLocalizedDescriptionKey: "AlarmKit is required (iOS 26+)"])
    }

    AsyncFunction("cancelAlarm") { (id: String) in
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *), let uuid = UUID(uuidString: id) {
        do { try AlarmManager.shared.cancel(id: uuid) } catch { /* ignore */ }
      }
      #endif
      var items = loadScheduled()
      items.removeAll { ($0["id"] as? String) == id }
      saveScheduled(items)
    }

    AsyncFunction("cancelAll") {
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        do {
          let current = try AlarmManager.shared.alarms
          for a in current {
            do { try AlarmManager.shared.cancel(id: a.id) } catch { /* ignore */ }
          }
        } catch { /* ignore */ }
      }
      #endif
      saveScheduled([])
    }

    AsyncFunction("getAlarms") { () -> [[String: Any]] in
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        do {
          let remote = try AlarmManager.shared.alarms
          return remote.map { a in
            [
              "id": a.id.uuidString,
              "dateISO": isoForAlarm(a),
              "label": "", // AlarmKit stores label in presentation; omitted here
              "enabled": a.state != .paused
            ]
          }
        } catch {
          // fall back to local mirror
        }
      }
      #endif
      return loadScheduled()
    }
  }

  static func colorFromHex(_ hex: String) -> Color {
    var str = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    if str.hasPrefix("#") { str.removeFirst() }
    var rgb: UInt64 = 0
    Scanner(string: str).scanHexInt64(&rgb)
    let r, g, b: Double
    if str.count == 6 {
      r = Double((rgb & 0xFF0000) >> 16) / 255.0
      g = Double((rgb & 0x00FF00) >> 8) / 255.0
      b = Double(rgb & 0x0000FF) / 255.0
      return Color(red: r, green: g, blue: b)
    }
    return Color.blue
  }
}

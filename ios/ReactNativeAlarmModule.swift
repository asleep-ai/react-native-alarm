import ExpoModulesCore
import SwiftUI
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

    AsyncFunction("requestPermission") { () async -> Bool in
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        do {
          let state = try await AlarmManager.shared.requestAuthorization()
          return state == .authorized
        } catch {
          return false
        }
      }
      #endif
      return false
    }

    AsyncFunction("scheduleAlarm") { (options: [String: Any]) -> [String: Any] in
      let label = options["label"] as? String ?? "Alarm"
      let dateISO = options["dateISO"] as? String ?? ""
      let countdownSeconds = options["countdownSeconds"] as? Double
      var targetDate: Date? = nil
      if let d = isoFormatter.date(from: dateISO), d.timeIntervalSinceNow > 0 {
        targetDate = d
      }
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        // Title for presentations
        let title: LocalizedStringResource = label.isEmpty ? LocalizedStringResource("Alarm") : LocalizedStringResource(stringLiteral: label)
        // Basic alert presentation with default stop button
        let stopBtn = AlarmButton(text: "Done", textColor: .white, systemImageName: "stop.circle")
        let alert = AlarmPresentation.Alert(
          title: title,
          stopButton: stopBtn,
          secondaryButton: nil,
          secondaryButtonBehavior: nil
        )
        // Provide default countdown and paused presentations so a Live Activity can appear
        let pauseBtn = AlarmButton(text: "Pause", textColor: .black, systemImageName: "pause.fill")
        let countdownUI = AlarmPresentation.Countdown(
          title: title,
          pauseButton: pauseBtn
        )
        let resumeBtn = AlarmButton(text: "Start", textColor: .black, systemImageName: "play.fill")
        let pausedUI = AlarmPresentation.Paused(
          title: LocalizedStringResource("Paused"),
          resumeButton: resumeBtn
        )
        let presentation = AlarmPresentation(alert: alert, countdown: countdownUI, paused: pausedUI)
        // Attributes
        let attributes = AlarmAttributes<RNMetadata>(
          presentation: presentation,
          metadata: RNMetadata(),
          tintColor: Color.blue
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
        // Configuration
        let configuration: AlarmManager.AlarmConfiguration<RNMetadata>
        if let pre = effectivePreAlert, let sch = schedule {
          configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
            countdownDuration: Alarm.CountdownDuration(preAlert: pre, postAlert: nil),
            schedule: sch,
            attributes: attributes
          )
        } else if let pre = effectivePreAlert {
          configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
            countdownDuration: Alarm.CountdownDuration(preAlert: pre, postAlert: nil),
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
}

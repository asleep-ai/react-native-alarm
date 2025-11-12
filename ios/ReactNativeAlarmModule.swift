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
        // Alert presentation with default stop button
        let stopBtn = AlarmButton(text: "Done", textColor: .white, systemImageName: "stop.circle")
        let alert = AlarmPresentation.Alert(
          title: title,
          stopButton: stopBtn,
          secondaryButton: nil,
          secondaryButtonBehavior: nil
        )
        // Optional countdown/paused presentations
        let presentation: AlarmPresentation
        if let seconds = countdownSeconds, seconds > 0 {
          let pauseBtn = AlarmButton(text: "Pause", textColor: .black, systemImageName: "pause.fill")
          let countdown = AlarmPresentation.Countdown(
            title: title,
            pauseButton: pauseBtn
          )
          let resumeBtn = AlarmButton(text: "Start", textColor: .black, systemImageName: "play.fill")
          let paused = AlarmPresentation.Paused(
            title: LocalizedStringResource("Paused"),
            resumeButton: resumeBtn
          )
          presentation = AlarmPresentation(alert: alert, countdown: countdown, paused: paused)
        } else {
          presentation = AlarmPresentation(alert: alert)
        }
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
        // Configuration
        let configuration: AlarmManager.AlarmConfiguration<RNMetadata>
        if let seconds = countdownSeconds, seconds > 0, let sch = schedule {
          configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
            countdownDuration: Alarm.CountdownDuration(preAlert: seconds, postAlert: nil),
            schedule: sch,
            attributes: attributes
          )
        } else if let seconds = countdownSeconds, seconds > 0 {
          configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
            countdownDuration: Alarm.CountdownDuration(preAlert: seconds, postAlert: nil),
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
        } else if let seconds = countdownSeconds, seconds > 0 {
          outISO = isoFormatter.string(from: Date().addingTimeInterval(seconds))
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

import ExpoModulesCore
import SwiftUI
import UIKit
#if canImport(AlarmKit)
import AlarmKit

@available(iOS 26.0, *)
fileprivate struct RNMetadata: AlarmMetadata, Codable {}
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
  private var iosSnoozeMinutes: Int = 5  // default 5 minutes
  #if canImport(AlarmKit)
  private var cancelAlarmUpdatesObserver: (() -> Void)?
  #endif


  deinit {
    #if canImport(AlarmKit)
    cancelAlarmUpdatesObserver?()
    #endif
  }
  // iOS 26+ only. AlarmKit usage only; no UserNotifications fallback.
  #if canImport(AlarmKit)
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


  private func loadScheduled() -> [[String: Any]] {
    let defaults = UserDefaults.standard
    return defaults.array(forKey: storageKey) as? [[String: Any]] ?? []
  }

  private func saveScheduled(_ items: [[String: Any]]) {
    let defaults = UserDefaults.standard
    defaults.set(items, forKey: storageKey)
  }

  private func debugLog(_ message: String) {
    #if DEBUG
    print("🔔 [ReactNativeAlarm] \(message)")
    #endif
  }

  public func definition() -> ModuleDefinition {
    Name("ReactNativeAlarm")

    Events("onAlarmFired", "onAlarmStarted", "onAlarmSnoozed", "onAlarmStopped", "onAlarmStateChanged")

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
        if let snoozeMinutes = ios["snoozeMinutes"] as? Int, snoozeMinutes > 0 {
          self.iosSnoozeMinutes = snoozeMinutes
        }
      }
    }

    AsyncFunction("requestPermission") { () async -> [String: Any] in
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        do {
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
      let snoozeMinutes = (iosOpts?["snoozeMinutes"] as? Int) ?? self.iosSnoozeMinutes
      var targetDate: Date? = nil
      if let d = isoFormatter.date(from: dateISO), d.timeIntervalSinceNow > 0 {
        targetDate = d
      }
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        self.startAlarmUpdatesObserver()
        // Ensure title is always set for Live Activity/Dynamic Island display
        // Use stringLiteral to ensure text is properly displayed
        let titleText = label.isEmpty ? "Alarm" : label
        let title: LocalizedStringResource = LocalizedStringResource(stringLiteral: titleText)
        let stopBtn = AlarmButton(text: LocalizedStringResource(stringLiteral: stopText), textColor: .white, systemImageName: "stop.circle")
        let pauseBtn = AlarmButton(text: LocalizedStringResource(stringLiteral: pauseText), textColor: .black, systemImageName: "pause.fill")
        // AlarmPresentation.Countdown requires title for Live Activity/Dynamic Island display
        // The title will be shown in the widget even without custom widget extension
        let countdownUI = AlarmPresentation.Countdown(title: title, pauseButton: pauseBtn)
        let resumeBtn = AlarmButton(text: LocalizedStringResource(stringLiteral: resumeText), textColor: .black, systemImageName: "play.fill")
        let pausedTitle: LocalizedStringResource = LocalizedStringResource(stringLiteral: "Paused")
        let pausedUI = AlarmPresentation.Paused(title: pausedTitle, resumeButton: resumeBtn)
        
        var schedule: Alarm.Schedule? = nil
        if let target = targetDate {
          schedule = .fixed(target)
        }
        
        // Calculate preAlert duration for Live Activity display
        // According to AlarmKit docs: preAlert is the duration before alarm fires when countdown starts
        // To show Live Activity 1 minute before alarm fires, set preAlert to at least 60 seconds
        var preAlertDuration: Double? = nil
        if let seconds = countdownSeconds, seconds > 0 {
          // Use provided countdownSeconds
          preAlertDuration = seconds
        } else if let target = targetDate {
          // For alarms with dateISO, calculate time until alarm fires
          let timeUntilAlarm = target.timeIntervalSinceNow
          if timeUntilAlarm > 0 {
            // If alarm is more than 60 seconds away, show countdown starting 60 seconds before
            // If alarm is less than 60 seconds away, show countdown immediately
            preAlertDuration = timeUntilAlarm >= 60 ? 60 : timeUntilAlarm
          }
        }
        
        // Always provide countdown UI when we have a schedule or preAlert
        // This ensures Live Activity and Dynamic Island show real-time countdown
        let hasCountdown = preAlertDuration != nil || schedule != nil
        
        let repeatBtn = AlarmButton(text: LocalizedStringResource("Snooze"), textColor: .black, systemImageName: "repeat.circle")
        // When secondaryButton is provided, secondaryButtonBehavior must also be provided
        // Use .countdown but AlarmKit will use default snooze duration (5 minutes) for the snooze action
        // The .countdown behavior here refers to showing countdown UI after snooze, not the snooze duration itself
        // Note: AlarmKit's AlarmPresentation.Alert does not support subtitle parameter
        // The app name shown below the title is automatically set by the system
        let alert = AlarmPresentation.Alert(
          title: title,
          stopButton: stopBtn,
          secondaryButton: (hasCountdown ? repeatBtn : nil),
          secondaryButtonBehavior: (hasCountdown ? .countdown : nil)
        )
        let presentation = AlarmPresentation(alert: alert, countdown: countdownUI, paused: pausedUI)
        let attributes = AlarmAttributes<RNMetadata>(
          presentation: presentation,
          metadata: RNMetadata(),
          tintColor: (tintHex != nil ? ReactNativeAlarmModule.colorFromHex(tintHex!) : Color.blue)
        )
        let configuration: AlarmManager.AlarmConfiguration<RNMetadata>
        if let sch = schedule {
          // When schedule is provided, always include countdownDuration to show Live Activity
          // According to AlarmKit docs, countdownDuration with schedule enables Live Activity display
          if let preAlert = preAlertDuration {
            configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
              countdownDuration: Alarm.CountdownDuration(preAlert: preAlert, postAlert: 300),
              schedule: sch,
              attributes: attributes
            )
          } else if let target = targetDate {
            // Calculate time until alarm fires and use minimum 60 seconds for preAlert
            let timeUntilAlarm = target.timeIntervalSinceNow
            let preAlert = timeUntilAlarm >= 60 ? 60 : max(timeUntilAlarm, 1)
            configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
              countdownDuration: Alarm.CountdownDuration(preAlert: preAlert, postAlert: 300),
              schedule: sch,
              attributes: attributes
            )
          } else {
            // Fallback: use 60 seconds
            configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
              countdownDuration: Alarm.CountdownDuration(preAlert: 60, postAlert: 300),
              schedule: sch,
              attributes: attributes
            )
          }
        } else if let preAlert = preAlertDuration {
          // Timer mode: only countdownDuration, no schedule
          configuration = AlarmManager.AlarmConfiguration<RNMetadata>(
            countdownDuration: Alarm.CountdownDuration(preAlert: preAlert, postAlert: 300),
            attributes: attributes
          )
        } else {
          throw NSError(domain: "ReactNativeAlarm", code: 2, userInfo: [NSLocalizedDescriptionKey: "Provide countdownSeconds or dateISO"])
        }
        let id = UUID()
        let scheduleTime = Date()
        _ = try await AlarmManager.shared.schedule(id: id, configuration: configuration)
        
        let outISO: String
        if let target = targetDate {
          outISO = isoFormatter.string(from: target)
        } else if let preAlert = preAlertDuration, preAlert > 0 {
          outISO = isoFormatter.string(from: Date().addingTimeInterval(preAlert))
        } else {
          outISO = ""
        }
        
        var items = loadScheduled()
        items.append([
          "id": id.uuidString,
          "dateISO": outISO,
          "label": label,
          "enabled": true,
          "scheduleTimeISO": isoFormatter.string(from: scheduleTime),
          "countdownSeconds": preAlertDuration ?? 0,
          "snoozeMinutes": snoozeMinutes
        ])
        saveScheduled(items)
        
        let remainingSeconds = Int64(preAlertDuration ?? 0)
        sendEvent("onAlarmStarted", [
          "id": id.uuidString,
          "label": label,
          "remainingSeconds": remainingSeconds
        ])
        sendEvent("onAlarmStateChanged", [
          "id": id.uuidString,
          "label": label,
          "isRinging": false,
          "isSnoozed": false,
          "remainingSeconds": remainingSeconds
        ])
        
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
      let item = items.first { ($0["id"] as? String) == id }
      items.removeAll { ($0["id"] as? String) == id }
      saveScheduled(items)
      
      let stoppedAtISO = isoFormatter.string(from: Date())
      let label = item?["label"] as? String
      sendEvent("onAlarmStopped", [
        "id": id,
        "label": label ?? "",
        "stoppedAtISO": stoppedAtISO
      ])
      sendEvent("onAlarmStateChanged", [
        "id": id,
        "label": label ?? "",
        "isRinging": false,
        "isSnoozed": false,
        "remainingSeconds": 0,
        "stoppedAtISO": stoppedAtISO
      ])
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
          self.startAlarmUpdatesObserver()
          let remote = try AlarmManager.shared.alarms
          let stateInfo = self.calculateAlarmStates(remote)
          // Check and send events, including for snoozed alarms
          self.checkAndSendAlarmStateEvents(remote)
          return remote.map { a in
            let id = a.id.uuidString
            let state = stateInfo[id]
            var result: [String: Any] = [
              "id": id,
              "dateISO": isoForAlarm(a),
              "label": "",
              "enabled": a.state != .paused
            ]
            if let state = state {
              result["isRinging"] = state.isRinging
              result["isSnoozed"] = state.isSnoozed
              result["remainingSeconds"] = state.remainingSeconds
              if let snoozeUntilISO = state.snoozeUntilISO {
                result["snoozeUntilISO"] = snoozeUntilISO
              }
            }
            return result
          }
        } catch {
          // fall back to local mirror
        }
      }
      #endif
      return loadScheduled()
    }
    AsyncFunction("checkAlarmStates") {
      #if canImport(AlarmKit)
      if #available(iOS 26.0, *) {
        do {
          self.startAlarmUpdatesObserver()
          let remote = try AlarmManager.shared.alarms
          self.checkAndSendAlarmStateEvents(remote)
        } catch {
          // ignore
        }
      }
      #endif
    }
  }

  #if canImport(AlarmKit)
  @available(iOS 26.0, *)
  private struct AlarmStateInfo {
    let isRinging: Bool
    let isSnoozed: Bool
    let remainingSeconds: Int64
    let snoozeUntilISO: String?
  }
  
  @available(iOS 26.0, *)
  private func calculateAlarmStates(_ alarms: [Alarm]) -> [String: AlarmStateInfo] {
    let stored = loadScheduled()
    var lastStates: [String: String] = [:]
    var lastIsSnoozed: [String: Bool] = [:]
    var lastIsRinging: [String: Bool] = [:]
    var lastRemaining: [String: Int64] = [:]
    var lastScheduleDates: [String: String] = [:]
    var lastSnoozeUntilISO: [String: String] = [:]
    
    if let lastStatesData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastStates"),
       let decoded = try? JSONDecoder().decode([String: String].self, from: lastStatesData) {
      lastStates = decoded
    }
    if let lastIsSnoozedData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastIsSnoozed"),
       let decoded = try? JSONDecoder().decode([String: Bool].self, from: lastIsSnoozedData) {
      lastIsSnoozed = decoded
    }
    if let lastIsRingingData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastIsRinging"),
       let decoded = try? JSONDecoder().decode([String: Bool].self, from: lastIsRingingData) {
      lastIsRinging = decoded
    }
    if let lastRemainingData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastRemaining"),
       let decoded = try? JSONDecoder().decode([String: Int64].self, from: lastRemainingData) {
      lastRemaining = decoded
    }
    if let lastScheduleDatesData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastScheduleDates"),
       let decoded = try? JSONDecoder().decode([String: String].self, from: lastScheduleDatesData) {
      lastScheduleDates = decoded
    }
    if let lastSnoozeUntilISOData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastSnoozeUntilISO"),
       let decoded = try? JSONDecoder().decode([String: String].self, from: lastSnoozeUntilISOData) {
      lastSnoozeUntilISO = decoded
    }
    
    var stateMap: [String: AlarmStateInfo] = [:]
    
    for alarm in alarms {
      let id = alarm.id.uuidString
      let currentState = alarm.state
      
      // If alarm is paused, it's stopped
      if currentState == .paused {
        stateMap[id] = AlarmStateInfo(
          isRinging: false,
          isSnoozed: false,
          remainingSeconds: 0,
          snoozeUntilISO: nil
        )
        continue
      }
      
      let remaining = self.calculateRemainingSeconds(alarm)
      
      var currentScheduleDate: Date? = nil
      if let schedule = alarm.schedule {
        switch schedule {
        case .fixed(let date):
          currentScheduleDate = date
        default:
          break
        }
      }
      let currentScheduleDateISO = currentScheduleDate.map { isoFormatter.string(from: $0) } ?? ""
      
      let wasSnoozed = lastIsSnoozed[id] ?? false
      let wasRinging = lastIsRinging[id] ?? false
      let lastRemainingValue = lastRemaining[id] ?? Int64.max
      let lastStateStr = lastStates[id]
      let lastScheduleDateISO = lastScheduleDates[id] ?? ""
      
      // Use same snooze detection logic as checkAndSendAlarmStateEvents
      var isSnoozed = false
      
      if wasSnoozed && (currentState == .countdown || currentState == .scheduled) {
        if let countdown = alarm.countdownDuration, let preAlert = countdown.preAlert, preAlert > 0 {
          if currentState != .alerting {
            isSnoozed = true
          }
        }
      }
      
      if !isSnoozed && (wasRinging || lastStateStr == "alerting") && !currentScheduleDateISO.isEmpty {
        if lastScheduleDateISO.isEmpty {
          if let currentDate = currentScheduleDate, currentDate > Date() {
            isSnoozed = true
          }
        } else if currentScheduleDateISO != lastScheduleDateISO {
          if let currentDate = currentScheduleDate, currentDate > Date() {
            if let lastDate = isoFormatter.date(from: lastScheduleDateISO), currentDate > lastDate {
              isSnoozed = true
            }
          }
        }
      }
      
      if !isSnoozed && lastStateStr == "alerting" && (currentState == .countdown || currentState == .scheduled) {
        if let countdown = alarm.countdownDuration, let preAlert = countdown.preAlert, preAlert > 0 {
          isSnoozed = true
        } else if !currentScheduleDateISO.isEmpty {
          if let currentDate = currentScheduleDate, currentDate > Date() {
            isSnoozed = true
          }
        }
      }
      
      if !isSnoozed {
        let wasRingingOrNearZero = wasRinging || lastRemainingValue <= 1
        let remainingJumpedSignificantly = wasRingingOrNearZero && remaining > 60
        let remainingJumpedModerately = wasRinging && remaining > 30 && lastRemainingValue <= 1
        let remainingIncreasedDramatically = wasRinging && remaining > lastRemainingValue + 20 && lastRemainingValue <= 1
        let anyRemainingJump = remainingJumpedSignificantly || remainingJumpedModerately || remainingIncreasedDramatically
        
        let stateChangedFromAlerting = lastStateStr == "alerting" && (currentState == .countdown || currentState == .scheduled)
        let stateChangedToScheduled = (lastStateStr == "countdown" || lastStateStr == "alerting") && currentState == .scheduled && anyRemainingJump
        if anyRemainingJump && (currentState == .countdown || currentState == .scheduled || stateChangedFromAlerting || stateChangedToScheduled) && !wasSnoozed {
          isSnoozed = true
        }
      }
      
      // Get stored alarm data for snooze minutes
      let storedItem = stored.first { ($0["id"] as? String) == id }
      let snoozeMinutes = (storedItem?["snoozeMinutes"] as? Int) ?? 5  // default 5 minutes
      
      // Always use actual alarm data - calculate from current schedule date and current time
      let now = Date()
      
      // For snoozed alarms, use the stored snoozeUntilISO if already snoozed, otherwise calculate from schedule date
      // When snooze button is pressed, AlarmKit reschedules the alarm with a new future date
      let snoozeUntilISO: String? = {
        if isSnoozed {
          // If already snoozed, use the stored snoozeUntilISO to maintain consistency
          if wasSnoozed, let lastSnoozeISO = lastSnoozeUntilISO[id], !lastSnoozeISO.isEmpty {
            return lastSnoozeISO
          }
          // First time detecting snooze - use the actual schedule date from AlarmKit
          if let scheduleDate = currentScheduleDate, scheduleDate > now {
            return isoFormatter.string(from: scheduleDate)
          } else {
            // If schedule date is not available (shouldn't happen), use custom snooze duration from stored value
            // Note: Don't use preAlert as it may be the original countdown duration (e.g., 5 seconds)
            let snoozeDuration = TimeInterval(snoozeMinutes * 60)  // Use stored snoozeMinutes
            return isoFormatter.string(from: now.addingTimeInterval(snoozeDuration))
          }
        }
        return nil
      }()
      
      // Calculate remaining seconds from actual schedule date and current time
      // Always use the actual schedule date from AlarmKit, never stored values
      var finalRemaining = remaining
      if isSnoozed {
        // Always calculate from actual schedule date
        if let scheduleDate = currentScheduleDate, scheduleDate > now {
          finalRemaining = Int64(scheduleDate.timeIntervalSince(now))
        } else if let snoozeUntilStr = snoozeUntilISO, let snoozeUntilDate = isoFormatter.date(from: snoozeUntilStr) {
          if snoozeUntilDate > now {
            finalRemaining = Int64(snoozeUntilDate.timeIntervalSince(now))
          } else {
            finalRemaining = 0
          }
        }
      }
      
      let isRinging = finalRemaining <= 1 && currentState == .alerting && !isSnoozed
      
      stateMap[id] = AlarmStateInfo(
        isRinging: isRinging,
        isSnoozed: isSnoozed,
        remainingSeconds: finalRemaining,
        snoozeUntilISO: snoozeUntilISO
      )
    }
    
    return stateMap
  }
  
  @available(iOS 26.0, *)
  private func checkAndSendAlarmStateEvents(_ alarms: [Alarm]) {
    let stored = loadScheduled()
    // Store state as string representation for comparison
    var lastStates: [String: String] = [:]
    var lastRemaining: [String: Int64] = [:]
    var lastIsRinging: [String: Bool] = [:]
    var lastIsSnoozed: [String: Bool] = [:]
    var lastScheduleDates: [String: String] = [:] // Track schedule dates to detect snooze
    var lastSnoozeUntilISO: [String: String] = [:] // Track snooze until dates
    if let lastStatesData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastStates"),
       let decoded = try? JSONDecoder().decode([String: String].self, from: lastStatesData) {
      lastStates = decoded
    }
    if let lastRemainingData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastRemaining"),
       let decoded = try? JSONDecoder().decode([String: Int64].self, from: lastRemainingData) {
      lastRemaining = decoded
    }
    if let lastIsRingingData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastIsRinging"),
       let decoded = try? JSONDecoder().decode([String: Bool].self, from: lastIsRingingData) {
      lastIsRinging = decoded
    }
    if let lastIsSnoozedData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastIsSnoozed"),
       let decoded = try? JSONDecoder().decode([String: Bool].self, from: lastIsSnoozedData) {
      lastIsSnoozed = decoded
    }
    if let lastScheduleDatesData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastScheduleDates"),
       let decoded = try? JSONDecoder().decode([String: String].self, from: lastScheduleDatesData) {
      lastScheduleDates = decoded
    }
    if let lastSnoozeUntilISOData = UserDefaults.standard.data(forKey: "ReactNativeAlarm.lastSnoozeUntilISO"),
       let decoded = try? JSONDecoder().decode([String: String].self, from: lastSnoozeUntilISOData) {
      lastSnoozeUntilISO = decoded
    }
    
    var newStates: [String: String] = [:]
    var newRemaining: [String: Int64] = [:]
    var newIsRinging: [String: Bool] = [:]
    var newIsSnoozed: [String: Bool] = [:]
    var newScheduleDates: [String: String] = [:]
    var newSnoozeUntilISO: [String: String] = [:]
    let currentAlarmIds = Set(alarms.map { $0.id.uuidString })
    
    // Check for stopped alarms (alarms that were in lastStates but not in current alarms)
    for (id, _) in lastStates {
      if !currentAlarmIds.contains(id) {
        // Alarm was stopped/removed - only send event if it was ringing or snoozed
        if lastIsRinging[id] == true || lastIsSnoozed[id] == true {
          let storedItem = stored.first { ($0["id"] as? String) == id }
          let label = storedItem?["label"] as? String ?? ""
          let stoppedAtISO = isoFormatter.string(from: Date())
          sendEvent("onAlarmStopped", [
            "id": id,
            "label": label,
            "stoppedAtISO": stoppedAtISO
          ])
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": false,
            "isSnoozed": false,
            "remainingSeconds": 0,
            "stoppedAtISO": stoppedAtISO
          ])
        }
      }
    }
    
    for alarm in alarms {
      let id = alarm.id.uuidString
      let currentState = alarm.state
      // Convert state to string for storage
      let stateStr = String(describing: currentState)
      let remaining = self.calculateRemainingSeconds(alarm)
      
      // Get stored alarm data for snooze minutes
      let storedItem = stored.first { ($0["id"] as? String) == id }
      let snoozeMinutes = (storedItem?["snoozeMinutes"] as? Int) ?? 5  // default 5 minutes
      
      // Get current schedule date for snooze detection
      var currentScheduleDate: Date? = nil
      if let schedule = alarm.schedule {
        switch schedule {
        case .fixed(let date):
          currentScheduleDate = date
        default:
          break
        }
      }
      let currentScheduleDateISO = currentScheduleDate.map { isoFormatter.string(from: $0) } ?? ""
      
      // Get previous state
      let wasSnoozed = lastIsSnoozed[id] ?? false
      let wasRinging = lastIsRinging[id] ?? false
      let lastRemainingValue = lastRemaining[id] ?? Int64.max
      let lastStateStr = lastStates[id]
      let lastScheduleDateISO = lastScheduleDates[id] ?? ""
      
      // Detect snooze by checking multiple indicators
      // When LiveActivity snooze button is pressed, AlarmKit reschedules the alarm with a new future date
      var isSnoozed = false
      
      // Maintain snooze state if was snoozed and still in countdown/scheduled
      if wasSnoozed && (currentState == .countdown || currentState == .scheduled) {
        if let countdown = alarm.countdownDuration, let preAlert = countdown.preAlert, preAlert > 0 {
          if currentState != .alerting {
            isSnoozed = true
          }
        }
      }
      
      // Method 1: Schedule date appeared or changed while alarm was ringing/alerting
      if !isSnoozed && (wasRinging || lastStateStr == "alerting") && !currentScheduleDateISO.isEmpty {
        if lastScheduleDateISO.isEmpty {
          if let currentDate = currentScheduleDate, currentDate > Date() {
            isSnoozed = true
          }
        } else if currentScheduleDateISO != lastScheduleDateISO {
          if let currentDate = currentScheduleDate, currentDate > Date() {
            if let lastDate = isoFormatter.date(from: lastScheduleDateISO), currentDate > lastDate {
              isSnoozed = true
            }
          }
        }
      }
      
      // Method 2: State changed from alerting to countdown/scheduled with countdown duration
      if !isSnoozed && lastStateStr == "alerting" && (currentState == .countdown || currentState == .scheduled) {
        if let countdown = alarm.countdownDuration, let preAlert = countdown.preAlert, preAlert > 0 {
          isSnoozed = true
        } else if !currentScheduleDateISO.isEmpty {
          if let currentDate = currentScheduleDate, currentDate > Date() {
            isSnoozed = true
          }
        }
      }
      
      // Method 3: Fallback - remaining time jump
      if !isSnoozed {
        let wasRingingOrNearZero = wasRinging || lastRemainingValue <= 1
        let remainingJumpedSignificantly = wasRingingOrNearZero && remaining > 60
        let remainingJumpedModerately = wasRinging && remaining > 30 && lastRemainingValue <= 1
        let remainingIncreasedDramatically = wasRinging && remaining > lastRemainingValue + 20 && lastRemainingValue <= 1
        let anyRemainingJump = remainingJumpedSignificantly || remainingJumpedModerately || remainingIncreasedDramatically
        
        let stateChangedFromAlerting = lastStateStr == "alerting" && (currentState == .countdown || currentState == .scheduled)
        let stateChangedToScheduled = (lastStateStr == "countdown" || lastStateStr == "alerting") && currentState == .scheduled && anyRemainingJump
        if anyRemainingJump && (currentState == .countdown || currentState == .scheduled || stateChangedFromAlerting || stateChangedToScheduled) && !wasSnoozed {
          isSnoozed = true
        }
      }
      
      // Always use actual alarm data - calculate from current schedule date and current time
      let now = Date()
      
      // For snoozed alarms, use the stored snoozeUntilISO if already snoozed, otherwise calculate from schedule date
      // When snooze button is pressed, AlarmKit reschedules the alarm with a new future date
      let snoozeUntilISO: String = {
        if isSnoozed {
          // If already snoozed, use the stored snoozeUntilISO to maintain consistency
          if wasSnoozed, let lastSnoozeISO = lastSnoozeUntilISO[id], !lastSnoozeISO.isEmpty {
            return lastSnoozeISO
          }
          // First time detecting snooze - use the actual schedule date from AlarmKit
          if let scheduleDate = currentScheduleDate, scheduleDate > now {
            return isoFormatter.string(from: scheduleDate)
          } else {
            // If schedule date is not available (shouldn't happen), use custom snooze duration from stored value
            // Note: Don't use preAlert as it may be the original countdown duration (e.g., 5 seconds)
            let snoozeDuration = TimeInterval(snoozeMinutes * 60)  // Use stored snoozeMinutes
            return isoFormatter.string(from: now.addingTimeInterval(snoozeDuration))
          }
        } else {
          // Not snoozed - use schedule or remaining
          if let scheduleDate = currentScheduleDate {
            return isoFormatter.string(from: scheduleDate)
          } else {
            return isoFormatter.string(from: now.addingTimeInterval(TimeInterval(remaining)))
          }
        }
      }()
      
      // Calculate remaining seconds from actual schedule date and current time
      // Always use the actual schedule date from AlarmKit, never stored values
      var finalRemaining = remaining
      if isSnoozed {
        // Always calculate from actual schedule date
        if let scheduleDate = currentScheduleDate, scheduleDate > now {
          finalRemaining = Int64(scheduleDate.timeIntervalSince(now))
        } else if let snoozeUntilDate = isoFormatter.date(from: snoozeUntilISO) {
          if snoozeUntilDate > now {
            finalRemaining = Int64(snoozeUntilDate.timeIntervalSince(now))
          } else {
            finalRemaining = 0
          }
        }
      }
      
      // Store snoozeUntilISO for reference (but always use actual schedule date for calculation)
      if isSnoozed {
        newSnoozeUntilISO[id] = snoozeUntilISO
      } else {
        newSnoozeUntilISO.removeValue(forKey: id)
      }
      
      let isRinging = finalRemaining <= 1 && (currentState == .countdown || currentState == .alerting) && !isSnoozed
      
      newStates[id] = stateStr
      newRemaining[id] = finalRemaining
      newIsRinging[id] = isRinging
      newIsSnoozed[id] = isSnoozed
      newScheduleDates[id] = currentScheduleDateISO
      
      // Use already declared storedItem from line 629
      let label = storedItem?["label"] as? String ?? ""
      
      if let lastState = lastStateStr, lastState != stateStr {
        switch currentState {
        case .scheduled:
          // Alarm is scheduled but not yet active
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": false,
            "isSnoozed": false,
            "remainingSeconds": remaining
          ])
        case .countdown:
          if isSnoozed && !wasSnoozed {
            sendEvent("onAlarmSnoozed", [
              "id": id,
              "label": label,
              "snoozeUntilISO": snoozeUntilISO
            ])
            sendEvent("onAlarmStateChanged", [
              "id": id,
              "label": label,
              "isRinging": false,
              "isSnoozed": true,
              "remainingSeconds": finalRemaining,
              "snoozeUntilISO": snoozeUntilISO
            ])
          } else if !isSnoozed && wasSnoozed {
            // Snooze was cancelled (e.g., from system UI or another app)
            sendEvent("onAlarmStateChanged", [
              "id": id,
              "label": label,
              "isRinging": false,
              "isSnoozed": false,
              "remainingSeconds": finalRemaining
            ])
          } else if isRinging && !wasRinging && !isSnoozed {
            sendEvent("onAlarmStarted", [
              "id": id,
              "label": label,
              "remainingSeconds": 0
            ])
            sendEvent("onAlarmStateChanged", [
              "id": id,
              "label": label,
              "isRinging": true,
              "isSnoozed": false,
              "remainingSeconds": 0
            ])
          } else if isSnoozed && wasSnoozed && abs(finalRemaining - lastRemainingValue) >= 1 {
            // Update remaining time for snoozed alarm
            sendEvent("onAlarmStateChanged", [
              "id": id,
              "label": label,
              "isRinging": false,
              "isSnoozed": true,
              "remainingSeconds": finalRemaining,
              "snoozeUntilISO": snoozeUntilISO
            ])
          } else if finalRemaining > 0 && !isSnoozed {
            sendEvent("onAlarmStarted", [
              "id": id,
              "label": label,
              "remainingSeconds": finalRemaining
            ])
            sendEvent("onAlarmStateChanged", [
              "id": id,
              "label": label,
              "isRinging": false,
              "isSnoozed": false,
              "remainingSeconds": finalRemaining
            ])
          }
        case .alerting:
          if isSnoozed && !wasSnoozed {
            sendEvent("onAlarmSnoozed", [
              "id": id,
              "label": label,
              "snoozeUntilISO": snoozeUntilISO
            ])
            sendEvent("onAlarmStateChanged", [
              "id": id,
              "label": label,
              "isRinging": false,
              "isSnoozed": true,
              "remainingSeconds": finalRemaining,
              "snoozeUntilISO": snoozeUntilISO
            ])
          } else if !isSnoozed && wasSnoozed {
            // Snooze was cancelled (e.g., from system UI or another app)
            sendEvent("onAlarmStateChanged", [
              "id": id,
              "label": label,
              "isRinging": isRinging,
              "isSnoozed": false,
              "remainingSeconds": finalRemaining
            ])
          } else if isRinging && !wasRinging {
            sendEvent("onAlarmStarted", [
              "id": id,
              "label": label,
              "remainingSeconds": 0
            ])
            sendEvent("onAlarmStateChanged", [
              "id": id,
              "label": label,
              "isRinging": true,
              "isSnoozed": false,
              "remainingSeconds": 0
            ])
          } else {
            sendEvent("onAlarmStateChanged", [
              "id": id,
              "label": label,
              "isRinging": isRinging,
              "isSnoozed": isSnoozed,
              "remainingSeconds": finalRemaining,
              "snoozeUntilISO": isSnoozed ? snoozeUntilISO : nil
            ])
          }
        case .paused:
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": false,
            "isSnoozed": false,
            "remainingSeconds": finalRemaining
          ])
        @unknown default:
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": isRinging,
            "isSnoozed": isSnoozed,
            "remainingSeconds": finalRemaining,
            "snoozeUntilISO": isSnoozed ? snoozeUntilISO : nil
          ])
        }
      } else if lastStateStr == nil {
        // First time seeing this alarm (e.g., after app restart) - send current state
        if isSnoozed {
          sendEvent("onAlarmSnoozed", [
            "id": id,
            "label": label,
            "snoozeUntilISO": snoozeUntilISO
          ])
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": false,
            "isSnoozed": true,
            "remainingSeconds": finalRemaining,
            "snoozeUntilISO": snoozeUntilISO
          ])
        } else if isRinging {
          sendEvent("onAlarmStarted", [
            "id": id,
            "label": label,
            "remainingSeconds": 0
          ])
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": true,
            "isSnoozed": false,
            "remainingSeconds": 0
          ])
        } else if finalRemaining > 0 {
          sendEvent("onAlarmStarted", [
            "id": id,
            "label": label,
            "remainingSeconds": finalRemaining
          ])
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": false,
            "isSnoozed": false,
            "remainingSeconds": finalRemaining
          ])
        } else {
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": false,
            "isSnoozed": false,
            "remainingSeconds": finalRemaining
          ])
        }
      } else if isSnoozed && wasSnoozed && abs(finalRemaining - lastRemainingValue) >= 1 {
        // Update remaining time for snoozed alarm
        sendEvent("onAlarmStateChanged", [
          "id": id,
          "label": label,
          "isRinging": false,
          "isSnoozed": true,
          "remainingSeconds": finalRemaining,
          "snoozeUntilISO": snoozeUntilISO
        ])
      } else {
        if isSnoozed && !wasSnoozed {
          sendEvent("onAlarmSnoozed", [
            "id": id,
            "label": label,
            "snoozeUntilISO": snoozeUntilISO
          ])
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": false,
            "isSnoozed": true,
            "remainingSeconds": remaining,
            "snoozeUntilISO": snoozeUntilISO
          ])
        } else if !isSnoozed && wasSnoozed {
          // Snooze was cancelled (e.g., from system UI or another app)
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": isRinging,
            "isSnoozed": false,
            "remainingSeconds": remaining
          ])
        } else if isRinging && !wasRinging {
          sendEvent("onAlarmStarted", [
            "id": id,
            "label": label,
            "remainingSeconds": 0
          ])
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": true,
            "isSnoozed": false,
            "remainingSeconds": 0
          ])
        } else if isRinging && wasRinging && abs(remaining - lastRemainingValue) >= 1 {
          sendEvent("onAlarmStateChanged", [
            "id": id,
            "label": label,
            "isRinging": true,
            "isSnoozed": false,
            "remainingSeconds": remaining
          ])
        } else if abs(remaining - lastRemainingValue) >= 1 && remaining > 0 && !isRinging {
          var eventMap: [String: Any] = [
            "id": id,
            "label": label,
            "isRinging": false,
            "isSnoozed": isSnoozed,
            "remainingSeconds": remaining
          ]
          if isSnoozed {
            eventMap["snoozeUntilISO"] = snoozeUntilISO
          }
          sendEvent("onAlarmStateChanged", eventMap)
        }
      }
    }
    
    // Save current states
    if let encoded = try? JSONEncoder().encode(newStates) {
      UserDefaults.standard.set(encoded, forKey: "ReactNativeAlarm.lastStates")
    }
    if let encoded = try? JSONEncoder().encode(newRemaining) {
      UserDefaults.standard.set(encoded, forKey: "ReactNativeAlarm.lastRemaining")
    }
    if let encoded = try? JSONEncoder().encode(newIsRinging) {
      UserDefaults.standard.set(encoded, forKey: "ReactNativeAlarm.lastIsRinging")
    }
    if let encoded = try? JSONEncoder().encode(newIsSnoozed) {
      UserDefaults.standard.set(encoded, forKey: "ReactNativeAlarm.lastIsSnoozed")
    }
    if let encoded = try? JSONEncoder().encode(newScheduleDates) {
      UserDefaults.standard.set(encoded, forKey: "ReactNativeAlarm.lastScheduleDates")
    }
    if let encoded = try? JSONEncoder().encode(newSnoozeUntilISO) {
      UserDefaults.standard.set(encoded, forKey: "ReactNativeAlarm.lastSnoozeUntilISO")
    }
  }
  
  @available(iOS 26.0, *)
  private func calculateRemainingSeconds(_ alarm: Alarm) -> Int64 {
    let stored = loadScheduled()
    let storedItem = stored.first { ($0["id"] as? String) == alarm.id.uuidString }
    
    if let schedule = alarm.schedule {
      switch schedule {
      case .fixed(let date):
        return max(0, Int64(date.timeIntervalSinceNow))
      default:
        break
      }
    }
    
    if let countdown = alarm.countdownDuration, let preAlert = countdown.preAlert {
      if let scheduleTimeISO = storedItem?["scheduleTimeISO"] as? String,
         let scheduleTime = isoFormatter.date(from: scheduleTimeISO) {
        let elapsed = Date().timeIntervalSince(scheduleTime)
        return max(0, Int64(preAlert - elapsed))
      } else if let countdownSeconds = storedItem?["countdownSeconds"] as? Double,
                let scheduleTimeISO = storedItem?["scheduleTimeISO"] as? String,
                let scheduleTime = isoFormatter.date(from: scheduleTimeISO) {
        let elapsed = Date().timeIntervalSince(scheduleTime)
        return max(0, Int64(countdownSeconds - elapsed))
      }
    }
    
    return 0
  }
  
  @available(iOS 26.0, *)
  private func startAlarmUpdatesObserver() {
    if cancelAlarmUpdatesObserver != nil { return }
    let task = Task.detached(priority: .background) { [weak self] in
      for await alarms in AlarmManager.shared.alarmUpdates {
        guard let strongSelf = self else { break }
        await strongSelf.handleAlarmUpdatesOnMain(alarms)
      }
    }
    cancelAlarmUpdatesObserver = { task.cancel() }
  }
  
  @available(iOS 26.0, *)
  @MainActor
  private func handleAlarmUpdatesOnMain(_ alarms: [Alarm]) {
    self.checkAndSendAlarmStateEvents(alarms)
  }
  #endif

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

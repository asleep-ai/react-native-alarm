require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'ReactNativeAlarm'
  s.version        = package['version']
  s.summary        = package['description']
  s.description    = package['description']
  s.license        = package['license']
  s.author         = package['author']
  s.homepage       = package['homepage']
  s.platforms      = {
    :ios => '15.1',
    :tvos => '15.1'
  }
  s.swift_version  = '5.9'
  s.source         = { git: 'https://github.com/asleep-ai/react-native-alarm' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'

  # Weak-link AlarmKit so builds succeed on older SDKs while enabling iOS 18+ features.
  s.weak_frameworks = 'AlarmKit'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  # Fix for React-Core-umbrella.h not found in React Native 0.81.5+
  # Create umbrella header if it doesn't exist during pod installation
  s.prepare_command = <<-CMD
    REACT_CORE_HEADERS_PATH="${PODS_ROOT}/Headers/Public/React-Core/React"
    UMBRELLA_HEADER_PATH="${REACT_CORE_HEADERS_PATH}/React-Core-umbrella.h"
    
    if [ ! -f "${UMBRELLA_HEADER_PATH}" ]; then
      mkdir -p "${REACT_CORE_HEADERS_PATH}"
      cat > "${UMBRELLA_HEADER_PATH}" << 'EOF'
#ifdef __cplusplus
#import <React/RCTBridge.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTEventDispatcher.h>
#import <React/RCTEventEmitter.h>
#import <React/RCTViewManager.h>
#else
#import "RCTBridge.h"
#import "RCTBridgeModule.h"
#import "RCTEventDispatcher.h"
#import "RCTEventEmitter.h"
#import "RCTViewManager.h"
#endif
EOF
    fi
  CMD

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end

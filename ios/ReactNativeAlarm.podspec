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
  s.platforms      = { :ios => '15.1' }
  s.swift_version  = '5.9'
  s.source         = {
    git: 'https://github.com/asleep-ai/react-native-alarm',
    tag: s.version.to_s
  }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.frameworks = 'AlarmKit'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "ios/**/*.{h,m,mm,swift,hpp,cpp}"

  s.script_phase = {
    :name => 'ReactNativeAlarm Umbrella Header',
    :shell_path => '/bin/sh',
    :execution_position => :before_compile,
    :script => <<-'SCRIPT'
set -euo pipefail
if [ -z "${PODS_ROOT:-}" ]; then
  echo "ReactNativeAlarm: PODS_ROOT missing, skipping umbrella fix"
  exit 0
fi

HEADER_DIR="${PODS_ROOT}/Headers/Public/React-Core/React"
HEADER_PATH="${HEADER_DIR}/React-Core-umbrella.h"

if [ ! -f "${HEADER_PATH}" ]; then
  mkdir -p "${HEADER_DIR}"
  cat <<'HEADER' > "${HEADER_PATH}"
#ifdef __cplusplus
#import <React/RCTBridge.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTEventDispatcher.h>
#import <React/RCTEventEmitter.h>
#import <React/RCTViewManager.h>
#import <React/RCTView.h>
#else
#import "RCTBridge.h"
#import "RCTBridgeModule.h"
#import "RCTEventDispatcher.h"
#import "RCTEventEmitter.h"
#import "RCTViewManager.h"
#import "RCTView.h"
#endif
HEADER
  echo "ReactNativeAlarm: Installed fallback React-Core-umbrella.h at ${HEADER_PATH}"
fi
SCRIPT
  }
end

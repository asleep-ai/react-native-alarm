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
  s.public_header_files = "ios/React-Core-umbrella.h"

  s.script_phase = {
    :name => 'ReactNativeAlarm Umbrella Header',
    :shell_path => '/bin/sh',
    :execution_position => :before_compile,
    :script => <<-'SCRIPT'
set -euo pipefail

SOURCE_HEADER="${PODS_TARGET_SRCROOT}/ios/React-Core-umbrella.h"
if [ ! -f "${SOURCE_HEADER}" ]; then
  SOURCE_HEADER="${PODS_TARGET_SRCROOT}/React-Core-umbrella.h"
fi

if [ ! -f "${SOURCE_HEADER}" ]; then
  echo "ReactNativeAlarm: umbrella header missing in pod sources; skipping."
  exit 0
fi

HEADER_DIR="${PODS_ROOT}/Headers/Public/React"
HEADER_PATH="${HEADER_DIR}/React-Core-umbrella.h"

if [ ! -f "${HEADER_PATH}" ]; then
  mkdir -p "${HEADER_DIR}"
  cp "${SOURCE_HEADER}" "${HEADER_PATH}"
  echo "ReactNativeAlarm: Installed fallback React-Core-umbrella.h at ${HEADER_PATH}"
fi
SCRIPT
  }
end

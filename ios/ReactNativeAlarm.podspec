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
  # Copy umbrella header to React-Core headers directory during pod installation
  # This script runs after the pod is downloaded but before installation
  s.prepare_command = <<-CMD
    set -e
    # Get the directory where this podspec is located (the ios directory)
    PODSPEC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    UMBRELLA_SOURCE="${PODSPEC_DIR}/React-Core-umbrella.h"
    
    # Check if the source file exists
    if [ ! -f "${UMBRELLA_SOURCE}" ]; then
      echo "Warning: React-Core-umbrella.h not found at ${UMBRELLA_SOURCE}"
      exit 0
    fi
    
    # Try to copy to React-Core headers if PODS_ROOT is available
    # Note: This may not work if PODS_ROOT is not set yet, but it's worth trying
    if [ -n "${PODS_ROOT}" ] && [ -d "${PODS_ROOT}/Headers/Public/React-Core/React" ]; then
      REACT_CORE_HEADERS_PATH="${PODS_ROOT}/Headers/Public/React-Core/React"
      UMBRELLA_HEADER_PATH="${REACT_CORE_HEADERS_PATH}/React-Core-umbrella.h"
      
      if [ ! -f "${UMBRELLA_HEADER_PATH}" ]; then
        mkdir -p "${REACT_CORE_HEADERS_PATH}"
        cp "${UMBRELLA_SOURCE}" "${UMBRELLA_HEADER_PATH}"
        echo "Copied React-Core-umbrella.h to ${UMBRELLA_HEADER_PATH}"
      fi
    else
      echo "PODS_ROOT not available or React-Core headers not found, skipping header copy"
      echo "The header will be copied in post_install hook if needed"
    fi
  CMD

  # Swift/Objective-C compatibility and header search paths
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'HEADER_SEARCH_PATHS' => '$(inherited) "${PODS_ROOT}/Headers/Public/React-Core" "${PODS_ROOT}/Headers/Public/React-Core/React"',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end

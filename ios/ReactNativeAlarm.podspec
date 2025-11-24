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
  # Include the umbrella header file in the pod source
  s.public_header_files = "React-Core-umbrella.h"
  
  # Copy umbrella header to React-Core headers directory during pod installation
  # This runs after the pod is downloaded but before it's installed
  s.prepare_command = <<-CMD
    set -e
    # Get the directory where this podspec is located
    PODSPEC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-${0}}")" && pwd)"
    UMBRELLA_SOURCE="${PODSPEC_DIR}/React-Core-umbrella.h"
    
    if [ ! -f "${UMBRELLA_SOURCE}" ]; then
      echo "Warning: React-Core-umbrella.h not found at ${UMBRELLA_SOURCE}"
      exit 0
    fi
    
    # Try to find PODS_ROOT from various possible locations
    # First, try environment variable
    if [ -n "${PODS_ROOT}" ] && [ -d "${PODS_ROOT}/Headers/Public/React-Core/React" ]; then
      REACT_CORE_HEADERS_PATH="${PODS_ROOT}/Headers/Public/React-Core/React"
      UMBRELLA_HEADER_PATH="${REACT_CORE_HEADERS_PATH}/React-Core-umbrella.h"
      
      if [ ! -f "${UMBRELLA_HEADER_PATH}" ]; then
        mkdir -p "${REACT_CORE_HEADERS_PATH}"
        cp "${UMBRELLA_SOURCE}" "${UMBRELLA_HEADER_PATH}"
        echo "Copied React-Core-umbrella.h to ${UMBRELLA_HEADER_PATH}"
      fi
    # Try to find from current working directory (when running from example/ios)
    elif [ -d "../../Pods/Headers/Public/React-Core/React" ]; then
      REACT_CORE_HEADERS_PATH="../../Pods/Headers/Public/React-Core/React"
      UMBRELLA_HEADER_PATH="${REACT_CORE_HEADERS_PATH}/React-Core-umbrella.h"
      
      if [ ! -f "${UMBRELLA_HEADER_PATH}" ]; then
        mkdir -p "${REACT_CORE_HEADERS_PATH}"
        cp "${UMBRELLA_SOURCE}" "${UMBRELLA_HEADER_PATH}"
        echo "Copied React-Core-umbrella.h to ${UMBRELLA_HEADER_PATH} (relative path)"
      fi
    else
      echo "Note: PODS_ROOT not found, header will be copied during build if needed"
    fi
  CMD

  # Swift/Objective-C compatibility and header search paths
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'HEADER_SEARCH_PATHS' => '$(inherited) "${PODS_ROOT}/Headers/Public/React-Core" "${PODS_ROOT}/Headers/Public/React-Core/React"',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end

#!/usr/bin/env bash
# Phase 5 Task T4a — builds the KMP `shared.xcframework` that `Packages/MentoraShared/Package.swift`
# wraps as a binaryTarget, via the Gradle tasks Task T1 registered in `mobile/shared/build.gradle.kts`
# (the `XCFramework("shared")` DSL holder). Run this once before opening the Xcode project generated
# from `project.yml` (or any time `mobile/shared` changes and you need a fresh framework).
#
# This is Kotlin/Native iOS compilation, which only ever links on a macOS host with Xcode installed
# (`execution/PHASE_5_IOS_SYSTEM_DESIGN.md` § 0) — this script is authored on a Windows host and is
# genuinely unverified until it is actually run on a Mac (MC-2).
#
# Task names, verified directly against `mobile/shared/build.gradle.kts` by applying the
# `XCFramework("shared")` change on this host and inspecting the registered task graph (Task T1):
#   :shared:assembleSharedDebugXCFramework    debug build (this script's default)
#   :shared:assembleSharedReleaseXCFramework  release build
#   :shared:assembleXCFramework               umbrella task, builds both
# There is NO `:shared:assembleSharedXCFramework` task — that name does not exist; do not use it.
#
# Output path (Kotlin Multiplatform's documented XCFramework DSL convention, also assumed by
# `mobile/.gitignore`'s `**/build/XCFrameworks/` entry):
#   mobile/shared/build/XCFrameworks/debug/shared.xcframework
#   mobile/shared/build/XCFrameworks/release/shared.xcframework

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
# scripts/ -> iosApp/ -> mobile/
MOBILE_DIR="$(cd "${SCRIPT_DIR}/../.." >/dev/null 2>&1 && pwd)"

CONFIGURATION="${1:-debug}"

cd "${MOBILE_DIR}"

case "${CONFIGURATION}" in
  debug)
    echo "Building shared.xcframework (debug) via :shared:assembleSharedDebugXCFramework ..."
    ./gradlew :shared:assembleSharedDebugXCFramework
    echo "Output: mobile/shared/build/XCFrameworks/debug/shared.xcframework"
    ;;
  release)
    echo "Building shared.xcframework (release) via :shared:assembleSharedReleaseXCFramework ..."
    ./gradlew :shared:assembleSharedReleaseXCFramework
    echo "Output: mobile/shared/build/XCFrameworks/release/shared.xcframework"
    ;;
  both)
    echo "Building shared.xcframework (debug + release) via :shared:assembleXCFramework ..."
    ./gradlew :shared:assembleXCFramework
    echo "Output: mobile/shared/build/XCFrameworks/{debug,release}/shared.xcframework"
    ;;
  *)
    echo "Usage: $0 [debug|release|both]  (default: debug)" >&2
    exit 1
    ;;
esac

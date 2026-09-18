// swift-tools-version:5.9
import PackageDescription

// Phase 5 Task T4a — local Swift Package wrapping the KMP-built `shared.xcframework` as a
// `binaryTarget`, per `execution/PHASE_5_IOS_SYSTEM_DESIGN.md` § 4 / `architecture/
// KMP_ARCHITECTURE.md` § 4's explicit guidance to consume `shared` from iOS via Swift Package
// Manager (not a raw framework embed in the Xcode project). Referenced from
// `mobile/iosApp/project.yml` as the local package `MentoraShared`, product `Shared`.
//
// The `path:` below is a BEST-EFFORT path, NOT verified — this file is authored on a Windows host
// with no macOS/Xcode toolchain, so `mobile/shared/build/XCFrameworks/debug/shared.xcframework` has
// never actually been produced or confirmed to exist at this exact location. It follows Kotlin
// Multiplatform's documented `XCFramework` DSL output convention
// (`<module>/build/XCFrameworks/<buildType>/<xcFrameworkName>.xcframework`), matches the comments in
// `mobile/shared/build.gradle.kts` (Task T1, which registers `assembleSharedDebugXCFramework` /
// `assembleSharedReleaseXCFramework` via `XCFramework("shared")`), and matches
// `mobile/.gitignore`'s `**/build/XCFrameworks/` entry. Confirm this path for real the first time
// `mobile/iosApp/scripts/build-shared-xcframework.sh` actually runs on a Mac (MC-2); update this
// `path:` if the real output differs.
let package = Package(
    name: "MentoraShared",
    platforms: [
        .iOS(.v17)
    ],
    products: [
        .library(name: "Shared", targets: ["Shared"])
    ],
    targets: [
        .binaryTarget(
            name: "Shared",
            path: "../../../shared/build/XCFrameworks/debug/shared.xcframework"
        )
    ]
)

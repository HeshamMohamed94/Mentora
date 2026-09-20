import XCTest
@testable import iosApp

/// Phase 5 Task T11 slice 1 -- `Components/CourseArtwork.swift`'s pure `courseArtworkHash`/`motifFor`
/// (`design-to-code/shared/artwork.json`'s `assignmentRule`: "never a random per-render pick, so a
/// given course's card looks identical everywhere it appears").
///
/// The golden-vector values below are ported EXACTLY from Android's own
/// `mobile/androidApp/src/test/kotlin/com/mentora/android/ui/components/CourseArtworkHashTest.kt`,
/// whose own kdoc explains they were computed independently, in Node, from the *exact* JS formula
/// `artwork.json` specifies (`hash = (hash*31 + charCode) >>> 0`, `index = hash % 5`) -- not derived
/// from either platform's implementation. Porting the same literal expected indices here proves iOS
/// and Android actually agree on the same real formula, not just that each is internally
/// self-consistent.
///
/// Plain, non-isolated `final class` -- `courseArtworkHash`/`motifFor` are pure functions with no
/// `@MainActor` isolation, so no such annotation is needed here.
final class CourseArtworkTests: XCTestCase {

    // MARK: - Determinism

    func test_sameSeedAlwaysProducesTheSameHashAndMotif() {
        let seeds = ["Software Development", "Design", "course-123", "", "Data & Analytics"]
        for seed in seeds {
            XCTAssertEqual(
                courseArtworkHash(seed: seed), courseArtworkHash(seed: seed),
                "hash must be deterministic for seed '\(seed)'"
            )
        }
        for seed in seeds {
            let first = motifFor(seed: seed)
            let second = motifFor(seed: seed)
            XCTAssertEqual(first, second, "motifFor must be deterministic for seed '\(seed)'")
        }
    }

    // MARK: - Range

    func test_motifIndexAlwaysFallsWithinTheFiveMotifRange() {
        let seeds = ["a", "b", "Software Development", "Data & Analytics", "zzzzzzzzzz", "12345"]
        for seed in seeds {
            XCTAssertTrue(
                CourseMotif.allCases.contains(motifFor(seed: seed)),
                "motifFor('\(seed)') returned an entry outside CourseMotif.allCases"
            )
        }
    }

    // MARK: - Golden vectors (cross-platform parity proof)

    /// Computed in Node from `artwork.json`'s own formula, not from this Swift code or the Kotlin
    /// implementation -- see file header. Index order: analytics=0, design=1, code=2, grid=3, layers=4.
    func test_knownSeedsResolveToTheExactIndexTheSpecFormulaComputesIndependently() {
        XCTAssertEqual(motifFor(seed: "Data & Analytics"), CourseMotif.allCases[3])
        XCTAssertEqual(motifFor(seed: "Design"), CourseMotif.allCases[2])
        XCTAssertEqual(motifFor(seed: "Software Development"), CourseMotif.allCases[2])
        XCTAssertEqual(motifFor(seed: "Business Skills"), CourseMotif.allCases[3])
        XCTAssertEqual(motifFor(seed: "general/fallback"), CourseMotif.allCases[4])
        XCTAssertEqual(motifFor(seed: "course-123"), CourseMotif.allCases[2])
        XCTAssertEqual(motifFor(seed: ""), CourseMotif.allCases[0])
    }
}

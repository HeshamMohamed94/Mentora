import Foundation
import shared

/// The one boundary layer between SwiftUI and KMP (System Design § 2). `.invoke` appears ONLY in
/// this file -- every other file under `Features/`/`Components/` calls a named method here instead.
///
/// A `struct`, not a class: value semantics, trivially passed into screen-model initializers
/// (System Design § 3.1). NOT `@MainActor`, NOT `Sendable` -- `project.yml` sets
/// `SWIFT_VERSION: "5.0"` (strict concurrency off); do not "fix" this by adding either annotation.
///
/// Cancellation: a suspend call's SKIE-generated `async throws` wrapper converts a Kotlin
/// `CancellationException` into a thrown Swift error, which this bridge does NOT catch or
/// specially wrap -- it just propagates. Callers write
/// `catch let e as MentoraError { ... } catch { /* cancelled, or a lower-level throw */ }`.
///
/// Seam definitions (System Design § 2 item 4 -- per-screen closures/protocols over this client)
/// are deliberately NOT part of this file; each screen task declares its own narrow seam.
///
/// Slice 1 (CI run #17, green) covered `auth` + `user`; slice 2 adds the remaining 8 façades
/// (`catalog`, `enrollment`, `learningPaths`, `media`, `progress`, `quiz`, `certificates`,
/// `aiTutor`) -- all 10 façades / 37 use cases are now named methods here except `aiStream(...)`,
/// deferred to its own follow-up commit for ordinary slice hygiene, not because its signature is
/// unknown (see DECISIONS_LOG D116(d)). 24 of the other 25 signatures below were copied from the real
/// shipped SKIE `.swiftinterface` inside CI's `kmp-swift-interface` artifact
/// (`shared.xcframework/.../shared.swiftmodule/arm64-apple-ios-simulator.swiftinterface`);
/// `thumbnailURLString` (non-suspend, absent from the `.swiftinterface`) is instead sourced from
/// `shared-api.json`/`shared.apinotes` in that same artifact, both stronger evidence than the Kotlin
/// source or the Kotlin->Obj-C header alone (that header is PRE-SKIE: its Flow return types are wrong,
/// which is the trap D108 fell into once already).
///
/// Default arguments below are SWIFT-side and ours alone. SKIE 0.9.5 generates no default-argument
/// overloads (D112), so every `.invoke(...)` call passes every Kotlin parameter explicitly.
///
/// Do NOT split this type across files. `sdk` is `private` deliberately (D112 Fix 6 -- the `.invoke`
/// boundary is compiler-enforced, not grep-enforced), which only holds while every method is here.
///
/// Review fix round (D112, Fix 6): `FlowBridge.swift`'s four flow/state-read methods
/// (`authStates`/`currentAuthState`/`localeChanges`/`currentLocale`) were folded directly into this
/// file (that separate file is deleted) specifically so `sdk` below could become `private` --
/// tightening the `.invoke` boundary from a grep-enforced convention to a compiler-enforced one.
/// Nothing outside this file's own methods reads `sdk` (confirmed by grep before making this change).
struct MentoraClient {
    private let sdk: MentoraSdk

    init(sdk: MentoraSdk) {
        self.sdk = sdk
    }

    // MARK: - auth

    func register(email: String, password: String, name: String) async throws -> SessionUser {
        try ApiResultBridge.unwrap(try await sdk.auth.register.invoke(email: email, password: password, name: name))
    }

    func login(email: String, password: String) async throws -> SessionUser {
        try ApiResultBridge.unwrap(try await sdk.auth.login.invoke(email: email, password: password))
    }

    func logout() async throws {
        try ApiResultBridge.unwrapVoid(try await sdk.auth.logout.invoke())
    }

    func refreshSession() async throws {
        try ApiResultBridge.unwrapVoid(try await sdk.auth.refreshSession.invoke())
    }

    /// Returns `AuthState` directly -- NOT wrapped in `ApiResult` (verified: `RestoreSessionUseCase`'s
    /// real signature is `invoke() async throws -> shared.AuthState`).
    func restoreSession() async throws -> AuthState {
        try await sdk.auth.restoreSession.invoke()
    }

    // MARK: - user

    func profile() async throws -> User {
        try ApiResultBridge.unwrap(try await sdk.user.getProfile.invoke())
    }

    func updateProfile(name: String) async throws -> User {
        try ApiResultBridge.unwrap(try await sdk.user.updateProfile.invoke(name: name))
    }

    func setLocale(_ locale: AppLocale) async throws {
        try ApiResultBridge.unwrapVoid(try await sdk.user.setLocale.invoke(locale: locale))
    }

    /// Synchronous, no `ApiResult` wrapper -- a local preference write.
    func setTheme(_ theme: ThemePreference) {
        sdk.user.setTheme.invoke(theme: theme)
    }

    // Deliberately NOT exposed (do not add these, even though they exist on `shared`):
    // `SetLocaleUseCase.onLogin(accountPreferredLocale:)` / `.onRegister()` -- these are called by
    // `LoginUseCase`/`RegisterUseCase` internally on the Kotlin side already; exposing and calling
    // them again from Swift would double-apply locale precedence (an F6 violation).

    // MARK: - catalog

    /// `ApiResult<List<Category>>` bridges as `ApiResult<NSArray>` -- real `.swiftinterface`:
    /// `invoke() async throws -> ApiResult<Foundation.NSArray>`. Swift's `NSArray` is non-generic,
    /// so the element type has no parameterized spelling here; de-erased once, in `unwrapList`.
    /// (This closes D112's open slice-2 question about `ApiResult<NSArray<T>>` variance: it never
    /// arises.)
    func categories() async throws -> [Category] {
        try ApiResultBridge.unwrapList(try await sdk.catalog.listCategories.invoke())
    }

    /// `CourseFilters`' four fields are decomposed into plain Swift parameters so no `Features/`
    /// file ever has to box `maxPrice` into a `KotlinInt` itself (A5). `?language=` is threaded
    /// server-side by `CatalogRepository` from the active locale -- never a parameter here (A4).
    func searchCourses(
        category: String? = nil,
        level: CourseLevel? = nil,
        maxPrice: Int? = nil,
        query: String? = nil,
        cursor: String? = nil
    ) async throws -> Page<CourseSummary> {
        let filters = CourseFilters(
            category: category,
            level: level,
            maxPrice: ApiResultBridge.boxedInt(maxPrice),
            query: query
        )
        return try ApiResultBridge.unwrapPage(
            try await sdk.catalog.searchCourses.invoke(filters: filters, cursor: cursor)
        )
    }

    func course(id: String) async throws -> Course {
        try ApiResultBridge.unwrap(try await sdk.catalog.getCourseDetails.invoke(courseId: id))
    }

    /// Module-qualified `shared.Section` deliberately: SwiftUI also declares a `Section`, and this
    /// file must stay unambiguous if it ever gains a SwiftUI import. `GetCourseCurriculumUseCase` hits
    /// the same `GET /courses/{id}` endpoint as `course(id:)` above (no separate curriculum endpoint
    /// exists) -- but `CatalogRepositoryImpl` has no cache, so calling both back-to-back for the same
    /// course issues two real network requests, not one deduplicated one.
    func curriculum(courseId: String) async throws -> [shared.Section] {
        try ApiResultBridge.unwrapList(try await sdk.catalog.getCourseCurriculum.invoke(courseId: courseId))
    }

    // MARK: - enrollment

    func checkoutPreview(courseId: String) async throws -> CheckoutPreview {
        try ApiResultBridge.unwrap(try await sdk.enrollment.getCheckoutPreview.invoke(courseId: courseId))
    }

    /// Demo checkout only -- no payment SDK, no gateway, no card data, no payment vocabulary
    /// anywhere on the Swift side (E3). A repeat call for an already-enrolled course is an ordinary
    /// success with `alreadyEnrolled == true`, never an error -- and must never be wrapped in a
    /// client-side retry loop (Phase 3 Task 8 "Must NOT"; F6).
    func completeDemoCheckout(courseId: String) async throws -> EnrollmentCompletion {
        try ApiResultBridge.unwrap(try await sdk.enrollment.completeDemoCheckout.invoke(courseId: courseId))
    }

    func enrollments(cursor: String? = nil, limit: Int? = nil) async throws -> Page<Enrollment> {
        try ApiResultBridge.unwrapPage(
            try await sdk.enrollment.listEnrollments.invoke(cursor: cursor, limit: ApiResultBridge.boxedInt(limit))
        )
    }

    /// The enrollment+course composition (`GetMyLearningUseCase`'s deliberate N+1) lives in Kotlin,
    /// exactly once -- never re-composed in Swift (A4).
    func myLearning(cursor: String? = nil, limit: Int? = nil) async throws -> Page<MyLearningItem> {
        try ApiResultBridge.unwrapPage(
            try await sdk.enrollment.getMyLearning.invoke(cursor: cursor, limit: ApiResultBridge.boxedInt(limit))
        )
    }

    // MARK: - learningPaths

    /// Plain unpaginated list, guest-accessible -- never a `CursorPage` (see `LearningPathRepository`).
    func learningPaths() async throws -> [LearningPath] {
        try ApiResultBridge.unwrapList(try await sdk.learningPaths.listLearningPaths.invoke())
    }

    func learningPath(id: String) async throws -> LearningPathDetail {
        try ApiResultBridge.unwrap(try await sdk.learningPaths.getLearningPathDetail.invoke(id: id))
    }

    /// Idempotent server-side: following an already-followed path is `true`, never an error. The
    /// CSRF header is attached globally by `HttpClientFactory` -- never added in Swift (A4).
    @discardableResult
    func followLearningPath(id: String) async throws -> Bool {
        try ApiResultBridge.unwrapBool(try await sdk.learningPaths.followLearningPath.invoke(id: id))
    }

    /// Idempotent: unfollowing a path that isn't followed is `false`, never an error.
    @discardableResult
    func unfollowLearningPath(id: String) async throws -> Bool {
        try ApiResultBridge.unwrapBool(try await sdk.learningPaths.unfollowLearningPath.invoke(id: id))
    }

    // MARK: - media

    /// Already absolute: the relative->absolute resolution happens exactly once, in Kotlin
    /// (`GetLessonPlaybackSourceUseCase`) -- never re-resolved here (A4).
    func playbackSource(mediaId: String) async throws -> PlaybackSource {
        try ApiResultBridge.unwrap(try await sdk.media.getLessonPlaybackSource.invoke(mediaId: mediaId))
    }

    /// nil means "the current source is still comfortably valid, nothing was re-requested" -- keep
    /// using `current` unchanged. nil must NEVER become an error, a retry, or a Swift-side refresh
    /// timer (F6): the 30-second-buffer expiry policy is Kotlin's alone (E6).
    func refreshPlaybackSource(mediaId: String, current: PlaybackSource) async throws -> PlaybackSource? {
        try ApiResultBridge.unwrapOptional(
            try await sdk.media.refreshPlaybackUrl.invoke(mediaId: mediaId, current: current)
        )
    }

    /// Synchronous, no network, no `ApiResult`, not `suspend` -- pure string construction over the
    /// PUBLIC `/api/v1/media/{id}/file` route. This is E4's "a URL produced by `sdk.media.resolveThumbnailUrl`"
    /// for `AsyncImage`. The parameter keeps its Kotlin name: it must come from a thumbnail-context
    /// field (`CourseSummary.thumbnailMediaId`), never from `Lesson.videoMediaId`.
    func thumbnailURLString(thumbnailMediaId: String) -> String {
        sdk.media.resolveThumbnailUrl.invoke(thumbnailMediaId: thumbnailMediaId)
    }

    // MARK: - progress

    /// A non-enrolled caller surfaces as an ordinary `FORBIDDEN_NOT_ENROLLED` `MentoraError` --
    /// a typed, distinguishable failure, forwarded unchanged.
    func courseProgress(courseId: String) async throws -> CourseProgress {
        try ApiResultBridge.unwrap(try await sdk.progress.getCourseProgress.invoke(courseId: courseId))
    }

    /// Returns the server-authoritative progress AND the resolved auto-advance target together, so
    /// iOS can never auto-advance to a different lesson than Android from the same data. Never
    /// recompute either side in Swift (A4). Idempotent server-side -- no Swift-side guard needed.
    func completeLesson(courseId: String, lessonId: String) async throws -> LessonCompletionOutcome {
        try ApiResultBridge.unwrap(
            try await sdk.progress.completeLesson.invoke(courseId: courseId, lessonId: lessonId)
        )
    }

    /// `LessonProgressTarget` is a Kotlin sealed CLASS (not interface) -- no `any` needed, unlike
    /// `AuthState`. Screens branch on it with SKIE's `onEnum(of:)`; the bridge does not re-model it.
    func resumeTarget(courseId: String) async throws -> LessonProgressTarget {
        try ApiResultBridge.unwrap(try await sdk.progress.resumeCourse.invoke(courseId: courseId))
    }

    /// nil means the shared 5-second throttle dropped this heartbeat -- expected, never an error,
    /// and never retried or queued on the Swift side (F6: that throttle is the ONE mechanism, and it
    /// is per-`MentoraSdk`-instance, which is why A3's single-instance rule is load-bearing).
    /// `Int32(clamping:)` rather than `Int32(_:)`: the bridge must never trap (I4).
    @discardableResult
    func reportPlaybackPosition(
        courseId: String,
        lessonId: String,
        positionSeconds: Int
    ) async throws -> CourseProgress? {
        try ApiResultBridge.unwrapOptional(
            try await sdk.progress.reportPlaybackPosition.invoke(
                courseId: courseId,
                lessonId: lessonId,
                positionSeconds: Int32(clamping: positionSeconds)
            )
        )
    }

    // MARK: - quiz

    /// Returns the Kotlin sealed `QuizLookupResult` unchanged: `NoQuiz` is a LEGITIMATE state folded
    /// into `Success` by `GetQuizUseCase`, never an error code to branch on. Screens branch with
    /// SKIE's `onEnum(of:)`; the bridge deliberately performs no translation (its stated contract).
    func quiz(courseId: String) async throws -> QuizLookupResult {
        try ApiResultBridge.unwrap(try await sdk.quiz.getQuiz.invoke(courseId: courseId))
    }

    /// `quiz` is the already-fetched `Quiz` the student answered -- `SubmitQuizUseCase` deliberately
    /// does not re-fetch it. Answer-completeness validation and grading are both Kotlin's; never
    /// score, grade, or pre-validate locally in Swift (A4).
    func submitQuiz(courseId: String, quiz: Quiz, answers: [QuizAnswer]) async throws -> QuizAttemptResult {
        try ApiResultBridge.unwrap(
            try await sdk.quiz.submitQuiz.invoke(courseId: courseId, quiz: quiz, answers: answers)
        )
    }

    /// Same sealed-state convention as `quiz(courseId:)`: `NoAttemptYet` is a success, not an error.
    func latestAttempt(courseId: String) async throws -> LatestAttemptLookupResult {
        try ApiResultBridge.unwrap(try await sdk.quiz.getLatestAttempt.invoke(courseId: courseId))
    }

    // MARK: - certificates

    /// Read-only by design -- there is no issuance action on this façade (issuance is a server-side
    /// side effect of completing a lesson). Do not add one.
    func certificates(cursor: String? = nil, limit: Int? = nil) async throws -> Page<CertificateSummary> {
        try ApiResultBridge.unwrapPage(
            try await sdk.certificates.listCertificates.invoke(cursor: cursor, limit: ApiResultBridge.boxedInt(limit))
        )
    }

    /// Unlike the quiz façade, a 404 here is a genuine error (`CERTIFICATE_NOT_FOUND`), never folded
    /// into a sealed "empty state" -- forwarded unchanged as a `MentoraError`.
    func certificate(id: String) async throws -> CertificateDetail {
        try ApiResultBridge.unwrap(try await sdk.certificates.getCertificate.invoke(id: id))
    }

    // MARK: - aiTutor
    //
    // Phase 6 stays out of scope. This method exposes an AI Tutor use case `shared` has had since
    // Phase 3 Task 14 EXACTLY as it already exists -- no new AI behavior, no prompt, no model
    // choice. This is a mechanical bridge, nothing more. `aiStream(...)` (the Flow-returning
    // send-message use case) is deferred to a follow-up commit -- see DECISIONS_LOG D116 for why.

    func aiConversation(cursor: String? = nil, limit: Int? = nil) async throws -> AiConversation {
        try ApiResultBridge.unwrap(
            try await sdk.aiTutor.getConversation.invoke(cursor: cursor, limit: ApiResultBridge.boxedInt(limit))
        )
    }

    // MARK: - flow/state reads (formerly `FlowBridge.swift`, folded in per D112 Fix 6)
    //
    // Auth-state + locale state reads. The AI-stream Flow adapter (`aiStream(content:...)`, § 7(c))
    // is deferred to a follow-up commit -- see DECISIONS_LOG D116.

    /// System Design § 2 item 3 / § 7(c). PROVEN real: `SkieSwiftStateFlow<any AuthState>` is a
    /// genuine `AsyncSequence` -- confirmed by a real CI compiler error and already consumed with
    /// `for await` by `SessionController` in CI-green code. `AuthState` is a sealed Kotlin
    /// interface/class hierarchy, hence `any`.
    func authStates() -> SkieSwiftStateFlow<any AuthState> {
        sdk.auth.observeAuthState.invoke()
    }

    /// Synchronous current-value read -- `SkieSwiftStateFlow` exposes `final public var value: T { get }`
    /// per the real captured `.swiftinterface`. Verify at CI; if it fails to compile, delete this
    /// method -- nothing in this slice depends on it existing.
    func currentAuthState() -> any AuthState {
        sdk.auth.observeAuthState.invoke().value
    }

    /// `AppLocale` is a Kotlin `enum class` -> a genuine Obj-C class, so no `any` needed here.
    func localeChanges() -> SkieSwiftStateFlow<AppLocale> {
        sdk.user.observeLocale.invoke()
    }

    func currentLocale() -> AppLocale {
        sdk.user.observeLocale.invoke().value
    }
}

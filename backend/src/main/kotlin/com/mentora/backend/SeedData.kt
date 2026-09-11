package com.mentora.backend

import com.mentora.backend.auth.authModule
import com.mentora.backend.auth.repository.AuthRepository
import com.mentora.backend.auth.repository.ensureAuthIndexes
import com.mentora.backend.auth.service.AuthService
import com.mentora.backend.auth.service.RegisterRequest
import com.mentora.backend.categories.categoriesModule
import com.mentora.backend.categories.repository.CategoryDocument
import com.mentora.backend.categories.repository.ensureCategoriesIndexes
import com.mentora.backend.categories.service.CategoryNameRequest
import com.mentora.backend.categories.service.CategoryService
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Role
import com.mentora.backend.config.AppConfig
import com.mentora.backend.courses.coursesModule
import com.mentora.backend.courses.repository.CourseDocument
import com.mentora.backend.courses.repository.ensureCoursesIndexes
import com.mentora.backend.courses.service.CourseResponse
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.courses.service.CourseTranslationDto
import com.mentora.backend.courses.service.CreateCourseRequest
import com.mentora.backend.courses.service.CreateLessonRequest
import com.mentora.backend.courses.service.PriceDisplayDto
import com.mentora.backend.courses.service.ResourceDto
import com.mentora.backend.courses.service.SectionTitleRequest
import com.mentora.backend.courses.service.UpdateCourseRequest
import com.mentora.backend.courses.service.UpdateLessonRequest
import com.mentora.backend.enrollment.enrollmentModule
import com.mentora.backend.learningpaths.repository.LearningPathDocument
import com.mentora.backend.learningpaths.repository.ensureLearningPathIndexes
import com.mentora.backend.media.mediaModule
import com.mentora.backend.media.repository.MediaDocument
import com.mentora.backend.media.repository.ensureMediaIndexes
import com.mentora.backend.media.service.MediaService
import com.mentora.backend.media.service.MediaUpload
import com.mentora.backend.plugins.configKoinModule
import com.mentora.backend.plugins.databaseKoinModule
import com.mentora.backend.progress.progressModule
import com.mentora.backend.quiz.quizModule
import com.mentora.backend.quiz.repository.QuizDocument
import com.mentora.backend.quiz.repository.ensureQuizIndexes
import com.mentora.backend.quiz.service.EditorOptionRequest
import com.mentora.backend.quiz.service.EditorQuestionRequest
import com.mentora.backend.quiz.service.PutQuizRequest
import com.mentora.backend.quiz.service.QuizService
import com.mentora.backend.users.repository.UserDocument
import com.mentora.backend.users.usersModule
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.bson.Document
import org.bson.types.ObjectId
import org.koin.core.Koin
import org.koin.core.context.startKoin

private const val DEMO_PASSWORD = "MentoraDemo1"
private const val PATH_TITLE = "Modern Backend Developer Path"

private data class AccountSeed(val email: String, val name: String, val role: Role)
internal data class LessonSeed(
    val title: String,
    val description: String,
    /** Classpath resource for this lesson's own small, real, unique DEMO video — see [addLesson].
     * Every lesson gets its own distinct clip (never shared with another lesson), generated via
     * `tools/seed-media/generate-lesson-videos.js` and committed under
     * `backend/src/main/resources/seed-media/lessons/`. */
    val videoResource: String,
    /** The clip's own real, true duration in seconds (from the generator's ffprobe manifest) —
     * never a fabricated per-lesson estimate. See [addLesson]'s doc comment. */
    val videoDurationSeconds: Int,
    val resources: List<ResourceDto> = emptyList(),
)
internal data class SectionSeed(val title: String, val lessons: List<LessonSeed>)
private data class CourseSection(val courseId: String, val sectionId: String)
internal data class CourseSeed(
    val title: String,
    val description: String,
    val category: String,
    val instructorEmail: String,
    val level: String,
    val language: String,
    val price: Int,
    val sections: List<SectionSeed> = emptyList(),
    /** Localized title/description for other locales — see the Course Localized Metadata ticket
     * (D57). Keyed by locale ("en"/"ar"), never the course's own `language` (that's already
     * `title`/`description` above). Empty for every course except the seeded showcase example. */
    val translations: Map<String, CourseTranslationDto> = emptyMap(),
) {
    val published: Boolean get() = sections.isNotEmpty()
}

private data class SeedServices(
    val auth: AuthService,
    val authRepository: AuthRepository,
    val categories: CategoryService,
    val courses: CourseService,
    val media: MediaService,
    val quiz: QuizService,
    val database: MongoDatabase,
)

private data class SeedSummary(
    var accounts: Int = 0,
    var categories: Int = 0,
    var courses: Int = 0,
    var quizzes: Int = 0,
    var learningPaths: Int = 0,
    var curriculumUpgrades: Int = 0,
    var artworkUpgrades: Int = 0,
    var uniqueVideoUpgrades: Int = 0,
)

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) = runBlocking {
    val config = AppConfig.load()
    val koinApplication = startKoin {
        modules(
            configKoinModule(config), databaseKoinModule, authModule, usersModule, categoriesModule,
            coursesModule, enrollmentModule, progressModule, quizModule, mediaModule,
        )
    }
    val mongoClient = koinApplication.koin.get<MongoClient>()
    try {
        seedDemoData(services(koinApplication.koin))
    } finally {
        mongoClient.close()
        koinApplication.close()
    }
}

private fun services(koin: Koin) = SeedServices(
    auth = koin.get(),
    authRepository = koin.get(),
    categories = koin.get(),
    courses = koin.get(),
    media = koin.get(),
    quiz = koin.get(),
    database = koin.get(),
)

private suspend fun seedDemoData(services: SeedServices) {
    ensureIndexes(services.database)
    if (allSeedDataExists(services.database)) {
        println("All demo seed data already exists; no changes made.")
        printCredentials()
        return
    }

    val summary = SeedSummary()
    val principals = seedAccounts(services, summary)
    val categoryIds = seedCategories(services, summary)
    val courseIds = seedCourses(services, principals, categoryIds, summary)
    seedQuiz(services, principals.getValue("instructor1@mentora.dev"), courseIds.getValue(QUIZ_COURSE), summary)
    seedLearningPath(services.database, courseIds, summary)
    ensureCurriculum(services, principals, courseIds, summary)
    ensureUniqueLessonVideos(services, principals, courseIds, summary)
    ensureCourseArtwork(services, principals, courseIds, summary)
    println("Demo seed complete: ${summary.accounts} accounts, ${summary.categories} categories, " +
        "${summary.courses} courses, ${summary.quizzes} quizzes, ${summary.learningPaths} learning paths, " +
        "${summary.curriculumUpgrades} course curriculum upgrade(s), ${summary.uniqueVideoUpgrades} unique " +
        "lesson video upgrade(s), ${summary.artworkUpgrades} real course artwork(s) created.")
    printCredentials()
}

private suspend fun ensureIndexes(database: MongoDatabase) {
    ensureAuthIndexes(database)
    ensureCategoriesIndexes(database)
    ensureCoursesIndexes(database)
    ensureQuizIndexes(database)
    ensureLearningPathIndexes(database)
    ensureMediaIndexes(database)
}

private suspend fun allSeedDataExists(database: MongoDatabase): Boolean {
    val users = database.getCollection<UserDocument>("users")
    val categories = database.getCollection<CategoryDocument>("categories")
    val courses = database.getCollection<CourseDocument>("courses")
    val paths = database.getCollection<LearningPathDocument>("learningPaths")
    if (ACCOUNTS.any { users.find(eq("email", it.email)).firstOrNull()?.role != it.role }) return false
    if (CATEGORIES.any { categories.find(eq("name", it)).firstOrNull() == null }) return false
    if (COURSES.any { courses.find(eq("title", it.title)).firstOrNull() == null }) return false
    // Existence alone isn't enough for a course whose seed spec carries translations added after
    // it was first seeded (D57) — re-run seedCourses's update-if-existing branch until they land.
    if (COURSES.any { seed ->
            seed.translations.isNotEmpty() &&
                courses.find(eq("title", seed.title)).firstOrNull()?.translations.isNullOrEmpty()
        }
    ) return false
    val quizCourse = courses.find(eq("title", QUIZ_COURSE)).firstOrNull() ?: return false
    val quizExists = database.getCollection<QuizDocument>("quizzes")
        .find(eq("courseId", requireNotNull(quizCourse.id))).firstOrNull() != null
    // Converge every published course's full curriculum (section/lesson titles and ordering) even
    // against a database seeded before this realistic-curriculum expansion existed — see
    // ensureCurriculum. A course whose title exists but whose curriculum still carries the old
    // generic placeholder structure must not be mistaken for "already seeded."
    val curriculumConverged = COURSES.filter { it.published }.all { seed ->
        val course = courses.find(eq("title", seed.title)).firstOrNull() ?: return@all false
        val currentTitles = course.sections.sortedBy { it.order }.flatMap { section ->
            listOf(section.title) + section.lessons.sortedBy { it.order }.map { it.title }
        }
        currentTitles == desiredCurriculumTitles(seed)
    }
    if (!curriculumConverged) return false
    // Converge every lesson's own unique DEMO video even against a database seeded before this
    // upgrade existed (including one this session's own earlier ensureCurriculum run already
    // converged onto a *shared* per-course video) — see ensureUniqueLessonVideos. A lesson whose
    // video byte size doesn't match its own expected resource is either still sharing another
    // lesson's clip or missing one entirely.
    val media = database.getCollection<MediaDocument>("media")
    val uniqueVideosConverged = COURSES.filter { it.published }.all { seed ->
        val course = courses.find(eq("title", seed.title)).firstOrNull() ?: return@all false
        seed.sections.all { sectionSeed ->
            val section = course.sections.firstOrNull { it.title == sectionSeed.title } ?: return@all false
            sectionSeed.lessons.all { lessonSeed ->
                val lesson = section.lessons.firstOrNull { it.title == lessonSeed.title } ?: return@all false
                val expectedSize = classpathResourceSize(lessonSeed.videoResource)
                val actualSize = lesson.videoMediaId?.let { media.find(eq("_id", it)).firstOrNull()?.sizeBytes }
                actualSize == expectedSize
            }
        }
    }
    if (!uniqueVideosConverged) return false
    // Converge every course's real-artwork demo thumbnail even against a database seeded before
    // this upgrade existed — see ensureCourseArtwork.
    val artworkConverged = COURSE_ARTWORK_SEEDS.all { seed ->
        val thumbnailId = courses.find(eq("title", seed.courseTitle)).firstOrNull()?.thumbnailMediaId ?: return@all false
        (media.find(eq("_id", thumbnailId)).firstOrNull()?.sizeBytes ?: 0) > REAL_ARTWORK_MIN_BYTES
    }
    if (!artworkConverged) return false
    return quizExists && paths.find(eq("title", PATH_TITLE)).firstOrNull() != null
}

private fun desiredCurriculumTitles(seed: CourseSeed): List<String> =
    seed.sections.flatMap { section -> listOf(section.title) + section.lessons.map { it.title } }

/** Real byte size of a bundled classpath seed-media resource — the convergence marker
 * [ensureUniqueLessonVideos] and `allSeedDataExists` use to detect a lesson still carrying a
 * stale, missing, or shared (non-unique) video. */
private fun classpathResourceSize(resource: String): Long = requireNotNull(
    Thread.currentThread().contextClassLoader.getResourceAsStream(resource)
) { "Missing seed resource: $resource" }.use { it.readBytes() }.size.toLong()

private suspend fun seedAccounts(services: SeedServices, summary: SeedSummary): Map<String, MentoraPrincipal> =
    ACCOUNTS.associate { account ->
        var user = services.authRepository.findUserByEmail(account.email)
        if (user == null) {
            services.auth.register(RegisterRequest(account.email, DEMO_PASSWORD, account.name))
            user = requireNotNull(services.authRepository.findUserByEmail(account.email))
            summary.accounts++
        }
        if (user.role != account.role) {
            services.database.getCollection<Document>("users")
                .updateOne(eq("_id", requireNotNull(user.id)), set("role", account.role.name))
        }
        account.email to MentoraPrincipal(requireNotNull(user.id), account.role)
    }

private suspend fun seedCategories(services: SeedServices, summary: SeedSummary): Map<String, String> {
    val collection = services.database.getCollection<CategoryDocument>("categories")
    return CATEGORIES.associateWith { name ->
        val existing = collection.find(eq("name", name)).firstOrNull()
        if (existing != null) return@associateWith requireNotNull(existing.id).toHexString()
        summary.categories++
        services.categories.create(CategoryNameRequest(name)).id
    }
}

private suspend fun seedCourses(
    services: SeedServices,
    principals: Map<String, MentoraPrincipal>,
    categoryIds: Map<String, String>,
    summary: SeedSummary,
): Map<String, String> {
    val collection = services.database.getCollection<CourseDocument>("courses")
    return COURSES.associate { seed ->
        val existing = collection.find(eq("title", seed.title)).firstOrNull()
        val id = existing?.id?.toHexString() ?: createCourse(
            services, principals.getValue(seed.instructorEmail), categoryIds.getValue(seed.category), seed,
        ).also { summary.courses++ }
        // A course already seeded in an earlier run (before D57's translations field existed)
        // won't have picked up seed.translations from createCourse — converge it here too, so
        // re-running seedDemoData against an existing dev database stays idempotent.
        if (existing != null && seed.translations.isNotEmpty()) {
            services.courses.update(
                principals.getValue(seed.instructorEmail), id, UpdateCourseRequest(translations = seed.translations),
            )
        }
        seed.title to id
    }
}

private suspend fun createCourse(
    services: SeedServices,
    principal: MentoraPrincipal,
    categoryId: String,
    seed: CourseSeed,
): String {
    val course = services.courses.create(principal, CreateCourseRequest(
        seed.title, seed.description, categoryId, seed.level, seed.language,
        PriceDisplayDto(seed.price, "EGP"), translations = seed.translations,
    ))
    val thumbnail = services.media.upload(MediaUpload(
        "courseThumbnail", course.id, "image/jpeg", null, null,
        ByteReadChannel("seed-thumbnail-${seed.title}".encodeToByteArray()),
    ), principal)
    services.courses.update(principal, course.id, UpdateCourseRequest(thumbnailMediaId = thumbnail.mediaId))
    seed.sections.forEach { addSection(services, principal, course.id, it) }
    if (seed.published) services.courses.publish(principal, course.id)
    return course.id
}

private suspend fun addSection(
    services: SeedServices,
    principal: MentoraPrincipal,
    courseId: String,
    seed: SectionSeed,
) {
    val course = services.courses.addSection(principal, courseId, SectionTitleRequest(seed.title))
    val sectionId = course.sections.last().sectionId
    val courseSection = CourseSection(courseId, sectionId)
    seed.lessons.forEach { addLesson(services, principal, courseSection, it) }
}

/** Every seeded lesson gets its own real, small, browser-playable, unique DEMO video — not the
 * historic fake-bytes placeholder every lesson but one used originally, and not the later
 * one-clip-shared-across-a-whole-course design either (see execution/DECISIONS_LOG.md D67/D68).
 * Each [LessonSeed.videoResource] is a distinct, topic-referencing, small generated clip
 * (`tools/seed-media/generate-lesson-videos.js`) — guaranteeing every curriculum row the Course
 * Player shows is genuinely selectable, playable, *and* visibly distinct from every other lesson's
 * video, not just a title change over identical footage. `videoDurationSeconds` is the clip's own
 * real, true duration (never fabricated) — see architecture/MEDIA_ARCHITECTURE.md for the
 * local-filesystem `MediaStorage`/no-binaries-in-Mongo constraint this still honors. */
private suspend fun addLesson(
    services: SeedServices,
    principal: MentoraPrincipal,
    courseSection: CourseSection,
    seed: LessonSeed,
) {
    val course = services.courses.addLesson(
        principal, courseSection.courseId, courseSection.sectionId,
        CreateLessonRequest(seed.title, seed.description, resources = seed.resources),
    )
    val lessonId = course.sections.first { it.sectionId == courseSection.sectionId }.lessons.last().lessonId
    val bytes = requireNotNull(
        Thread.currentThread().contextClassLoader.getResourceAsStream(seed.videoResource)
    ) { "Missing seed resource: ${seed.videoResource}" }.use { it.readBytes() }
    val video = services.media.upload(MediaUpload(
        "lessonVideo", lessonId, "video/mp4", courseSection.courseId,
        seed.videoDurationSeconds.toString(), ByteReadChannel(bytes),
    ), principal)
    services.courses.updateLesson(
        principal, courseSection.courseId, courseSection.sectionId, lessonId,
        UpdateLessonRequest(videoMediaId = video.mediaId),
    )
}

/** Converges every published course's *whole* curriculum (section titles, lesson titles, lesson
 * count/ordering) against its current [CourseSeed] spec — the realistic-curriculum-expansion
 * counterpart to [ensureCourseArtwork]'s thumbnail convergence. Replaces broken/generic placeholder
 * curricula (e.g. a course still carrying the old "Reliability and Evolution" / "Applied workshop"
 * two-lesson placeholder structure) deterministically: every existing section is deleted, then the
 * course is rebuilt section-by-section from [COURSES] — each new lesson getting a real playable
 * video via [addLesson]. Idempotent via a title-list comparison, same pattern
 * `allSeedDataExists`/[desiredCurriculumTitles] use, so a course whose curriculum already matches
 * is left untouched (0 duplicate sections/lessons/media on repeat runs). This is a local
 * demo/development database with no real production users — any pre-existing `progress` rows for a
 * course whose curriculum is replaced necessarily reference lesson ids that no longer exist, so
 * they are cleared alongside the replacement rather than left stale (see
 * execution/DECISIONS_LOG.md for this pass's full account of that tradeoff). */
private suspend fun ensureCurriculum(
    services: SeedServices,
    principals: Map<String, MentoraPrincipal>,
    courseIds: Map<String, String>,
    summary: SeedSummary,
) {
    COURSES.filter { it.published }.forEach { seed ->
        val courseId = courseIds.getValue(seed.title)
        val principal = principals.getValue(seed.instructorEmail)
        val course = services.courses.get(courseId, principal)
        if (currentCurriculumTitles(course) == desiredCurriculumTitles(seed)) return@forEach

        course.sections.forEach { services.courses.deleteSection(principal, courseId, it.sectionId) }
        seed.sections.forEach { addSection(services, principal, courseId, it) }
        services.database.getCollection<Document>("progress").deleteMany(eq("courseId", ObjectId(courseId)))
        summary.curriculumUpgrades++
    }
}

private fun currentCurriculumTitles(course: CourseResponse): List<String> =
    course.sections.sortedBy { it.order }.flatMap { section ->
        listOf(section.title) + section.lessons.sortedBy { it.order }.map { it.title }
    }

/** Converges every lesson's video onto its own unique [LessonSeed.videoResource], non-destructively
 * — unlike [ensureCurriculum], this never deletes/recreates sections or lessons (their titles
 * already match; only the *video* needs to change), so lesson ids and any real per-lesson progress
 * survive this upgrade untouched. Handles two real cases against an already-seeded dev database:
 * (a) a lesson still on the historic shared-per-course video (pre-D68), and (b) a lesson somehow
 * missing a video entirely. Idempotent via a byte-size comparison against the lesson's own expected
 * resource (same convergence pattern `ensureCourseArtwork` uses for thumbnails) — a lesson whose
 * current video already matches its expected resource size is left untouched (0 duplicate uploads
 * on repeat runs). */
private suspend fun ensureUniqueLessonVideos(
    services: SeedServices,
    principals: Map<String, MentoraPrincipal>,
    courseIds: Map<String, String>,
    summary: SeedSummary,
) {
    val media = services.database.getCollection<MediaDocument>("media")
    COURSES.filter { it.published }.forEach { seed ->
        val courseId = courseIds.getValue(seed.title)
        val principal = principals.getValue(seed.instructorEmail)
        val course = services.courses.get(courseId, principal)
        seed.sections.forEach { sectionSeed ->
            val section = course.sections.first { it.title == sectionSeed.title }
            sectionSeed.lessons.forEach lesson@{ lessonSeed ->
                val lesson = section.lessons.first { it.title == lessonSeed.title }
                val expectedSize = classpathResourceSize(lessonSeed.videoResource)
                val actualSize = lesson.videoMediaId?.let {
                    media.find(eq("_id", ObjectId(it))).firstOrNull()?.sizeBytes
                }
                if (actualSize == expectedSize) return@lesson

                val bytes = requireNotNull(
                    Thread.currentThread().contextClassLoader.getResourceAsStream(lessonSeed.videoResource)
                ) { "Missing seed resource: ${lessonSeed.videoResource}" }.use { it.readBytes() }
                val video = services.media.upload(
                    MediaUpload(
                        "lessonVideo", lesson.lessonId, "video/mp4", courseId,
                        lessonSeed.videoDurationSeconds.toString(), ByteReadChannel(bytes),
                    ),
                    principal,
                )
                services.courses.updateLesson(
                    principal, courseId, section.sectionId, lesson.lessonId,
                    UpdateLessonRequest(videoMediaId = video.mediaId),
                )
                summary.uniqueVideoUpgrades++
            }
        }
    }
}

/** Upgrades each [COURSE_ARTWORK_SEEDS] course's thumbnail from the generic fake-bytes placeholder
 * every seeded course still uses (see [createCourse]) to a real, topic-relevant demo image, so
 * courses that share a category no longer render the identical governed motif/gradient artwork
 * everywhere they appear (Landing, Explore, Dashboard, My Learning, Course Details, Course Player,
 * Checkout, Learning Path Details all read the same `thumbnailMediaId` via CourseThumbnail). Courses
 * with no artwork seed keep rendering the governed 5-motif fallback exactly as before — this only
 * replaces the placeholder for the specific real, published courses this ticket targets.
 * Idempotent via the current thumbnail's stored byte size as the convergence marker, same pattern
 * `ensureLessonVideos` uses for lesson titles. */
private suspend fun ensureCourseArtwork(
    services: SeedServices,
    principals: Map<String, MentoraPrincipal>,
    courseIds: Map<String, String>,
    summary: SeedSummary,
) {
    val media = services.database.getCollection<MediaDocument>("media")
    COURSE_ARTWORK_SEEDS.forEach { seed ->
        val courseId = courseIds.getValue(seed.courseTitle)
        val principal = principals.getValue(COURSES.first { it.title == seed.courseTitle }.instructorEmail)
        val course = services.courses.get(courseId, principal)
        val currentSize = course.thumbnailMediaId?.let { media.find(eq("_id", ObjectId(it))).firstOrNull()?.sizeBytes }
        if ((currentSize ?: 0) > REAL_ARTWORK_MIN_BYTES) return@forEach

        val bytes = requireNotNull(
            Thread.currentThread().contextClassLoader.getResourceAsStream(seed.imageResource)
        ) { "Missing seed resource: ${seed.imageResource}" }.use { it.readBytes() }

        val thumbnail = services.media.upload(
            MediaUpload("courseThumbnail", courseId, "image/jpeg", null, null, ByteReadChannel(bytes)),
            principal,
        )
        services.courses.update(principal, courseId, UpdateCourseRequest(thumbnailMediaId = thumbnail.mediaId))
        summary.artworkUpgrades++
    }
}

private suspend fun seedQuiz(
    services: SeedServices,
    principal: MentoraPrincipal,
    courseId: String,
    summary: SeedSummary,
) {
    if (services.quiz.hasQuiz(ObjectId(courseId))) return
    services.quiz.replace(courseId, principal, PutQuizRequest(listOf(
        question("Which HTTP method is conventionally idempotent?", "PUT", "POST", 0),
        question("What does a database index primarily improve?", "Query lookup speed", "Password strength", 1),
        question("Which status code means Not Found?", "404", "201", 2),
    )))
    summary.quizzes++
}

private fun question(prompt: String, correct: String, incorrect: String, order: Int) = EditorQuestionRequest(
    prompt = prompt,
    order = order,
    options = listOf(EditorOptionRequest(text = correct, isCorrect = true),
        EditorOptionRequest(text = incorrect, isCorrect = false)),
)

private suspend fun seedLearningPath(
    database: MongoDatabase,
    courseIds: Map<String, String>,
    summary: SeedSummary,
) {
    val paths = database.getCollection<LearningPathDocument>("learningPaths")
    if (paths.find(eq("title", PATH_TITLE)).firstOrNull() != null) return
    paths.insertOne(LearningPathDocument(
        title = PATH_TITLE,
        description = "Build practical backend skills from API fundamentals through reliable data persistence.",
        courseIds = PATH_COURSES.map { ObjectId(courseIds.getValue(it)) },
        createdAt = Clock.System.now(),
    ))
    summary.learningPaths++
}

private fun printCredentials() {
    println("Demo accounts (password for all: $DEMO_PASSWORD):")
    ACCOUNTS.forEach { println("  ${it.role}: ${it.email} / $DEMO_PASSWORD") }
}

private val ACCOUNTS = listOf(
    AccountSeed("admin@mentora.dev", "Nadia Hassan", Role.admin),
    AccountSeed("instructor1@mentora.dev", "Omar Khalil", Role.instructor),
    AccountSeed("instructor2@mentora.dev", "Mariam Adel", Role.instructor),
    AccountSeed("student1@mentora.dev", "Youssef Ali", Role.student),
    AccountSeed("student2@mentora.dev", "Salma Mostafa", Role.student),
    AccountSeed("student3@mentora.dev", "Karim Samir", Role.student),
)

private val CATEGORIES = listOf("Software Development", "Data & Analytics", "Design", "Business Skills")
private const val QUIZ_COURSE = "Building Reliable REST APIs"
private const val MONGODB_COURSE = "Practical MongoDB for Application Developers"
private const val KOTLIN_COURSE = "Kotlin Coroutines in Practice"
private const val UX_COURSE = "أساسيات تصميم تجربة المستخدم"

/** Byte-size floor distinguishing a real seeded image from the few-dozen-byte
 * `"seed-thumbnail-<title>"` placeholder every other seeded course still uses — see
 * [ensureCourseArtwork]. */
private const val REAL_ARTWORK_MIN_BYTES = 10_000L

/** One real, topic-specific artwork image per course targeted by the course-artwork-identity
 * ticket — see [ensureCourseArtwork]. Purely visual (gradient + icon, no embedded text), so this
 * carries no content-language claim and needs no translation/RTL handling. Every other seeded
 * course (including drafts) keeps rendering the governed 5-motif fallback unchanged. */
internal data class CourseArtworkSeed(val courseTitle: String, val imageResource: String)

internal val COURSE_ARTWORK_SEEDS = listOf(
    CourseArtworkSeed(QUIZ_COURSE, "seed-media/rest-api-artwork.jpg"),
    CourseArtworkSeed(MONGODB_COURSE, "seed-media/mongodb-artwork.jpg"),
    CourseArtworkSeed(KOTLIN_COURSE, "seed-media/kotlin-coroutines-artwork.jpg"),
    CourseArtworkSeed(UX_COURSE, "seed-media/ux-design-artwork.jpg"),
)

private val PATH_COURSES = listOf(QUIZ_COURSE, MONGODB_COURSE, KOTLIN_COURSE)

internal val COURSES = listOf(
    CourseSeed(
        QUIZ_COURSE, "Design, validate, and evolve production-ready HTTP APIs with clear contracts.",
        "Software Development", "instructor1@mentora.dev", "intermediate", "en", 850,
        sections = listOf(
            SectionSeed("API Design Foundations", listOf(
                LessonSeed(
                    "REST API Reliability Fundamentals",
                    "Learn the core principles behind reliable REST APIs, including resource design, HTTP " +
                        "semantics, validation, status codes, and predictable error handling.",
                    videoResource = "seed-media/lessons/rest-api/01-rest-api-reliability-fundamentals.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "Resources and HTTP Methods",
                    "Model your API around resources and choose the HTTP method — GET, POST, PUT, PATCH, " +
                        "DELETE — that matches the operation you're actually performing.",
                    videoResource = "seed-media/lessons/rest-api/02-resources-and-http-methods.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "Request and Response Design",
                    "Shape predictable JSON payloads and headers so every endpoint in your API feels " +
                        "consistent to the client that calls it.",
                    videoResource = "seed-media/lessons/rest-api/03-request-and-response-design.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "HTTP Status Codes",
                    "Use status codes precisely — 2xx success, 4xx client error, 5xx server error — so " +
                        "callers can react correctly without parsing error text.",
                    videoResource = "seed-media/lessons/rest-api/04-http-status-codes.mp4", videoDurationSeconds = 19,
                    resources = listOf(ResourceDto("MDN: HTTP Response Status Codes", "https://developer.mozilla.org/en-US/docs/Web/HTTP/Status")),
                ),
            )),
            SectionSeed("Validation and Error Handling", listOf(
                LessonSeed(
                    "Request Validation",
                    "Validate incoming requests at the boundary and reject bad input early, before it " +
                        "reaches your business logic.",
                    videoResource = "seed-media/lessons/rest-api/05-request-validation.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "Consistent Error Responses",
                    "Design one error response shape used everywhere in the API, so clients can handle " +
                        "failures with a single code path.",
                    videoResource = "seed-media/lessons/rest-api/06-consistent-error-responses.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "API Contracts and DTOs",
                    "Separate your public API contract from internal domain models using DTOs, so internal " +
                        "refactors don't break every client.",
                    videoResource = "seed-media/lessons/rest-api/07-api-contracts-and-dtos.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "Idempotency",
                    "Make retried requests safe by designing idempotent operations, so a flaky network never " +
                        "causes a duplicate side effect.",
                    videoResource = "seed-media/lessons/rest-api/08-idempotency.mp4", videoDurationSeconds = 23,
                    resources = listOf(ResourceDto("MDN: HTTP Request Methods", "https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods")),
                ),
            )),
            SectionSeed("Production-Ready APIs", listOf(
                LessonSeed(
                    "Pagination and Filtering",
                    "Return large collections in manageable pages and let clients filter results, keeping " +
                        "responses fast and predictable.",
                    videoResource = "seed-media/lessons/rest-api/09-pagination-and-filtering.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "Authentication and Authorization Concepts",
                    "Understand the difference between authenticating who a caller is and authorizing what " +
                        "they're allowed to do.",
                    videoResource = "seed-media/lessons/rest-api/10-authentication-and-authorization-concepts.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "Versioning and API Evolution",
                    "Evolve your API without breaking existing clients, using deliberate versioning strategies.",
                    videoResource = "seed-media/lessons/rest-api/11-versioning-and-api-evolution.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "Reliability Best Practices",
                    "Bring validation, error handling, and idempotency together into a checklist for shipping " +
                        "APIs that hold up in production.",
                    videoResource = "seed-media/lessons/rest-api/12-reliability-best-practices.mp4", videoDurationSeconds = 27,
                    resources = listOf(ResourceDto("Microsoft REST API Guidelines", "https://github.com/microsoft/api-guidelines")),
                ),
            )),
        ),
    ),
    CourseSeed(
        MONGODB_COURSE, "Model documents and build efficient queries for modern applications.",
        "Data & Analytics", "instructor1@mentora.dev", "beginner", "en", 700,
        sections = listOf(
            SectionSeed("Document Modeling", listOf(
                LessonSeed(
                    "MongoDB Fundamentals for Application Developers",
                    "Learn how to model documents, perform CRUD operations, design efficient schemas, and use " +
                        "indexes and queries to build fast, reliable applications with MongoDB.",
                    videoResource = "seed-media/lessons/mongodb/01-mongodb-fundamentals-for-application-developers.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "Documents and Collections",
                    "Understand how MongoDB stores data as flexible JSON-like documents grouped into " +
                        "collections, and how that differs from relational tables.",
                    videoResource = "seed-media/lessons/mongodb/02-documents-and-collections.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "MongoDB Data Types",
                    "Work confidently with MongoDB's core BSON data types, including ObjectId, dates, arrays, " +
                        "and embedded documents.",
                    videoResource = "seed-media/lessons/mongodb/03-mongodb-data-types.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "CRUD Fundamentals",
                    "Perform create, read, update, and delete operations using the MongoDB driver's core " +
                        "methods.",
                    videoResource = "seed-media/lessons/mongodb/04-crud-fundamentals.mp4", videoDurationSeconds = 19,
                    resources = listOf(ResourceDto("MongoDB Manual: CRUD Operations", "https://www.mongodb.com/docs/manual/crud/")),
                ),
            )),
            SectionSeed("Querying and Modeling", listOf(
                LessonSeed(
                    "Query Operators",
                    "Use comparison, logical, and array query operators to find exactly the documents you need.",
                    videoResource = "seed-media/lessons/mongodb/05-query-operators.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "Schema Design",
                    "Design a document schema around how your application actually reads and writes data, not " +
                        "around normalization habits carried over from SQL.",
                    videoResource = "seed-media/lessons/mongodb/06-schema-design.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "Embedded vs Referenced Documents",
                    "Decide when to embed related data inside a single document versus referencing it in " +
                        "another collection.",
                    videoResource = "seed-media/lessons/mongodb/07-embedded-vs-referenced-documents.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "Index Fundamentals",
                    "Speed up queries with indexes, and understand the trade-off between faster reads and " +
                        "slower writes.",
                    videoResource = "seed-media/lessons/mongodb/08-index-fundamentals.mp4", videoDurationSeconds = 23,
                    resources = listOf(ResourceDto("MongoDB Manual: Indexes", "https://www.mongodb.com/docs/manual/indexes/")),
                ),
            )),
            SectionSeed("Application Development", listOf(
                LessonSeed(
                    "Aggregation Basics",
                    "Build aggregation pipelines to transform, filter, and summarize data directly inside " +
                        "MongoDB.",
                    videoResource = "seed-media/lessons/mongodb/09-aggregation-basics.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "Pagination and Filtering",
                    "Implement cursor-based pagination and filtering for large MongoDB collections in a real " +
                        "application.",
                    videoResource = "seed-media/lessons/mongodb/10-pagination-and-filtering.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "Performance Considerations",
                    "Recognize common MongoDB performance pitfalls and how indexes, schema design, and query " +
                        "shape affect them.",
                    videoResource = "seed-media/lessons/mongodb/11-performance-considerations.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "Practical Application Patterns",
                    "Apply everything covered in this course to real patterns for building a MongoDB-backed " +
                        "application.",
                    videoResource = "seed-media/lessons/mongodb/12-practical-application-patterns.mp4", videoDurationSeconds = 27,
                    resources = listOf(ResourceDto("MongoDB Manual: Aggregation Pipeline", "https://www.mongodb.com/docs/manual/core/aggregation-pipeline/")),
                ),
            )),
        ),
    ),
    CourseSeed(
        KOTLIN_COURSE, "Write responsive concurrent Kotlin programs using structured concurrency.",
        "Software Development", "instructor2@mentora.dev", "advanced", "en", 950,
        sections = listOf(
            SectionSeed("Coroutine Fundamentals", listOf(
                LessonSeed(
                    "Kotlin Coroutines Fundamentals",
                    "Learn the core building blocks of Kotlin coroutines, including suspend functions, " +
                        "coroutine scopes, dispatchers, and structured concurrency for writing responsive, " +
                        "reliable concurrent code.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/01-kotlin-coroutines-fundamentals.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "Suspend Functions",
                    "Write suspend functions that pause without blocking a thread, and understand how the " +
                        "compiler transforms them.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/02-suspend-functions.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "Coroutine Builders",
                    "Launch coroutines with launch, async, and runBlocking, and know when to reach for each " +
                        "one.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/03-coroutine-builders.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "CoroutineScope",
                    "Tie a coroutine's lifetime to a well-defined scope so it's cancelled automatically when " +
                        "that scope ends.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/04-coroutinescope.mp4", videoDurationSeconds = 19,
                    resources = listOf(ResourceDto("Kotlin Docs: Coroutines Basics", "https://kotlinlang.org/docs/coroutines-basics.html")),
                ),
            )),
            SectionSeed("Structured Concurrency", listOf(
                LessonSeed(
                    "Jobs and Cancellation",
                    "Use Job to track a coroutine's lifecycle and cancel it cooperatively without leaking " +
                        "resources.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/05-jobs-and-cancellation.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "Dispatchers and Context",
                    "Choose the right dispatcher — Main, IO, Default — to run coroutines on the thread pool " +
                        "suited to the work.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/06-dispatchers-and-context.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "Exception Handling",
                    "Handle exceptions thrown inside coroutines correctly, including the real differences " +
                        "between launch and async.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/07-exception-handling.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "Structured Concurrency",
                    "Keep concurrent work organized and safe by nesting coroutines inside scopes that " +
                        "guarantee they complete or are cancelled together.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/08-structured-concurrency.mp4", videoDurationSeconds = 23,
                    resources = listOf(ResourceDto("Kotlin Docs: Cancellation and Timeouts", "https://kotlinlang.org/docs/cancellation-and-timeouts.html")),
                ),
            )),
            SectionSeed("Practical Coroutines", listOf(
                LessonSeed(
                    "async and await",
                    "Run multiple suspend functions concurrently with async and combine their results with " +
                        "await.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/09-async-and-await.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "Flow Fundamentals",
                    "Model a stream of asynchronously computed values with Kotlin Flow.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/10-flow-fundamentals.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "Combining Asynchronous Work",
                    "Combine multiple coroutines and flows together to build real asynchronous pipelines.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/11-combining-asynchronous-work.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "Real Application Patterns",
                    "Apply coroutines to real application patterns like network calls, repositories, and UI " +
                        "state updates.",
                    videoResource = "seed-media/lessons/kotlin-coroutines/12-real-application-patterns.mp4", videoDurationSeconds = 27,
                    resources = listOf(ResourceDto("Kotlin Docs: Flow", "https://kotlinlang.org/docs/flow.html")),
                ),
            )),
        ),
    ),
    CourseSeed(
        UX_COURSE, "تعلّم مبادئ البحث والتخطيط لبناء تجارب رقمية واضحة وسهلة الاستخدام.",
        "Design", "instructor2@mentora.dev", "beginner", "ar", 600,
        sections = listOf(
            SectionSeed("فهم احتياجات المستخدم", listOf(
                LessonSeed(
                    "أساسيات تجربة المستخدم",
                    "تعلّم المبادئ الأساسية لتصميم تجربة المستخدم، بما في ذلك أبحاث المستخدمين، والنماذج الأولية، " +
                        "وقابلية الاستخدام، ومسارات المستخدم، لبناء تجارب رقمية واضحة وفعالة.",
                    videoResource = "seed-media/lessons/ux-design/01-ux-design-fundamentals.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "مقدمة في تجربة المستخدم",
                    "تعرّف على مفهوم تجربة المستخدم ولماذا تُعد جزءًا أساسيًا من نجاح أي منتج رقمي.",
                    videoResource = "seed-media/lessons/ux-design/02-introduction-to-ux.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "أبحاث المستخدمين الأساسية",
                    "تعلّم كيفية جمع رؤى حقيقية عن المستخدمين من خلال المقابلات والملاحظة قبل البدء بالتصميم.",
                    videoResource = "seed-media/lessons/ux-design/03-user-research-basics.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "تحليل احتياجات المستخدم",
                    "حوّل ملاحظات البحث إلى احتياجات واضحة يمكن تصميم حلول فعلية بناءً عليها.",
                    videoResource = "seed-media/lessons/ux-design/04-analyzing-user-needs.mp4", videoDurationSeconds = 19,
                    resources = listOf(ResourceDto("مبادئ Nielsen Norman العشرة لقابلية الاستخدام", "https://www.nngroup.com/articles/ten-usability-heuristics/")),
                ),
            )),
            SectionSeed("من الفكرة إلى النموذج الأولي", listOf(
                LessonSeed(
                    "مسارات المستخدم",
                    "ارسم الخطوات التي يتبعها المستخدم لإنجاز مهمة داخل المنتج، من البداية حتى الهدف النهائي.",
                    videoResource = "seed-media/lessons/ux-design/05-user-flows.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "هندسة المعلومات",
                    "نظّم المحتوى والتنقل بطريقة منطقية تسهّل على المستخدم إيجاد ما يبحث عنه.",
                    videoResource = "seed-media/lessons/ux-design/06-information-architecture.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "التصميم السلكي",
                    "ابنِ مخططات سلكية بسيطة لتحديد بنية الشاشة قبل الانتقال إلى التصميم المرئي.",
                    videoResource = "seed-media/lessons/ux-design/07-wireframing.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "تصميم التفاعل",
                    "صمّم كيف يتفاعل المستخدم مع عناصر الواجهة، من الأزرار إلى الانتقالات.",
                    videoResource = "seed-media/lessons/ux-design/08-interaction-design.mp4", videoDurationSeconds = 23,
                    resources = listOf(ResourceDto("دليل Material Design للتفاعل", "https://m3.material.io/")),
                ),
            )),
            SectionSeed("التقييم والتحسين", listOf(
                LessonSeed(
                    "النماذج الأولية",
                    "حوّل التصميمات الثابتة إلى نماذج أولية تفاعلية يمكن اختبارها مع مستخدمين حقيقيين.",
                    videoResource = "seed-media/lessons/ux-design/09-prototyping.mp4", videoDurationSeconds = 27,
                ),
                LessonSeed(
                    "اختبار قابلية الاستخدام",
                    "راقب مستخدمين حقيقيين وهم يستخدمون تصميمك لاكتشاف نقاط الالتباس قبل الإطلاق.",
                    videoResource = "seed-media/lessons/ux-design/10-usability-testing.mp4", videoDurationSeconds = 19,
                ),
                LessonSeed(
                    "التكرار بناءً على الملاحظات",
                    "استخدم ملاحظات الاختبار لتحسين التصميم عبر دورات تكرار سريعة.",
                    videoResource = "seed-media/lessons/ux-design/11-iterating-on-feedback.mp4", videoDurationSeconds = 23,
                ),
                LessonSeed(
                    "تسليم تصميم تجربة المستخدم",
                    "جهّز الملفات والمواصفات التي يحتاجها المطورون لتنفيذ التصميم بدقة.",
                    videoResource = "seed-media/lessons/ux-design/12-ux-design-handoff.mp4", videoDurationSeconds = 27,
                    resources = listOf(ResourceDto("دليل التسليم بين المصممين والمطورين", "https://www.figma.com/best-practices/guide-to-developer-handoff/")),
                ),
            )),
        ),
        translations = mapOf(
            "en" to CourseTranslationDto(
                "User Experience Design Fundamentals",
                "Learn the principles of research and planning to build clear, usable digital experiences.",
            ),
        ),
    ),
    CourseSeed(
        "تحليل البيانات لاتخاذ القرارات", "حوّل بيانات العمل إلى مؤشرات واضحة تدعم القرارات اليومية.",
        "Data & Analytics", "instructor1@mentora.dev", "intermediate", "ar", 750,
    ),
    CourseSeed(
        "Product Strategy for Growing Teams", "Connect customer needs, measurable outcomes, and a focused product roadmap.",
        "Business Skills", "instructor2@mentora.dev", "intermediate", "en", 800,
    ),
)

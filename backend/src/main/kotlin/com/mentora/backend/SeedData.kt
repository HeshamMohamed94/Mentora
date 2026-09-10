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
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.courses.service.CourseTranslationDto
import com.mentora.backend.courses.service.CreateCourseRequest
import com.mentora.backend.courses.service.CreateLessonRequest
import com.mentora.backend.courses.service.PriceDisplayDto
import com.mentora.backend.courses.service.SectionTitleRequest
import com.mentora.backend.courses.service.UpdateCourseRequest
import com.mentora.backend.courses.service.UpdateLessonRequest
import com.mentora.backend.enrollment.enrollmentModule
import com.mentora.backend.learningpaths.repository.LearningPathDocument
import com.mentora.backend.learningpaths.repository.ensureLearningPathIndexes
import com.mentora.backend.media.mediaModule
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
private data class LessonSeed(val title: String, val description: String)
private data class SectionSeed(val title: String, val lessons: List<LessonSeed>)
private data class CourseSection(val courseId: String, val sectionId: String)
private data class CourseSeed(
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
    println("Demo seed complete: ${summary.accounts} accounts, ${summary.categories} categories, " +
        "${summary.courses} courses, ${summary.quizzes} quizzes, ${summary.learningPaths} learning paths created.")
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
    return quizExists && paths.find(eq("title", PATH_TITLE)).firstOrNull() != null
}

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

private suspend fun addLesson(
    services: SeedServices,
    principal: MentoraPrincipal,
    courseSection: CourseSection,
    seed: LessonSeed,
) {
    val course = services.courses.addLesson(
        principal, courseSection.courseId, courseSection.sectionId, CreateLessonRequest(seed.title, seed.description),
    )
    val lessonId = course.sections.first { it.sectionId == courseSection.sectionId }.lessons.last().lessonId
    val video = services.media.upload(MediaUpload(
        "lessonVideo", lessonId, "video/mp4", courseSection.courseId, "300",
        ByteReadChannel("seed-video-${seed.title}".encodeToByteArray()),
    ), principal)
    services.courses.updateLesson(
        principal, courseSection.courseId, courseSection.sectionId, lessonId,
        UpdateLessonRequest(videoMediaId = video.mediaId),
    )
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

private fun sections(first: String, second: String) = listOf(
    SectionSeed(first, listOf(LessonSeed("Core concepts", "A guided introduction with practical examples."))),
    SectionSeed(second, listOf(LessonSeed("Applied workshop", "Turn the concepts into a small working project."))),
)

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
private val PATH_COURSES = listOf(QUIZ_COURSE, "Practical MongoDB for Application Developers", "Kotlin Coroutines in Practice")
private val COURSES = listOf(
    CourseSeed(
        QUIZ_COURSE, "Design, validate, and evolve production-ready HTTP APIs with clear contracts.",
        "Software Development", "instructor1@mentora.dev", "intermediate", "en", 850,
        sections("API Design Foundations", "Reliability and Evolution"),
    ),
    CourseSeed(
        "Practical MongoDB for Application Developers", "Model documents and build efficient queries for modern applications.",
        "Data & Analytics", "instructor1@mentora.dev", "beginner", "en", 700,
        sections("Document Modeling", "Queries and Indexes"),
    ),
    CourseSeed(
        "Kotlin Coroutines in Practice", "Write responsive concurrent Kotlin programs using structured concurrency.",
        "Software Development", "instructor2@mentora.dev", "advanced", "en", 950,
        sections("Coroutine Fundamentals", "Production Concurrency Patterns"),
    ),
    CourseSeed(
        "أساسيات تصميم تجربة المستخدم", "تعلّم مبادئ البحث والتخطيط لبناء تجارب رقمية واضحة وسهلة الاستخدام.",
        "Design", "instructor2@mentora.dev", "beginner", "ar", 600,
        sections("فهم احتياجات المستخدم", "من الفكرة إلى النموذج الأولي"),
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

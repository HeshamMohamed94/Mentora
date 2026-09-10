package com.mentora.backend.courses.repository

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.compoundIndex
import com.mongodb.client.model.Indexes.text
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document

private const val SEARCH_TEXT_INDEX_NAME = "course_search_text"

suspend fun ensureCoursesIndexes(database: MongoDatabase) {
    val courses = database.getCollection<Document>("courses")
    courses.createIndex(ascending("instructorId"))
    courses.createIndex(compoundIndex(ascending("status"), ascending("categoryId")))
    ensureSearchTextIndex(courses)
    courses.createIndex(ascending("status"))
}

/** Mongo allows at most one text index per collection. The Course Localized Metadata ticket
 * (D57) widened the search index to also cover `translations.{en,ar}.{title,description}` so
 * `$text` search matches localized metadata, not just the base `title`/`description` — a spec
 * change from the index this collection already had in any previously-seeded database, so the
 * stale one (if its key pattern differs) is dropped before the new one is created. Idempotent:
 * re-running with the same spec is a no-op past the first startup after this change. */
private suspend fun ensureSearchTextIndex(courses: MongoCollection<Document>) {
    val existing = courses.listIndexes<Document>().toList().firstOrNull { it.containsKey("textIndexVersion") }
    if (existing != null && existing.getString("name") != SEARCH_TEXT_INDEX_NAME) {
        courses.dropIndex(existing.getString("name"))
    }
    courses.createIndex(
        compoundIndex(
            text("title"), text("description"),
            text("translations.en.title"), text("translations.en.description"),
            text("translations.ar.title"), text("translations.ar.description"),
        ),
        IndexOptions().name(SEARCH_TEXT_INDEX_NAME),
    )
}

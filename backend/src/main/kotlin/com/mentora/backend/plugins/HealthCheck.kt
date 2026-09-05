package com.mentora.backend.plugins

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mongodb.MongoException
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
private data class HealthResponse(val status: String, val mongo: String)

fun Route.healthRoutes(database: MongoDatabase) {
    get("/healthz") {
        try {
            if (database.ping()) {
                call.respond(HttpStatusCode.OK, HealthResponse("ok", "ok"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse("error", "ping returned no ok value"))
            }
        } catch (cause: MongoException) {
            val reason = cause.message?.lineSequence()?.firstOrNull()?.take(160) ?: cause::class.simpleName ?: "unavailable"
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse("error", reason))
        }
    }
}

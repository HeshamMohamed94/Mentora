package com.mentora.backend.progress.routes

import com.mentora.backend.common.Role
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.requireRole
import com.mentora.backend.common.respondData
import com.mentora.backend.progress.service.PositionRequest
import com.mentora.backend.progress.service.ProgressService
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.progressRoutes(service: ProgressService) {
    authenticate("jwt-auth") {
        route("/api/v1/courses/{id}") {
            get("/progress") {
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                call.respondData(service.get(requireNotNull(call.parameters["id"]), principal))
            }
            post("/lessons/{lessonId}/complete") {
                call.requireCsrfHeader()
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                call.respondData(service.complete(
                    requireNotNull(call.parameters["id"]), requireNotNull(call.parameters["lessonId"]), principal,
                ))
            }
            post("/lessons/{lessonId}/position") {
                call.requireCsrfHeader()
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                call.respondData(service.updatePosition(
                    requireNotNull(call.parameters["id"]), requireNotNull(call.parameters["lessonId"]),
                    call.receive<PositionRequest>(), principal,
                ))
            }
        }
    }
}

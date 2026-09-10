package com.mentora.backend.learningpaths.routes

import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Role
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.requireRole
import com.mentora.backend.common.respondData
import com.mentora.backend.learningpaths.service.LearningPathService
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.learningPathRoutes(service: LearningPathService) {
    route("/api/v1/learning-paths") {
        get { call.respondData(service.list()) }
        authenticate("jwt-auth", optional = true) {
            get("/{id}") {
                call.respondData(service.get(
                    requireNotNull(call.parameters["id"]),
                    call.authentication.principal<MentoraPrincipal>(),
                    call.request.queryParameters["language"],
                ))
            }
        }
        authenticate("jwt-auth") {
            post("/{id}/follow") {
                call.requireCsrfHeader()
                call.respondData(service.follow(requireNotNull(call.parameters["id"]), call.student()))
            }
            delete("/{id}/follow") {
                call.requireCsrfHeader()
                call.respondData(service.unfollow(requireNotNull(call.parameters["id"]), call.student()))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.student() =
    mentoraPrincipal().also { it.requireRole(Role.student) }

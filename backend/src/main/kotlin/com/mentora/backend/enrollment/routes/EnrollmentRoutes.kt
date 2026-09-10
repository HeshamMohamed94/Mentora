package com.mentora.backend.enrollment.routes

import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.Role
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.requireRole
import com.mentora.backend.common.respondData
import com.mentora.backend.common.respondPage
import com.mentora.backend.enrollment.service.EnrollmentService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.enrollmentRoutes(service: EnrollmentService) {
    authenticate("jwt-auth") {
        route("/api/v1/courses/{id}/checkout") {
            get {
                val principal = call.student()
                call.respondData(service.preview(
                    requireNotNull(call.parameters["id"]), principal, call.request.queryParameters["language"],
                ))
            }
            post("/complete") {
                call.requireCsrfHeader()
                val principal = call.student()
                val outcome = service.complete(requireNotNull(call.parameters["id"]), principal)
                call.respondData(outcome.response, if (outcome.created) HttpStatusCode.Created else HttpStatusCode.OK)
            }
        }
        get("/api/v1/enrollments") {
            val principal = call.student()
            call.respondPage(service.list(principal, PageRequest.fromCall(call)))
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.student() =
    mentoraPrincipal().also { it.requireRole(Role.student) }

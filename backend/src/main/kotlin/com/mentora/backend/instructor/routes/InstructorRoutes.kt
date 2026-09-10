package com.mentora.backend.instructor.routes

import com.mentora.backend.common.Role
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireRole
import com.mentora.backend.common.respondData
import com.mentora.backend.instructor.service.InstructorService
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.instructorRoutes(service: InstructorService) {
    authenticate("jwt-auth") {
        get("/api/v1/instructor/dashboard") {
            val principal = call.mentoraPrincipal().also { it.requireRole(Role.instructor) }
            call.respondData(service.dashboard(principal, call.request.queryParameters["language"]))
        }
    }
}

package com.mentora.backend.admin.routes

import com.mentora.backend.admin.service.AdminService
import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.Role
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireRole
import com.mentora.backend.common.respondData
import com.mentora.backend.common.respondPage
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.adminRoutes(service: AdminService) {
    authenticate("jwt-auth") {
        route("/api/v1/admin") {
            get("/dashboard") {
                call.requireAdmin()
                call.respondData(service.dashboard())
            }
            get("/courses") {
                call.requireAdmin()
                call.respondPage(service.courses(call.request.queryParameters["q"], PageRequest.fromCall(call)))
            }
            get("/users") {
                call.requireAdmin()
                call.respondPage(service.users(call.request.queryParameters["q"], PageRequest.fromCall(call)))
            }
            get("/instructors") {
                call.requireAdmin()
                call.respondPage(service.instructors(call.request.queryParameters["q"], PageRequest.fromCall(call)))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireAdmin() =
    mentoraPrincipal().requireRole(Role.admin)

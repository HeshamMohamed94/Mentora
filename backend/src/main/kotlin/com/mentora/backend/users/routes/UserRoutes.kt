package com.mentora.backend.users.routes

import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.respondData
import com.mentora.backend.users.service.UpdateProfileRequest
import com.mentora.backend.users.service.UserService
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route

fun Route.userRoutes(service: UserService) {
    authenticate("jwt-auth") {
        route("/api/v1/users") {
            get("/me") { call.respondData(service.getProfile(call.mentoraPrincipal().userId)) }
            patch("/me") {
                call.requireCsrfHeader()
                call.respondData(service.updateProfile(call.mentoraPrincipal().userId, call.receive<UpdateProfileRequest>()))
            }
        }
    }
}

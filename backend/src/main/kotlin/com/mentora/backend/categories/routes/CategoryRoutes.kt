package com.mentora.backend.categories.routes

import com.mentora.backend.categories.service.CategoryNameRequest
import com.mentora.backend.categories.service.CategoryService
import com.mentora.backend.common.Role
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.requireRole
import com.mentora.backend.common.respondData
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.categoryRoutes(service: CategoryService) {
    route("/api/v1/categories") {
        get { call.respondData(service.list()) }
        authenticate("jwt-auth") {
            post {
                call.requireCsrfHeader()
                call.mentoraPrincipal().requireRole(Role.admin)
                call.respondData(service.create(call.receive<CategoryNameRequest>()), HttpStatusCode.Created)
            }
            patch("/{id}") {
                call.requireCsrfHeader()
                call.mentoraPrincipal().requireRole(Role.admin)
                call.respondData(service.update(requireNotNull(call.parameters["id"]), call.receive()))
            }
            delete("/{id}") {
                call.requireCsrfHeader()
                call.mentoraPrincipal().requireRole(Role.admin)
                service.delete(requireNotNull(call.parameters["id"]))
                call.respondData(emptyMap<String, String>())
            }
        }
    }
}

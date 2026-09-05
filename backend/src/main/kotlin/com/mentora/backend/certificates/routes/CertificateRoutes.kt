package com.mentora.backend.certificates.routes

import com.mentora.backend.certificates.service.CertificateService
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

fun Route.certificateRoutes(service: CertificateService) {
    authenticate("jwt-auth") {
        route("/api/v1/certificates") {
            get {
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                call.respondPage(service.list(principal, PageRequest.fromCall(call)))
            }
            get("/{id}") {
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                call.respondData(service.get(requireNotNull(call.parameters["id"]), principal))
            }
        }
    }
}

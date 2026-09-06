package com.mentora.backend.aitutor.routes

import com.mentora.backend.aitutor.service.AiTutorService
import com.mentora.backend.aitutor.service.SendAiMessageRequest
import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.Role
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.requireRole
import com.mentora.backend.common.respondData
import io.ktor.http.ContentType
import io.ktor.http.withCharset
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.collect

fun Route.aiTutorRoutes(service: AiTutorService) {
    authenticate("jwt-auth") {
        route("/api/v1/ai-tutor/conversation") {
            get {
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                call.respondData(service.conversation(principal, PageRequest.fromCall(call)))
            }
            rateLimit(RateLimitName("aiTutor")) {
                rateLimit(RateLimitName("aiTutorDaily")) {
                    post("/messages") {
                        val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                        call.requireCsrfHeader()
                        val tokens = service.prepareMessage(principal, call.receive<SendAiMessageRequest>())
                        call.respondTextWriter(ContentType.Text.Plain.withCharset(Charsets.UTF_8)) {
                            tokens.collect { token ->
                                write(token.text)
                                flush()
                            }
                        }
                    }
                }
            }
        }
    }
}

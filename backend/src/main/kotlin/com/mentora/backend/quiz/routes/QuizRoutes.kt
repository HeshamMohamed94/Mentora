package com.mentora.backend.quiz.routes

import com.mentora.backend.common.Role
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.requireRole
import com.mentora.backend.common.respondData
import com.mentora.backend.quiz.service.PutQuizRequest
import com.mentora.backend.quiz.service.QuizService
import com.mentora.backend.quiz.service.SubmitAttemptRequest
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.quizRoutes(service: QuizService) {
    authenticate("jwt-auth") {
        route("/api/v1/courses/{id}/quiz") {
            get {
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                call.respondData(service.studentQuiz(call.id(), principal))
            }
            get("/editor") {
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.instructor) }
                call.respondData(service.editorQuiz(call.id(), principal))
            }
            put("/editor") {
                call.requireCsrfHeader()
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.instructor) }
                call.respondData(service.replace(call.id(), principal, call.receive<PutQuizRequest>()))
            }
            post("/attempts") {
                call.requireCsrfHeader()
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                call.respondData(service.submit(call.id(), principal, call.receive<SubmitAttemptRequest>()))
            }
            get("/attempts/latest") {
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.student) }
                call.respondData(service.latest(call.id(), principal))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.id() = requireNotNull(parameters["id"])

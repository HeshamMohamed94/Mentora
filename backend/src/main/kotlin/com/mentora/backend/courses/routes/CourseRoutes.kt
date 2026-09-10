package com.mentora.backend.courses.routes

import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.Role
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.requireRole
import com.mentora.backend.common.respondData
import com.mentora.backend.common.respondPage
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.courses.service.CourseListQuery
import com.mentora.backend.courses.service.CreateCourseRequest
import com.mentora.backend.courses.service.CreateLessonRequest
import com.mentora.backend.courses.service.ReorderLessonsRequest
import com.mentora.backend.courses.service.ReorderSectionsRequest
import com.mentora.backend.courses.service.SectionTitleRequest
import com.mentora.backend.courses.service.UpdateCourseRequest
import com.mentora.backend.courses.service.UpdateLessonRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.courseRoutes(service: CourseService) {
    route("/api/v1/courses") {
        get {
            call.respondPage(service.list(CourseListQuery(
                call.request.queryParameters["category"],
                call.request.queryParameters["level"],
                call.request.queryParameters["language"],
                call.request.queryParameters["maxPrice"],
                call.request.queryParameters["q"],
                PageRequest.fromCall(call),
            )))
        }
        authenticate("jwt-auth", optional = true) {
            get("/{id}") {
                call.respondData(service.get(
                    requireNotNull(call.parameters["id"]), call.authentication.principal<MentoraPrincipal>(),
                ))
            }
        }
        authenticate("jwt-auth") {
            post {
                call.requireCsrfHeader()
                val principal = call.instructor()
                call.respondData(service.create(principal, call.receive<CreateCourseRequest>()), HttpStatusCode.Created)
            }
            patch("/{id}") {
                call.requireCsrfHeader()
                call.respondData(service.update(call.instructor(), call.id(), call.receive<UpdateCourseRequest>()))
            }
            patch("/{id}/sections/reorder") {
                call.requireCsrfHeader()
                call.respondData(service.reorderSections(call.instructor(), call.id(), call.receive<ReorderSectionsRequest>()))
            }
            post("/{id}/sections") {
                call.requireCsrfHeader()
                call.respondData(service.addSection(call.instructor(), call.id(), call.receive<SectionTitleRequest>()), HttpStatusCode.Created)
            }
            patch("/{id}/sections/{sectionId}") {
                call.requireCsrfHeader()
                call.respondData(service.updateSection(call.instructor(), call.id(), call.sectionId(), call.receive<SectionTitleRequest>()))
            }
            delete("/{id}/sections/{sectionId}") {
                call.requireCsrfHeader()
                call.respondData(service.deleteSection(call.instructor(), call.id(), call.sectionId()))
            }
            patch("/{id}/sections/{sectionId}/lessons/reorder") {
                call.requireCsrfHeader()
                call.respondData(service.reorderLessons(
                    call.instructor(), call.id(), call.sectionId(), call.receive<ReorderLessonsRequest>(),
                ))
            }
            post("/{id}/sections/{sectionId}/lessons") {
                call.requireCsrfHeader()
                call.respondData(service.addLesson(
                    call.instructor(), call.id(), call.sectionId(), call.receive<CreateLessonRequest>(),
                ), HttpStatusCode.Created)
            }
            patch("/{id}/sections/{sectionId}/lessons/{lessonId}") {
                call.requireCsrfHeader()
                call.respondData(service.updateLesson(
                    call.instructor(), call.id(), call.sectionId(), call.lessonId(), call.receive<UpdateLessonRequest>(),
                ))
            }
            delete("/{id}/sections/{sectionId}/lessons/{lessonId}") {
                call.requireCsrfHeader()
                call.respondData(service.deleteLesson(call.instructor(), call.id(), call.sectionId(), call.lessonId()))
            }
            post("/{id}/publish") {
                call.requireCsrfHeader()
                call.respondData(service.publish(call.instructor(), call.id()))
            }
            post("/{id}/unpublish") {
                call.requireCsrfHeader()
                val principal = call.mentoraPrincipal().also { it.requireRole(Role.instructor, Role.admin) }
                call.respondData(service.unpublish(principal, call.id()))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.instructor() =
    mentoraPrincipal().also { it.requireRole(Role.instructor) }
private fun io.ktor.server.application.ApplicationCall.id() = requireNotNull(parameters["id"])
private fun io.ktor.server.application.ApplicationCall.sectionId() = requireNotNull(parameters["sectionId"])
private fun io.ktor.server.application.ApplicationCall.lessonId() = requireNotNull(parameters["lessonId"])

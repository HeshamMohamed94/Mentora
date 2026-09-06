package com.mentora.backend.media.routes

import com.mentora.backend.common.ApiException
import com.mentora.backend.common.mentoraPrincipal
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.respondData
import com.mentora.backend.media.service.MediaService
import com.mentora.backend.media.service.MediaUpload
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.mediaRoutes(service: MediaService) {
    route("/api/v1/media") {
        authenticate("jwt-auth") {
            post("/uploads") {
                call.requireCsrfHeader()
                val fields = mutableMapOf<String, String>()
                var response: com.mentora.backend.media.service.MediaUploadResponse? = null
                call.receiveMultipart().forEachPart { part ->
                    try {
                        when (part) {
                            is PartData.FormItem -> part.name?.let { fields[it] = part.value }
                            is PartData.FileItem -> if (response == null) {
                                response = service.upload(
                                    MediaUpload(
                                        fields["kind"], fields["ownerRefId"], fields["contentType"],
                                        fields["courseId"], fields["durationSeconds"], part.provider(),
                                    ),
                                    call.mentoraPrincipal(),
                                )
                            }
                            else -> Unit
                        }
                    } finally {
                        part.dispose()
                    }
                }
                call.respondData(
                    response ?: throw ApiException.Validation(fields = mapOf("file" to "REQUIRED")),
                    HttpStatusCode.Created,
                )
            }
            get("/{mediaId}/playback-url") {
                call.respondData(service.playbackUrl(requireNotNull(call.parameters["mediaId"]), call.mentoraPrincipal()))
            }
        }
        get("/{mediaId}/file") {
            call.respond(service.publicFile(requireNotNull(call.parameters["mediaId"])).asLocalFileContent())
        }
        get("/{mediaId}/stream") {
            val mediaId = requireNotNull(call.parameters["mediaId"])
            service.verifyPlaybackToken(mediaId, call.request.queryParameters["token"])
            val content = service.stream(mediaId)
            call.respond(content.asLocalFileContent())
        }
    }
}

private fun com.mentora.backend.media.storage.MediaContent.asLocalFileContent() =
    LocalFileContent(file, ContentType.parse(contentType))

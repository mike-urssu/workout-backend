package cloud.jjoon.workout.media.controller

import cloud.jjoon.workout.media.service.MediaDownload
import cloud.jjoon.workout.media.service.MediaFile
import cloud.jjoon.workout.media.service.WorkoutMediaResponse
import cloud.jjoon.workout.media.service.WorkoutMediaService
import cloud.jjoon.workout.session.service.ExpiredSessionCleaner
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
class WorkoutMediaController(
    private val mediaService: WorkoutMediaService,
    private val expiredSessionCleaner: ExpiredSessionCleaner,
) {

    /** API-MEDIA-001: one file per request, staged until the session completes. */
    @PostMapping("/api/v1/workout-sessions/{sessionId}/media", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable sessionId: UUID,
        @RequestPart("file") file: MultipartFile,
    ): WorkoutMediaResponse {
        expiredSessionCleaner.cleanUp(userId)
        return mediaService.upload(userId, sessionId, file)
    }

    /** API-MEDIA-002 */
    @GetMapping("/api/v1/media/{mediaId}/preview")
    fun preview(@AuthenticationPrincipal userId: UUID, @PathVariable mediaId: UUID): ResponseEntity<InputStreamResource> =
        respond(mediaService.download(userId, mediaId, MediaFile.PREVIEW))

    /** API-MEDIA-003: `Range` goes to storage so video players can stream and seek (DEC-MEDIA-007). */
    @GetMapping("/api/v1/media/{mediaId}/original")
    fun original(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable mediaId: UUID,
        @RequestHeader(HttpHeaders.RANGE, required = false) range: String?,
    ): ResponseEntity<InputStreamResource> =
        respond(mediaService.download(userId, mediaId, MediaFile.ORIGINAL, range), ranged = true)

    /** API-MEDIA-004: the viewer's smaller copy, with `Range` like the original (DEC-MEDIA-010). */
    @GetMapping("/api/v1/media/{mediaId}/display")
    fun display(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable mediaId: UUID,
        @RequestHeader(HttpHeaders.RANGE, required = false) range: String?,
    ): ResponseEntity<InputStreamResource> =
        respond(mediaService.download(userId, mediaId, MediaFile.DISPLAY, range), ranged = true)

    /** Streams straight from storage. Files never change after upload, so clients may keep them (DEC-MEDIA-006). */
    private fun respond(download: MediaDownload, ranged: Boolean = false): ResponseEntity<InputStreamResource> {
        val response = ResponseEntity.status(if (download.file.contentRange != null) HttpStatus.PARTIAL_CONTENT else HttpStatus.OK)
            .contentType(MediaType.parseMediaType(download.contentType))
            .contentLength(download.file.length)
            .header(HttpHeaders.CACHE_CONTROL, if (download.cacheable) "private, max-age=31536000, immutable" else "private, no-store")
        if (ranged) response.header(HttpHeaders.ACCEPT_RANGES, "bytes")
        download.file.contentRange?.let { response.header(HttpHeaders.CONTENT_RANGE, it) }
        return response.body(InputStreamResource(download.file.stream))
    }
}

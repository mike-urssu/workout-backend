package cloud.jjoon.workout.media.service

import cloud.jjoon.workout.common.error.BusinessException
import cloud.jjoon.workout.common.error.ErrorCode
import cloud.jjoon.workout.common.storage.MediaKind
import cloud.jjoon.workout.common.storage.MediaProcessor
import cloud.jjoon.workout.common.storage.ObjectStorage
import cloud.jjoon.workout.common.storage.StoredObject
import cloud.jjoon.workout.common.storage.afterCommit
import cloud.jjoon.workout.media.domain.WorkoutMedia
import cloud.jjoon.workout.media.repository.WorkoutMediaRepository
import cloud.jjoon.workout.session.domain.WorkoutSession
import cloud.jjoon.workout.session.domain.WorkoutSessionStatus
import cloud.jjoon.workout.session.repository.WorkoutSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Workout proof photos and videos (docs/design/workout-media.md). */
@Service
class WorkoutMediaService(
    private val sessionRepository: WorkoutSessionRepository,
    private val mediaRepository: WorkoutMediaRepository,
    private val storage: ObjectStorage,
    private val processor: MediaProcessor,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
) {
    private val transaction = TransactionTemplate(transactionManager)

    /**
     * Stages one upload (workout-media 3.2 (1)). The slow part — reading, previewing, storing — runs without a lock;
     * only the final insert locks the session and checks it again (DEC-MEDIA-004).
     */
    fun upload(userId: UUID, sessionId: UUID, file: MultipartFile): WorkoutMediaResponse {
        if (file.isEmpty) throw BusinessException(ErrorCode.VALIDATION_FAILED)
        checkUploadable(userId, sessionRepository.findByIdOrNull(sessionId))
        val original = Files.createTempFile("media-", null)
        val preview = Files.createTempFile("media-preview-", ".jpg")
        try {
            file.transferTo(original)
            val format = processor.detect(original) ?: throw BusinessException(ErrorCode.MEDIA_UNSUPPORTED_TYPE)
            val size = Files.size(original)
            val maxBytes = if (format.kind == MediaKind.PHOTO) MAX_PHOTO_BYTES else MAX_VIDEO_BYTES
            if (size > maxBytes) throw limitExceeded("FILE_SIZE", maxBytes, if (format.kind == MediaKind.PHOTO) "사진은 20MB까지 올릴 수 있습니다." else "동영상은 100MB까지 올릴 수 있습니다.")
            if (format.kind == MediaKind.VIDEO) {
                val seconds = processor.durationSeconds(original) ?: throw BusinessException(ErrorCode.MEDIA_UNSUPPORTED_TYPE)
                if (seconds > MAX_VIDEO_SECONDS) throw limitExceeded("DURATION", MAX_VIDEO_SECONDS, "동영상은 1분까지 올릴 수 있습니다.")
            }
            if (!processor.preview(original, preview)) throw BusinessException(ErrorCode.MEDIA_UNSUPPORTED_TYPE) // unreadable

            val now = clock.instant()
            val id = WorkoutMedia.newId(now)
            val keys = listOf(WorkoutMedia.originalKey(userId, sessionId, id), WorkoutMedia.previewKey(userId, sessionId, id))
            try {
                storage.put(keys[0], original, format.contentType)
                storage.put(keys[1], preview, PREVIEW_CONTENT_TYPE)
                return transaction.execute {
                    checkUploadable(userId, sessionRepository.findForUpdateById(sessionId))
                    val media = mediaRepository.save(WorkoutMedia(id, sessionId, format.kind, format.contentType, size.toInt(), now))
                    log.info(
                        "event=workout_media.uploaded userId={} sessionId={} mediaId={} contentType={} size={}",
                        userId, sessionId, id, format.contentType, size,
                    )
                    WorkoutMediaResponse.of(media)
                }!!
            } catch (e: Exception) {
                storage.deleteQuietly(keys) // the row was not written, so the files must not stay (architecture 2.6)
                throw e
            }
        } finally {
            Files.deleteIfExists(original)
            Files.deleteIfExists(preview)
        }
    }

    /**
     * Attaches the chosen uploads in order and drops the other staged ones (workout-media 3.2 (2), BR-003, BR-007).
     * Runs inside the completing transaction, which holds the session lock.
     */
    fun attach(userId: UUID, session: WorkoutSession, mediaIds: List<UUID>) {
        if (mediaIds.size != mediaIds.toSet().size) throw BusinessException(ErrorCode.VALIDATION_FAILED)
        val staged = mediaRepository.findByWorkoutSessionId(session.id!!).associateBy { it.id }
        if (!staged.keys.containsAll(mediaIds)) throw BusinessException(ErrorCode.VALIDATION_FAILED)
        mediaIds.forEachIndexed { index, id -> staged.getValue(id).sortOrder = index + 1 }
        val dropped = staged.values.filter { it.id !in mediaIds }
        mediaRepository.deleteAll(dropped)
        val droppedKeys = dropped.flatMap {
            listOf(WorkoutMedia.originalKey(userId, session.id!!, it.id), WorkoutMedia.previewKey(userId, session.id!!, it.id))
        }
        afterCommit { storage.deleteQuietly(droppedKeys) }
    }

    /**
     * API-MEDIA-002, 003: only attached files (their session is completed) of the user's own sessions.
     * Staged uploads and missing files are 404 (ERR-007), someone else's are 403 (ERR-004).
     */
    fun download(userId: UUID, mediaId: UUID, original: Boolean, range: String? = null): MediaDownload {
        val media = mediaRepository.findByIdOrNull(mediaId) ?: throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
        val session = sessionRepository.findByIdOrNull(media.workoutSessionId)
        if (session == null || session.status != WorkoutSessionStatus.COMPLETED) throw BusinessException(ErrorCode.MEDIA_NOT_FOUND)
        if (session.userId != userId) throw BusinessException(ErrorCode.FORBIDDEN)
        return if (original) {
            MediaDownload(media.contentType, storage.open(WorkoutMedia.originalKey(userId, session.id!!, mediaId), range))
        } else {
            MediaDownload(PREVIEW_CONTENT_TYPE, storage.open(WorkoutMedia.previewKey(userId, session.id!!, mediaId)))
        }
    }

    /** Auto-completed sessions keep no media: nobody pressed save (design REQ-WORKOUT-006 step 2). */
    fun discardStaged(userId: UUID, sessionIds: List<UUID>) {
        if (sessionIds.isEmpty()) return
        mediaRepository.deleteByWorkoutSessionIdIn(sessionIds)
        deleteFilesOfSessions(userId, sessionIds)
    }

    /** The rows go by cascade with the session; the files follow once that commits (workout-media 6.4). */
    fun deleteFilesOfSessions(userId: UUID, sessionIds: List<UUID>) =
        afterCommit { sessionIds.forEach { storage.deletePrefixQuietly(WorkoutMedia.sessionPrefix(userId, it)) } }

    private fun checkUploadable(userId: UUID, session: WorkoutSession?) {
        if (session == null) throw BusinessException(ErrorCode.WORKOUT_SESSION_NOT_FOUND)
        if (session.userId != userId) throw BusinessException(ErrorCode.FORBIDDEN) // ERR-004
        if (session.status == WorkoutSessionStatus.COMPLETED) throw BusinessException(ErrorCode.WORKOUT_SESSION_NOT_EDITABLE) // BR-002
        if (mediaRepository.countByWorkoutSessionId(session.id!!) >= MAX_COUNT) {
            throw limitExceeded("COUNT", MAX_COUNT.toLong(), "사진·동영상은 ${MAX_COUNT}개까지 올릴 수 있습니다.") // BR-005
        }
    }

    private fun limitExceeded(limit: String, max: Long, message: String) =
        BusinessException(ErrorCode.MEDIA_LIMIT_EXCEEDED, mapOf("limit" to limit, "max" to max), message)

    companion object {
        private val log = LoggerFactory.getLogger(WorkoutMediaService::class.java)
        const val MAX_COUNT = 5
        private const val MAX_PHOTO_BYTES = 20L * 1024 * 1024 // DEC-MEDIA-005
        private const val MAX_VIDEO_BYTES = 100L * 1024 * 1024
        private const val MAX_VIDEO_SECONDS = 60L
        private const val PREVIEW_CONTENT_TYPE = "image/jpeg"
    }
}

data class WorkoutMediaResponse(
    val id: UUID,
    val mediaType: MediaKind,
    val contentType: String,
    val fileSize: Int,
    val createdAt: Instant,
) {
    companion object {
        fun of(media: WorkoutMedia) = WorkoutMediaResponse(media.id, media.mediaType, media.contentType, media.fileSize, media.createdAt)
    }
}

data class MediaDownload(val contentType: String, val file: StoredObject)

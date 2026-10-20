package cloud.jjoon.workout.media.domain

import cloud.jjoon.workout.common.storage.MediaKind
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/**
 * A photo or video of a workout session. Rows of an in-progress session are staged uploads; completing the session
 * gives the chosen ones a [sortOrder] and drops the rest (workout-media DEC-MEDIA-001).
 * The id is made before the row is saved because the files are stored first (architecture 2.6).
 */
@Entity
@Table(name = "workout_media")
class WorkoutMedia(
    @Id
    val id: UUID,

    @Column(name = "workout_session_id", nullable = false, updatable = false)
    val workoutSessionId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, updatable = false)
    val mediaType: MediaKind,

    @Column(name = "content_type", nullable = false, updatable = false)
    val contentType: String,

    @Column(name = "file_size", nullable = false, updatable = false)
    val fileSize: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @Column(name = "sort_order")
    var sortOrder: Int? = null

    companion object {
        private val random = SecureRandom()

        /** UUIDv7 (RFC 9562): 48-bit Unix milliseconds, version 7, variant 2, random rest (DEC-ARCH-010). */
        fun newId(now: Instant): UUID {
            val msb = (now.toEpochMilli() shl 16) or 0x7000L or (random.nextInt(0x1000).toLong())
            val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
            return UUID(msb, lsb)
        }

        /** Object keys sit under the user's prefix so an account's files go with one prefix delete (architecture 7.7). */
        fun sessionPrefix(userId: UUID, sessionId: UUID) = "users/$userId/workout-sessions/$sessionId/"

        fun originalKey(userId: UUID, sessionId: UUID, mediaId: UUID) = "${sessionPrefix(userId, sessionId)}media/$mediaId/original"

        fun previewKey(userId: UUID, sessionId: UUID, mediaId: UUID) = "${sessionPrefix(userId, sessionId)}media/$mediaId/preview.jpg"

        /** A photo's 2048px JPEG or a video's 720p MP4 for the viewer (workout-media DEC-MEDIA-010). */
        fun displayKey(userId: UUID, sessionId: UUID, mediaId: UUID) = "${sessionPrefix(userId, sessionId)}media/$mediaId/display"

        fun keys(userId: UUID, sessionId: UUID, mediaId: UUID) =
            listOf(originalKey(userId, sessionId, mediaId), previewKey(userId, sessionId, mediaId), displayKey(userId, sessionId, mediaId))
    }
}

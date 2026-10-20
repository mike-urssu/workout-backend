package cloud.jjoon.workout.session.service

import cloud.jjoon.workout.common.error.BusinessException
import cloud.jjoon.workout.common.error.ErrorCode
import cloud.jjoon.workout.common.storage.MediaKind
import cloud.jjoon.workout.media.domain.WorkoutMedia
import cloud.jjoon.workout.media.repository.WorkoutMediaRepository
import cloud.jjoon.workout.media.service.WorkoutMediaService
import cloud.jjoon.workout.session.repository.CalendarDay
import cloud.jjoon.workout.session.repository.DayRecord
import cloud.jjoon.workout.session.repository.WorkoutDayQueryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** The history screen's view of workouts: one calendar month and one day at a time (design workout-history 3.2). */
@Service
class WorkoutDayService(
    private val queryRepository: WorkoutDayQueryRepository,
    private val mediaRepository: WorkoutMediaRepository,
    private val mediaService: WorkoutMediaService,
) {

    @Transactional(readOnly = true)
    fun calendar(userId: UUID, month: YearMonth?): WorkoutCalendarResponse {
        val latestDate = queryRepository.findLatestDate(userId)
        // Without a month the screen opens on the latest workout's month; with no workouts at all, on nothing.
        val shown = month ?: latestDate?.let(YearMonth::from)
            ?: return WorkoutCalendarResponse(month = null, latestDate = null, days = emptyList())
        return WorkoutCalendarResponse(shown, latestDate, queryRepository.findMonth(userId, shown))
    }

    /** API-WORKOUT-008: null when nothing was completed that day. */
    @Transactional(readOnly = true)
    fun day(userId: UUID, date: LocalDate): WorkoutDayResponse? =
        queryRepository.findDay(userId, date)?.let { record ->
            // Sessions in the order they started, then each one's photos and videos in attached order (REQ-MEDIA-002).
            val media = mediaRepository.findByWorkoutSessionIdIn(record.sessionIds)
                .sortedWith(compareBy({ record.sessionIds.indexOf(it.workoutSessionId) }, { it.sortOrder }))
            WorkoutDayResponse.of(date, record, media.map(DayMediaResponse::of))
        }

    /** API-WORKOUT-009: removes every session completed that day; in-progress ones stay (cancel removes those). */
    @Transactional
    fun delete(userId: UUID, date: LocalDate) {
        val deleted = queryRepository.deleteDay(userId, date)
        if (deleted.isEmpty()) throw BusinessException(ErrorCode.WORKOUT_DAY_NOT_FOUND) // ERR-004
        mediaService.deleteFilesOfSessions(userId, deleted) // after commit (workout-media 6.4)
        deleted.forEach { log.info("event=workout_session.deleted userId={} sessionId={}", userId, it) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WorkoutDayService::class.java)
    }
}

data class WorkoutCalendarResponse(val month: YearMonth?, val latestDate: LocalDate?, val days: List<CalendarDay>)

/** The day's completed sessions as one record (design 5.2 WorkoutDayResponse, DEC-WORKOUT-020). */
data class WorkoutDayResponse(
    val date: LocalDate,
    val durationSeconds: Long,
    val exercises: List<DayExerciseResponse>,
    val categories: List<DayCategorySummary>,
    val summary: DaySummary,
    val media: List<DayMediaResponse>,
) {
    companion object {
        fun of(date: LocalDate, record: DayRecord, media: List<DayMediaResponse>): WorkoutDayResponse {
            // Same numbering and volume rules as the in-progress status (BR-007, BR-010, BR-015).
            val exercises = record.exercises.map { DayExerciseResponse.of(SessionExerciseResponse.of(it)) }
            val worked = record.exercises.zip(exercises).filter { (row, _) -> row.sets.isNotEmpty() }
            val categories = worked.groupBy { (row, _) -> row.categorySortOrder }.toSortedMap().values.map { group ->
                DayCategorySummary(
                    id = group.first().second.category.id,
                    name = group.first().second.category.name,
                    setCount = group.sumOf { it.second.setCount },
                    volume = group.sumOf { it.second.volume },
                    exerciseIds = group.map { it.second.exerciseId },
                )
            }
            return WorkoutDayResponse(
                date = date,
                durationSeconds = record.durationSeconds,
                exercises = exercises,
                categories = categories,
                summary = DaySummary(
                    exerciseCount = worked.size,
                    totalSets = exercises.sumOf { it.setCount },
                    totalRepetitions = exercises.sumOf { it.totalRepetitions },
                    totalVolume = exercises.fold(BigDecimal.ZERO.setScale(2)) { sum, it -> sum + it.volume },
                ),
                media = media,
            )
        }
    }
}

data class DayExerciseResponse(
    val exerciseId: UUID,
    val name: String,
    val nameEn: String?,
    val target: String?,
    val category: CategoryRef,
    val sets: List<WorkoutSetResponse>,
    val setCount: Int,
    val totalRepetitions: Int,
    val volume: BigDecimal,
) {
    companion object {
        fun of(e: SessionExerciseResponse) =
            DayExerciseResponse(e.exerciseId, e.name, e.nameEn, e.target, e.category, e.sets, e.setCount, e.totalRepetitions, e.volume)
    }
}

data class DayCategorySummary(val id: UUID, val name: String, val setCount: Int, val volume: BigDecimal, val exerciseIds: List<UUID>)

data class DaySummary(val exerciseCount: Int, val totalSets: Int, val totalRepetitions: Int, val totalVolume: BigDecimal)

/** Files are fetched through the API, never from storage directly (workout-media API-MEDIA-002, 003). */
data class DayMediaResponse(
    val id: UUID,
    val mediaType: MediaKind,
    val contentType: String,
    val previewUrl: String,
    val displayUrl: String,
    val originalUrl: String,
) {
    companion object {
        fun of(media: WorkoutMedia) = DayMediaResponse(
            id = media.id,
            mediaType = media.mediaType,
            contentType = media.contentType,
            previewUrl = "/api/v1/media/${media.id}/preview",
            displayUrl = "/api/v1/media/${media.id}/display",
            originalUrl = "/api/v1/media/${media.id}/original",
        )
    }
}

package cloud.jjoon.workout.session.repository

import cloud.jjoon.workout.jooq.Tables.EXERCISE
import cloud.jjoon.workout.jooq.Tables.EXERCISE_CATEGORY
import cloud.jjoon.workout.jooq.Tables.WORKOUT_SESSION
import cloud.jjoon.workout.jooq.Tables.WORKOUT_SESSION_EXERCISE
import cloud.jjoon.workout.jooq.Tables.WORKOUT_SET
import cloud.jjoon.workout.session.domain.WorkoutSessionStatus
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.exists
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.row
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.selectOne
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class WorkoutSessionQueryRepository(private val dsl: DSLContext) {

    /** The session's exercises with their body part and sets, in the order they were added, read in one query. */
    fun findExercises(sessionId: UUID): List<SessionExerciseRow> {
        val wse = WORKOUT_SESSION_EXERCISE
        val category = EXERCISE_CATEGORY
        val rows = dsl.select(
            wse.ID, wse.EXERCISE_ID, EXERCISE.NAME, EXERCISE.NAME_EN, EXERCISE.TARGET,
            category.ID, category.NAME, category.SORT_ORDER,
            WORKOUT_SET.ID, WORKOUT_SET.WEIGHT, WORKOUT_SET.REPETITIONS, WORKOUT_SET.CREATED_AT,
        )
            .from(wse)
            .join(EXERCISE).on(EXERCISE.ID.eq(wse.EXERCISE_ID))
            .join(category).on(category.ID.eq(EXERCISE.EXERCISE_CATEGORY_ID))
            .leftJoin(WORKOUT_SET).on(WORKOUT_SET.WORKOUT_SESSION_EXERCISE_ID.eq(wse.ID))
            .where(wse.WORKOUT_SESSION_ID.eq(sessionId))
            .orderBy(wse.CREATED_AT, wse.ID, WORKOUT_SET.CREATED_AT, WORKOUT_SET.ID)
            .fetch()
        return rows.groupBy { it.value1() }.values.map { group ->
            val first = group.first()
            SessionExerciseRow(
                sessionExerciseId = first.value1(),
                exerciseId = first.value2(),
                name = first.value3(),
                nameEn = first.value4(),
                target = first.value5(),
                categoryId = first.value6(),
                categoryName = first.value7(),
                categorySortOrder = first.value8(),
                sets = group.filter { it.value9() != null }.map { SetRow(it.value9(), it.value10(), it.value11(), it.value12()) },
            )
        }
    }

    /**
     * The user's completed sessions that recorded the exercise (BR-016), one per page, most recent first (REQ-SET-006).
     * In-progress sessions and sessions where the exercise has no sets are skipped. Two queries: the count, then the page.
     */
    fun findRecords(userId: UUID, exerciseId: UUID, page: Int): ExerciseRecordPage {
        val wse = WORKOUT_SESSION_EXERCISE
        val ws = WORKOUT_SESSION
        val recorded = ws.USER_ID.eq(userId)
            .and(ws.STATUS.eq(WorkoutSessionStatus.COMPLETED.name))
            .and(wse.EXERCISE_ID.eq(exerciseId))
            .andExists(selectOne().from(WORKOUT_SET).where(WORKOUT_SET.WORKOUT_SESSION_EXERCISE_ID.eq(wse.ID)))
        val total = dsl.fetchCount(wse.join(ws).on(ws.ID.eq(wse.WORKOUT_SESSION_ID)), recorded)
        // A session holds an exercise once (ux_workout_session_exercise_session_exercise), so one row is one session.
        val picked = select(wse.ID)
            .from(wse)
            .join(ws).on(ws.ID.eq(wse.WORKOUT_SESSION_ID))
            .where(recorded)
            .orderBy(ws.PERFORMED_DATE.desc(), ws.STARTED_AT.desc(), ws.ID.desc())
            .limit(1)
            .offset(page)
        val rows = dsl.select(ws.PERFORMED_DATE, WORKOUT_SET.WEIGHT, WORKOUT_SET.REPETITIONS)
            .from(wse)
            .join(ws).on(ws.ID.eq(wse.WORKOUT_SESSION_ID))
            .join(WORKOUT_SET).on(WORKOUT_SET.WORKOUT_SESSION_EXERCISE_ID.eq(wse.ID))
            .where(wse.ID.eq(field(picked)))
            .orderBy(WORKOUT_SET.CREATED_AT, WORKOUT_SET.ID)
            .fetch()
        val content = if (rows.isEmpty()) emptyList() else listOf(
            ExerciseRecord(
                performedDate = rows.first().value1(),
                sets = rows.mapIndexed { index, row -> ExerciseRecordSet(index + 1, row.value2(), row.value3()) },
            ),
        )
        return ExerciseRecordPage(content, page, size = 1, totalElements = total.toLong(), totalPages = total)
    }

    /** Completes stale sessions that have sets, ending them at their last set (BR-013). Idempotent. */
    fun completeExpired(userId: UUID, cutoff: Instant, now: Instant): List<UUID> {
        val lastSetAt = select(max(WORKOUT_SET.CREATED_AT)).from(setsOf(WORKOUT_SESSION.ID))
        return dsl.update(WORKOUT_SESSION)
            .set(WORKOUT_SESSION.STATUS, WorkoutSessionStatus.COMPLETED.name)
            .set(WORKOUT_SESSION.ENDED_AT, field(lastSetAt))
            .set(WORKOUT_SESSION.UPDATED_AT, now)
            .where(expired(userId, cutoff))
            .and(exists(selectOne().from(setsOf(WORKOUT_SESSION.ID))))
            .returning(WORKOUT_SESSION.ID)
            .fetch(WORKOUT_SESSION.ID)
    }

    /** Stale sessions without sets cannot be completed (BR-012), so they are deleted (BR-013). Idempotent. */
    fun deleteExpiredWithoutSets(userId: UUID, cutoff: Instant): List<UUID> =
        dsl.deleteFrom(WORKOUT_SESSION)
            .where(expired(userId, cutoff))
            .andNotExists(selectOne().from(setsOf(WORKOUT_SESSION.ID)))
            .returning(WORKOUT_SESSION.ID)
            .fetch(WORKOUT_SESSION.ID)

    /** Of the given completed sessions, deletes those left without sets after an exercise was deleted (BR-029). */
    fun deleteCompletedWithoutSets(userId: UUID, sessionIds: Collection<UUID>): List<UUID> =
        dsl.deleteFrom(WORKOUT_SESSION)
            .where(WORKOUT_SESSION.ID.`in`(sessionIds))
            .and(WORKOUT_SESSION.USER_ID.eq(userId))
            .and(WORKOUT_SESSION.STATUS.eq(WorkoutSessionStatus.COMPLETED.name))
            .andNotExists(selectOne().from(setsOf(WORKOUT_SESSION.ID)))
            .returning(WORKOUT_SESSION.ID)
            .fetch(WORKOUT_SESSION.ID)

    private fun expired(userId: UUID, cutoff: Instant) =
        WORKOUT_SESSION.USER_ID.eq(userId)
            .and(WORKOUT_SESSION.STATUS.eq(WorkoutSessionStatus.IN_PROGRESS.name))
            .and(WORKOUT_SESSION.STARTED_AT.lt(cutoff))

    /** Sets of the session identified by [sessionId], usable as a correlated subquery source. */
    private fun setsOf(sessionId: Field<UUID>) =
        WORKOUT_SET.join(WORKOUT_SESSION_EXERCISE)
            .on(WORKOUT_SESSION_EXERCISE.ID.eq(WORKOUT_SET.WORKOUT_SESSION_EXERCISE_ID))
            .and(WORKOUT_SESSION_EXERCISE.WORKOUT_SESSION_ID.eq(sessionId))

    fun hasSets(sessionId: UUID): Boolean =
        dsl.fetchExists(
            WORKOUT_SET.join(WORKOUT_SESSION_EXERCISE)
                .on(WORKOUT_SESSION_EXERCISE.ID.eq(WORKOUT_SET.WORKOUT_SESSION_EXERCISE_ID)),
            WORKOUT_SESSION_EXERCISE.WORKOUT_SESSION_ID.eq(sessionId),
        )

    /** Position of the set within its exercise, by the order sets were added (BR-007). */
    fun setNumber(setId: UUID): Int {
        val target = WORKOUT_SET.`as`("target")
        return dsl.selectCount()
            .from(WORKOUT_SET)
            .join(target).on(target.WORKOUT_SESSION_EXERCISE_ID.eq(WORKOUT_SET.WORKOUT_SESSION_EXERCISE_ID))
            .where(target.ID.eq(setId))
            .and(row(WORKOUT_SET.CREATED_AT, WORKOUT_SET.ID).le(target.CREATED_AT, target.ID))
            .fetchOne(0, Int::class.java)!!
    }
}

data class SessionExerciseRow(
    val sessionExerciseId: UUID,
    val exerciseId: UUID,
    val name: String,
    val nameEn: String?,
    val target: String?,
    val categoryId: UUID,
    val categoryName: String,
    val categorySortOrder: Int,
    val sets: List<SetRow>,
)

data class SetRow(val id: UUID, val weight: BigDecimal, val repetitions: Int, val createdAt: Instant)

/** The common page response (architecture 5장) with one session per page (DEC-WORKOUT-032). */
data class ExerciseRecordPage(
    val content: List<ExerciseRecord>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class ExerciseRecord(val performedDate: LocalDate, val sets: List<ExerciseRecordSet>)

data class ExerciseRecordSet(val setNumber: Int, val weight: BigDecimal, val repetitions: Int)

package cloud.jjoon.workout.session

import cloud.jjoon.workout.TestcontainersConfiguration
import cloud.jjoon.workout.session.repository.WorkoutSessionQueryRepository
import cloud.jjoon.workout.support.Operator
import cloud.jjoon.workout.support.SignedInUser
import cloud.jjoon.workout.support.TestClockConfiguration
import cloud.jjoon.workout.support.TestUsers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/** API-SET-006's query: one completed session per page, most recent first (REQ-SET-006, BR-016). */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class ExerciseRecordQueryTest {

    @Autowired lateinit var queryRepository: WorkoutSessionQueryRepository
    @Autowired lateinit var operator: Operator
    @Autowired lateinit var users: TestUsers
    @Autowired lateinit var jdbc: JdbcTemplate

    lateinit var me: SignedInUser
    lateinit var bench: UUID

    @BeforeEach
    fun setUp() {
        operator.deleteAllAccounts()
        me = users.signIn("demo.user")
        bench = operator.exerciseId("벤치프레스", me.id)
    }

    @Test
    fun `BR-016 완료된 세션을 가장 최근부터 한 페이지에 하나씩 넘긴다`() {
        session("2026-09-20", "2026-09-20T09:00:00Z", bench to listOf(60.0 to 12))
        session("2026-09-24", "2026-09-24T09:00:00Z", bench to listOf(65.0 to 10, 70.0 to 8))

        val first = queryRepository.findRecords(me.id, bench, 0)
        assertEquals(2, first.totalElements)
        assertEquals(2, first.totalPages)
        assertEquals(1, first.size)
        assertEquals(LocalDate.parse("2026-09-24"), first.content.single().performedDate)
        assertEquals(
            listOf(Triple(1, "65.00", 10), Triple(2, "70.00", 8)),
            first.content.single().sets.map { Triple(it.setNumber, it.weight.toPlainString(), it.repetitions) },
        )

        val second = queryRepository.findRecords(me.id, bench, 1)
        assertEquals(1, second.page)
        assertEquals(LocalDate.parse("2026-09-20"), second.content.single().performedDate)

        assertEquals(emptyList(), queryRepository.findRecords(me.id, bench, 2).content)
    }

    @Test
    fun `BR-016 진행 중 세션, 종목만 넣고 세트가 없는 세션, 다른 사용자의 세션은 세지 않는다`() {
        val squat = operator.exerciseId("스쿼트", me.id)
        val other = users.signIn("other.user")
        session("2026-09-20", "2026-09-20T09:00:00Z", bench to listOf(60.0 to 12))
        session("2026-09-21", "2026-09-21T09:00:00Z", bench to emptyList(), squat to listOf(100.0 to 5))
        session("2026-09-22", "2026-09-22T09:00:00Z", operator.exerciseId("벤치프레스", other.id) to listOf(90.0 to 5), userId = other.id)
        session("2026-09-23", "2026-09-23T09:00:00Z", bench to listOf(80.0 to 5), status = "IN_PROGRESS")

        val records = queryRepository.findRecords(me.id, bench, 0)

        assertEquals(1, records.totalElements)
        assertEquals(LocalDate.parse("2026-09-20"), records.content.single().performedDate)
    }

    @Test
    fun `REQ-SET-006 같은 날 세션은 늦게 시작한 것부터 따로 보이고 다른 종목의 세트는 섞이지 않는다`() {
        val squat = operator.exerciseId("스쿼트", me.id)
        session("2026-09-24", "2026-09-24T01:00:00Z", bench to listOf(60.0 to 12))
        session("2026-09-24", "2026-09-24T10:00:00Z", bench to listOf(70.0 to 8), squat to listOf(100.0 to 5, 110.0 to 3))

        val pages = (0..1).map { queryRepository.findRecords(me.id, bench, it).content.single() }

        assertEquals(listOf("70.00", "60.00"), pages.map { it.sets.single().weight.toPlainString() })
    }

    private fun session(
        performedDate: String,
        startedAt: String,
        vararg exercises: Pair<UUID, List<Pair<Double, Int>>>,
        status: String = "COMPLETED",
        userId: UUID = me.id,
    ): UUID {
        val id = UUID.randomUUID()
        val started = Timestamp.from(Instant.parse(startedAt))
        jdbc.update(
            "INSERT INTO workout_session (id, user_id, status, performed_date, started_at, ended_at) VALUES (?, ?, ?, ?, ?, ?)",
            id, userId, status, Date.valueOf(performedDate), started, if (status == "COMPLETED") started else null,
        )
        exercises.forEach { (exerciseId, sets) ->
            val sessionExercise = UUID.randomUUID()
            jdbc.update("INSERT INTO workout_session_exercise (id, workout_session_id, exercise_id) VALUES (?, ?, ?)", sessionExercise, id, exerciseId)
            sets.forEachIndexed { i, (weight, repetitions) ->
                jdbc.update(
                    "INSERT INTO workout_set (id, workout_session_exercise_id, weight, repetitions, created_at) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), sessionExercise, BigDecimal.valueOf(weight), repetitions,
                    Timestamp.from(Instant.parse(startedAt).plusSeconds(60L * i)),
                )
            }
        }
        return id
    }
}

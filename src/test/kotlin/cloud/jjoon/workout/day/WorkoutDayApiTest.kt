package cloud.jjoon.workout.day

import cloud.jjoon.workout.TestcontainersConfiguration
import cloud.jjoon.workout.support.MutableClock
import cloud.jjoon.workout.support.Operator
import cloud.jjoon.workout.support.SignedInUser
import cloud.jjoon.workout.support.TestClockConfiguration
import cloud.jjoon.workout.support.TestStorage
import cloud.jjoon.workout.support.TestUsers
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.test.assertEquals

/** Workout days: the history calendar, per-date records and per-date delete (design workout-history). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class WorkoutDayApiTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var operator: Operator
    @Autowired lateinit var users: TestUsers
    @Autowired lateinit var clock: MutableClock
    @Autowired lateinit var storage: TestStorage

    lateinit var me: SignedInUser

    @BeforeEach
    fun setUp() {
        operator.deleteAllAccounts()
        storage.deleteAll()
        clock.reset() // 2026-10-05T09:00Z, 18:00 in Seoul
        me = users.signIn("demo.user")
    }

    @Test
    fun `REQ-WORKOUT-007 BR-018 달력은 그 달의 운동한 날마다 완료한 세션의 부위를 부위 순서로 준다`() {
        workout(me, "스쿼트", "벤치프레스")                    // 10-05: 하체, 가슴
        clock.advance(Duration.ofDays(1))
        workout(me, "랫풀다운")                              // 10-06: 등
        clock.advance(Duration.ofHours(1))
        workout(me, "벤치프레스", "인클라인 벤치프레스")         // 10-06: 가슴 (same day, second session)
        clock.advance(Duration.ofDays(1))
        workout(me, "스쿼트", complete = false)               // 10-07: in progress, not on the calendar
        workout(users.signIn("other.user"), "데드리프트")      // someone else's

        calendar("2026-10").andExpect {
            status { isOk() }
            jsonPath("$.month") { value("2026-10") }
            jsonPath("$.days[*].date") { value(contains("2026-10-05", "2026-10-06")) }
            jsonPath("$.days[0].categories[*].name") { value(contains("가슴", "하체")) }
            jsonPath("$.days[1].categories[*].name") { value(contains("가슴", "등")) }
        }
    }

    @Test
    fun `REQ-WORKOUT-007 달을 고르지 않으면 가장 최근에 운동한 달과 날짜를 준다`() {
        workout(me, "벤치프레스")                             // 2026-10-05
        clock.advance(Duration.ofDays(28))
        workout(me, "스쿼트")                                 // 2026-11-02
        clock.advance(Duration.ofDays(1))
        workout(me, "랫풀다운", complete = false)              // in progress, does not count

        calendar().andExpect {
            status { isOk() }
            jsonPath("$.month") { value("2026-11") }
            jsonPath("$.latestDate") { value("2026-11-02") }
            jsonPath("$.days[*].date") { value(contains("2026-11-02")) }
        }
    }

    @Test
    fun `REQ-WORKOUT-007 운동 기록이 하나도 없으면 빈 달력이다`() {
        calendar().andExpect {
            status { isOk() }
            jsonPath("$.month") { value(null) }
            jsonPath("$.latestDate") { value(null) }
            jsonPath("$.days") { isEmpty() }
        }
    }

    @Test
    fun `ERR-014 달 형식이 틀리면 입력값 오류다`() {
        listOf("2026-13", "2026-1", "202610", "october").forEach {
            calendar(it).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
        }
    }

    @Test
    fun `REQ-WORKOUT-008 BR-020 같은 날 완료한 세션은 하나로 합치고 같은 운동은 세트 번호를 이어 매긴다`() {
        val morning = start()                                   // 09:00Z = 18:00 Seoul, 2026-10-05
        val bench = addExercise(morning, "벤치프레스")
        addSet(morning, bench, 60, 10)
        clock.advance(Duration.ofMinutes(2))
        addSet(morning, bench, 65, 8)
        addSet(morning, addExercise(morning, "랫풀다운"), 50, 12)
        clock.advance(Duration.ofMinutes(28))
        complete(morning)                                       // 30 minutes
        clock.advance(Duration.ofMinutes(90))
        val evening = start()                                   // 11:00Z = 20:00 Seoul, same day
        addSet(evening, addExercise(evening, "벤치프레스"), 70, 6)
        addSet(evening, addExercise(evening, "스쿼트"), 100, 5)
        clock.advance(Duration.ofMinutes(20))
        complete(evening)                                       // 20 minutes

        day("2026-10-05").andExpect {
            status { isOk() }
            jsonPath("$.date") { value("2026-10-05") }
            jsonPath("$.durationSeconds") { value(3000) }
            jsonPath("$.exercises[*].name") { value(contains("벤치프레스", "랫풀다운", "스쿼트")) }
            jsonPath("$.exercises[0].sets[*].setNumber") { value(contains(1, 2, 3)) }
            jsonPath("$.exercises[0].sets[*].weight") { value(contains(60.0, 65.0, 70.0)) }
            jsonPath("$.exercises[0].setCount") { value(3) }
            jsonPath("$.exercises[0].volume") { value(1540.0) }          // 600 + 520 + 420
            jsonPath("$.categories[*].name") { value(contains("가슴", "등", "하체")) }
            jsonPath("$.categories[0].volume") { value(1540.0) }
            jsonPath("$.categories[2].exerciseIds") { value(contains(operator.exerciseId("스쿼트", me.id).toString())) }
            jsonPath("$.summary.exerciseCount") { value(3) }
            jsonPath("$.summary.totalSets") { value(5) }
            jsonPath("$.summary.totalRepetitions") { value(41) }
            jsonPath("$.summary.totalVolume") { value(2640.0) }
        }
    }

    @Test
    fun `REQ-MEDIA-002 날짜별 기록에는 그날 붙은 사진·동영상이 붙은 순서대로 주소와 함께 나온다`() {
        val session = start()
        addSet(session, addExercise(session, "벤치프레스"), 60, 10)
        val first = upload(session)
        val second = upload(session)
        upload(session)                                         // staged but not chosen
        complete(session, body = """{"mediaIds": ["$second", "$first"]}""")

        day("2026-10-05").andExpect {
            jsonPath("$.media[*].id") { value(contains(second, first)) }
            jsonPath("$.media[0].mediaType") { value("PHOTO") }
            jsonPath("$.media[0].contentType") { value("image/jpeg") }
            jsonPath("$.media[0].previewUrl") { value("/api/v1/media/$second/preview") }
            jsonPath("$.media[0].displayUrl") { value("/api/v1/media/$second/display") }
            jsonPath("$.media[0].originalUrl") { value("/api/v1/media/$second/original") }
        }
    }

    @Test
    fun `REQ-WORKOUT-008 완료한 운동이 없는 날은 내용 없음이다`() {
        val inProgress = start()
        addSet(inProgress, addExercise(inProgress, "벤치프레스"), 60, 10)

        day("2026-10-05").andExpect { status { isNoContent() } }
        day("2026-10-04").andExpect { status { isNoContent() } }
    }

    @Test
    fun `ERR-015 날짜 형식이 틀리면 입력값 오류다`() {
        listOf("2026-10-32", "2026-1-5", "today").forEach {
            day(it).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
        }
    }

    @Test
    fun `REQ-WORKOUT-005 BR-020 날짜를 지우면 그날 완료한 세션과 파일이 모두 지워지고 진행 중 세션은 남는다`() {
        val morning = start()
        addSet(morning, addExercise(morning, "벤치프레스"), 60, 10)
        val photo = upload(morning)
        complete(morning, body = """{"mediaIds": ["$photo"]}""")
        clock.advance(Duration.ofHours(1))
        val evening = workout(me, "스쿼트")
        clock.advance(Duration.ofHours(1))
        val inProgress = start()                                // same day, still going

        mockMvc.delete("/api/v1/workout-days/2026-10-05") { header("Authorization", "Bearer ${me.token}") }
            .andExpect { status { isNoContent() } }

        day("2026-10-05").andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/workout-sessions/in-progress") { header("Authorization", "Bearer ${me.token}") }
            .andExpect { jsonPath("$.id") { value(inProgress) } }
        listOf(morning, evening).forEach { assertEquals(emptyList(), storage.keys("users/${me.id}/workout-sessions/$it/")) }
        mockMvc.delete("/api/v1/workout-days/2026-10-05") { header("Authorization", "Bearer ${me.token}") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("WORKOUT_DAY_NOT_FOUND") }
        }
    }

    private fun upload(session: String, user: SignedInUser = me): String {
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "jpg", it) }
        return idOf(mockMvc.multipart("/api/v1/workout-sessions/$session/media") {
            file(MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg.toByteArray()))
            header("Authorization", "Bearer ${user.token}")
        }.andExpect { status { isCreated() } })
    }

    private fun day(date: String): ResultActionsDsl =
        mockMvc.get("/api/v1/workout-days/$date") { header("Authorization", "Bearer ${me.token}") }

    private fun start(user: SignedInUser = me): String = idOf(mockMvc.post("/api/v1/workout-sessions") {
        header("Authorization", "Bearer ${user.token}")
        header("X-Time-Zone", "Asia/Seoul")
    })

    private fun addExercise(session: String, name: String, user: SignedInUser = me): String =
        idOf(mockMvc.post("/api/v1/workout-sessions/$session/exercises") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"exerciseId": "${operator.exerciseId(name, user.id)}"}"""
        }, "sessionExerciseId")

    private fun addSet(session: String, sessionExercise: String, weight: Int, repetitions: Int, user: SignedInUser = me) {
        mockMvc.post("/api/v1/workout-sessions/$session/exercises/$sessionExercise/sets") {
            header("Authorization", "Bearer ${user.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"weight": $weight, "repetitions": $repetitions}"""
        }.andExpect { status { isCreated() } }
    }

    private fun complete(session: String, user: SignedInUser = me, body: String? = null) {
        mockMvc.post("/api/v1/workout-sessions/$session/complete") {
            header("Authorization", "Bearer ${user.token}")
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }.andExpect { status { isOk() } }
    }

    private fun calendar(month: String? = null): ResultActionsDsl =
        mockMvc.get("/api/v1/workout-days") {
            header("Authorization", "Bearer ${me.token}")
            if (month != null) param("month", month)
        }

    /** Starts a session, records one 60kg × 10 set per exercise and completes it unless told otherwise. */
    private fun workout(user: SignedInUser, vararg exercises: String, complete: Boolean = true): String {
        val auth = "Bearer ${user.token}"
        val session = idOf(mockMvc.post("/api/v1/workout-sessions") {
            header("Authorization", auth)
            header("X-Time-Zone", "Asia/Seoul")
        })
        exercises.forEach { name ->
            val sessionExercise = idOf(mockMvc.post("/api/v1/workout-sessions/$session/exercises") {
                header("Authorization", auth)
                contentType = MediaType.APPLICATION_JSON
                content = """{"exerciseId": "${operator.exerciseId(name, user.id)}"}"""
            }, "sessionExerciseId")
            mockMvc.post("/api/v1/workout-sessions/$session/exercises/$sessionExercise/sets") {
                header("Authorization", auth)
                contentType = MediaType.APPLICATION_JSON
                content = """{"weight": 60, "repetitions": 10}"""
            }.andExpect { status { isCreated() } }
        }
        if (complete) {
            mockMvc.post("/api/v1/workout-sessions/$session/complete") { header("Authorization", auth) }
                .andExpect { status { isOk() } }
        }
        return session
    }

    private fun idOf(result: ResultActionsDsl, field: String = "id"): String =
        objectMapper.readTree(result.andReturn().response.contentAsString).get(field).asString()
}

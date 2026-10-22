package cloud.jjoon.workout.exercise

import cloud.jjoon.workout.TestcontainersConfiguration
import cloud.jjoon.workout.support.MutableClock
import cloud.jjoon.workout.support.Operator
import cloud.jjoon.workout.support.SignedInUser
import cloud.jjoon.workout.support.TestClockConfiguration
import cloud.jjoon.workout.support.TestUsers
import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

/** Requirements workout-exercise-manage, design workout-exercise-manage. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class ExerciseManageApiTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var operator: Operator
    @Autowired lateinit var users: TestUsers
    @Autowired lateinit var clock: MutableClock
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate

    lateinit var me: SignedInUser
    lateinit var chest: UUID
    lateinit var back: UUID

    @BeforeEach
    fun setUp() {
        operator.deleteAllAccounts()
        clock.reset()
        me = users.signIn("demo.user")
        chest = operator.categoryId("가슴")
        back = operator.categoryId("등")
    }

    @Test
    fun `BR-022 새 계정은 기본 목록 24개를 자기 종목으로 받는다`() {
        val other = users.signIn("other.user")

        assertEquals(24, jdbc.queryForObject("SELECT count(*) FROM exercise WHERE user_id = ?", Int::class.java, me.id))
        assertNotEquals(operator.exerciseId("벤치프레스", me.id), operator.exerciseId("벤치프레스", other.id))
        exercises(chest).andExpect {
            jsonPath("$[*].name") {
                value(contains("벤치프레스", "스미스 벤치프레스", "인클라인 벤치프레스", "스미스 인클라인 벤치프레스", "펙덱 플라이"))
            }
            jsonPath("$[0].target") { value("가슴 중부 타겟") }
        }
    }

    @Test
    fun `REQ-EXERCISE-004 추가한 종목은 그 부위 맨 뒤에 오고 타깃 설명이 없다`() {
        create(chest, "  케이블 크로스오버 ", "Cable Crossover").andExpect {
            status { isCreated() }
            jsonPath("$.id") { isString() }
            jsonPath("$.categoryId") { value(chest.toString()) }
            jsonPath("$.name") { value("케이블 크로스오버") }
            jsonPath("$.nameEn") { value("Cable Crossover") }
            jsonPath("$.target") { value(null) }
        }
        create(chest, "딥스", "  ").andExpect { jsonPath("$.nameEn") { value(null) } }

        exercises(chest).andExpect { jsonPath("$[5:].name") { value(contains("케이블 크로스오버", "딥스")) } }
        categories().andExpect { jsonPath("$[0].exerciseCount") { value(7) } }
    }

    @Test
    fun `BR-022 내가 추가한 종목은 다른 사용자에게 보이지 않는다`() {
        val other = users.signIn("other.user")
        create(chest, "케이블 크로스오버").andExpect { status { isCreated() } }

        exercises(chest, other).andExpect { jsonPath("$[*].name") { value(not(hasItem("케이블 크로스오버"))) } }
        categories(other).andExpect { jsonPath("$[0].exerciseCount") { value(5) } }
    }

    @Test
    fun `ERR-016 부위·종목명이 없거나 길이를 넘거나 없는 부위면 입력값 오류다`() {
        listOf(
            """{"categoryId": "$chest", "name": "   "}""" to "name",
            """{"categoryId": "$chest"}""" to "name",
            """{"categoryId": "$chest", "name": "${"가".repeat(31)}"}""" to "name",
            """{"categoryId": "$chest", "name": "딥스", "nameEn": "${"a".repeat(51)}"}""" to "nameEn",
            """{"name": "딥스"}""" to "categoryId",
            """{"categoryId": "${UUID.randomUUID()}", "name": "딥스"}""" to "categoryId",
        ).forEach { (body, field) ->
            post("/api/v1/exercises", body).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value(field) }
            }
        }
        create(chest, "  ${"가".repeat(30)}  ", "a".repeat(50)).andExpect { status { isCreated() } }
    }

    @Test
    fun `ERR-017 같은 부위에 대소문자만 다른 같은 이름은 추가할 수 없고 다른 부위는 된다`() {
        create(chest, "Cable Fly").andExpect { status { isCreated() } }

        create(chest, " cable fly ").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EXERCISE_NAME_DUPLICATED") }
        }
        create(chest, "벤치프레스").andExpect { status { isConflict() } }
        create(back, "벤치프레스").andExpect { status { isCreated() } }
    }

    @Test
    fun `REQ-EXERCISE-005 이름을 고치면 지난 기록에도 새 이름이 보인다`() {
        val bench = operator.exerciseId("벤치프레스", me.id)
        workout(me, listOf("벤치프레스"))

        update(bench, chest, "플랫 벤치프레스", null).andExpect {
            status { isOk() }
            jsonPath("$.name") { value("플랫 벤치프레스") }
            jsonPath("$.nameEn") { value(null) }
            jsonPath("$.target") { value("가슴 중부 타겟") }
        }

        mockMvc.get("/api/v1/workout-days/2026-10-05") { header("Authorization", "Bearer ${me.token}") }.andExpect {
            jsonPath("$.exercises[0].name") { value("플랫 벤치프레스") }
        }
        exercises(chest).andExpect { jsonPath("$[0].name") { value("플랫 벤치프레스") } }
    }

    @Test
    fun `BR-027 BR-028 부위를 옮기면 새 부위 맨 뒤로 가고 타깃 설명이 지워지고 지난 볼륨도 옮겨 간다`() {
        val bench = operator.exerciseId("벤치프레스", me.id)
        workout(me, listOf("벤치프레스"))

        update(bench, back, "벤치프레스", "Bench Press").andExpect {
            status { isOk() }
            jsonPath("$.categoryId") { value(back.toString()) }
            jsonPath("$.target") { value(null) }
        }

        exercises(back).andExpect { jsonPath("$[6].name") { value("벤치프레스") } }
        exercises(chest).andExpect { jsonPath("$[*].name") { value(not(hasItem("벤치프레스"))) } }
        mockMvc.get("/api/v1/workout-days/2026-10-05") { header("Authorization", "Bearer ${me.token}") }.andExpect {
            jsonPath("$.categories[0].name") { value("등") }
        }
    }

    @Test
    fun `REQ-EXERCISE-005 고칠 때도 같은 부위에 같은 이름은 안 되고 자기 이름은 그대로 둘 수 있다`() {
        val bench = operator.exerciseId("벤치프레스", me.id)

        update(bench, chest, "펙덱 플라이", null).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EXERCISE_NAME_DUPLICATED") }
        }
        update(bench, chest, "BENCH", null).andExpect { status { isOk() } }
        update(bench, chest, "bench", null).andExpect { status { isOk() } }
    }

    @Test
    fun `ERR-003 ERR-009 다른 사용자의 종목은 고치거나 지울 수 없고 없는 종목은 찾을 수 없다`() {
        val othersBench = operator.exerciseId("벤치프레스", users.signIn("other.user").id)

        update(othersBench, chest, "내 것", null).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        delete(othersBench).andExpect { status { isForbidden() } }
        update(UUID.randomUUID(), chest, "없음", null).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("EXERCISE_NOT_FOUND") }
        }
        delete(UUID.randomUUID()).andExpect { status { isNotFound() } }
    }

    @Test
    fun `ERR-003 다른 사용자의 종목은 내 세션에 넣거나 이전 기록을 볼 수 없다`() {
        val othersBench = operator.exerciseId("벤치프레스", users.signIn("other.user").id)
        val session = startSession(me)

        post("/api/v1/workout-sessions/$session/exercises", """{"exerciseId": "$othersBench"}""").andExpect {
            status { isForbidden() }
        }
        mockMvc.get("/api/v1/exercises/$othersBench/records") { header("Authorization", "Bearer ${me.token}") }
            .andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/stats/exercise-volumes") {
            header("Authorization", "Bearer ${me.token}")
            param("exerciseIds", othersBench.toString())
            param("dates", "2026-10-05")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("exerciseIds") }
        }
    }

    @Test
    fun `BR-025 BR-029 종목을 지우면 그 기록도 지워지고 비게 된 완료 세션은 사라진다`() {
        workout(me, listOf("벤치프레스"))                     // 10-05: only the bench press
        clock.advance(Duration.ofDays(1))
        workout(me, listOf("벤치프레스", "스쿼트"))            // 10-06: the squat stays

        delete(operator.exerciseId("벤치프레스", me.id)).andExpect { status { isNoContent() } }

        day("2026-10-05").andExpect { status { isNoContent() } }
        day("2026-10-06").andExpect {
            status { isOk() }
            jsonPath("$.exercises[*].name") { value(contains("스쿼트")) }
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM workout_session WHERE user_id = ?", Int::class.java, me.id))
        exercises(chest).andExpect { jsonPath("$[*].name") { value(not(hasItem("벤치프레스"))) } }
    }

    @Test
    fun `BR-029 진행 중 세션은 그 종목이 빠져도 세트 없이 남는다`() {
        workout(me, listOf("벤치프레스"), complete = false)

        delete(operator.exerciseId("벤치프레스", me.id)).andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/workout-sessions/in-progress") { header("Authorization", "Bearer ${me.token}") }.andExpect {
            status { isOk() }
            jsonPath("$.exercises") { isEmpty() }
        }
    }

    @Test
    fun `BR-030 부위의 마지막 종목도 지울 수 있고 다시 지우면 찾을 수 없다`() {
        val chestIds = jdbc.queryForList(
            "SELECT id FROM exercise WHERE user_id = ? AND exercise_category_id = ?", UUID::class.java, me.id, chest,
        )
        chestIds.forEach { delete(it!!).andExpect { status { isNoContent() } } }

        exercises(chest).andExpect { jsonPath("$") { isEmpty() } }
        categories().andExpect { jsonPath("$[0].exerciseCount") { value(0) } }
        delete(chestIds.first()!!).andExpect { status { isNotFound() } }
    }

    @Test
    fun `NFR-INTEG-003 계정을 지우면 그 사용자의 종목도 지워진다`() {
        create(chest, "케이블 크로스오버").andExpect { status { isCreated() } }

        operator.deleteAccount("demo.user")

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM exercise WHERE user_id = ?", Int::class.java, me.id))
    }

    @Test
    fun `REQ-EXERCISE-007 부위의 종목 순서를 바꾸면 운동 선택 목록이 그 순서를 따른다`() {
        val reversed = chestExerciseIds().reversed()

        reorder(chest, reversed).andExpect { status { isNoContent() } }

        exercises(chest).andExpect {
            jsonPath("$[*].name") {
                value(contains("펙덱 플라이", "스미스 인클라인 벤치프레스", "인클라인 벤치프레스", "스미스 벤치프레스", "벤치프레스"))
            }
        }
    }

    @Test
    fun `BR-031 바꾼 순서는 통계 종목 목록도 따르고 그 뒤에 추가한 종목은 맨 뒤에 온다`() {
        workout(me, listOf("벤치프레스", "펙덱 플라이"))
        reorder(chest, chestExerciseIds().reversed()).andExpect { status { isNoContent() } }

        create(chest, "케이블 크로스오버").andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/stats/exercises") {
            header("Authorization", "Bearer ${me.token}")
            param("categoryId", chest.toString())
        }.andExpect { jsonPath("$[*].name") { value(contains("펙덱 플라이", "벤치프레스")) } }
        exercises(chest).andExpect { jsonPath("$[0].name") { value("펙덱 플라이") } }
        exercises(chest).andExpect { jsonPath("$[5].name") { value("케이블 크로스오버") } }
    }

    @Test
    fun `ERR-018 종목이 빠진 순서는 목록이 바뀐 것으로 보고 순서를 바꾸지 않는다`() {
        val before = chestExerciseIds()

        reorder(chest, before.reversed().drop(1)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EXERCISE_ORDER_OUTDATED") }
        }

        assertEquals(before, chestExerciseIds())
    }

    private fun chestExerciseIds(): List<UUID> =
        JsonPath.read<List<String>>(exercises(chest).andReturn().response.contentAsString, "$[*].id").map(UUID::fromString)

    private fun reorder(categoryId: UUID, exerciseIds: List<UUID>): ResultActionsDsl =
        mockMvc.put("/api/v1/exercise-categories/$categoryId/exercise-order") {
            header("Authorization", "Bearer ${me.token}")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("exerciseIds" to exerciseIds))
        }

    private fun workout(user: SignedInUser, exercises: List<String>, complete: Boolean = true) {
        val session = startSession(user)
        exercises.forEach { name ->
            val sessionExercise = idOf(
                post("/api/v1/workout-sessions/$session/exercises", """{"exerciseId": "${operator.exerciseId(name, user.id)}"}""", user),
                "sessionExerciseId",
            )
            post("/api/v1/workout-sessions/$session/exercises/$sessionExercise/sets", """{"weight": 60, "repetitions": 10}""", user)
                .andExpect { status { isCreated() } }
        }
        if (complete) {
            mockMvc.post("/api/v1/workout-sessions/$session/complete") { header("Authorization", "Bearer ${user.token}") }
                .andExpect { status { isOk() } }
        }
    }

    private fun startSession(user: SignedInUser): String = idOf(mockMvc.post("/api/v1/workout-sessions") {
        header("Authorization", "Bearer ${user.token}")
        header("X-Time-Zone", "Asia/Seoul")
    })

    private fun create(categoryId: UUID, name: String, nameEn: String? = null): ResultActionsDsl =
        post("/api/v1/exercises", body(categoryId, name, nameEn))

    private fun update(exerciseId: UUID, categoryId: UUID, name: String, nameEn: String?): ResultActionsDsl =
        mockMvc.put("/api/v1/exercises/$exerciseId") {
            header("Authorization", "Bearer ${me.token}")
            contentType = MediaType.APPLICATION_JSON
            content = body(categoryId, name, nameEn)
        }

    private fun delete(exerciseId: UUID): ResultActionsDsl =
        mockMvc.delete("/api/v1/exercises/$exerciseId") { header("Authorization", "Bearer ${me.token}") }

    private fun body(categoryId: UUID, name: String, nameEn: String?): String =
        objectMapper.writeValueAsString(mapOf("categoryId" to categoryId, "name" to name, "nameEn" to nameEn))

    private fun post(url: String, json: String, user: SignedInUser = me): ResultActionsDsl = mockMvc.post(url) {
        header("Authorization", "Bearer ${user.token}")
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    private fun day(date: String) = mockMvc.get("/api/v1/workout-days/$date") { header("Authorization", "Bearer ${me.token}") }

    private fun categories(user: SignedInUser = me) =
        mockMvc.get("/api/v1/exercise-categories") { header("Authorization", "Bearer ${user.token}") }

    private fun exercises(categoryId: UUID, user: SignedInUser = me): ResultActionsDsl =
        mockMvc.get("/api/v1/exercises") {
            header("Authorization", "Bearer ${user.token}")
            param("categoryId", categoryId.toString())
        }

    private fun idOf(result: ResultActionsDsl, field: String = "id"): String =
        objectMapper.readTree(result.andReturn().response.contentAsString).get(field).asString()
}

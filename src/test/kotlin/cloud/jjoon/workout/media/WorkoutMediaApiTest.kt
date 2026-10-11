package cloud.jjoon.workout.media

import cloud.jjoon.workout.TestcontainersConfiguration
import cloud.jjoon.workout.support.MutableClock
import cloud.jjoon.workout.support.Operator
import cloud.jjoon.workout.support.SignedInUser
import cloud.jjoon.workout.support.TestClockConfiguration
import cloud.jjoon.workout.support.TestStorage
import cloud.jjoon.workout.support.TestUsers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.Duration
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertEquals

/** Needs `ffmpeg`/`ffprobe` on the PATH (architecture D-TODO-ARCH-008). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class WorkoutMediaApiTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var operator: Operator
    @Autowired lateinit var users: TestUsers
    @Autowired lateinit var clock: MutableClock
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var storage: TestStorage

    lateinit var me: SignedInUser
    lateinit var session: String

    @BeforeEach
    fun setUp() {
        operator.deleteAllAccounts()
        storage.deleteAll()
        clock.reset()
        me = users.signIn("demo.user")
        session = sessionWithSet(me)
    }

    @Test
    fun `REQ-MEDIA-001 사진을 올리면 원본과 미리보기가 세션 아래에 저장된다`() {
        val id = idOf(upload(session, file("photo.jpg", jpeg())).andExpect {
            status { isCreated() }
            jsonPath("$.mediaType") { value("PHOTO") }
            jsonPath("$.contentType") { value("image/jpeg") }
            jsonPath("$.fileSize") { isNumber() }
        })

        assertEquals(
            listOf("media/$id/original", "media/$id/preview.jpg").map { "${prefix(session)}$it" }.sorted(),
            storage.keys(prefix(session)).sorted(),
        )
    }

    @Test
    fun `BR-006 PNG 사진과 MP4 동영상도 올릴 수 있고 형식은 내용으로 판별한다`() {
        upload(session, file("photo.png", png())).andExpect { jsonPath("$.contentType") { value("image/png") } }
        upload(session, file("clip.bin", video(seconds = 2))).andExpect {
            status { isCreated() }
            jsonPath("$.mediaType") { value("VIDEO") }
            jsonPath("$.contentType") { value("video/mp4") }
        }
    }

    @Test
    fun `BR-003 BR-007 완료하면 고른 파일만 고른 순서로 붙고 고르지 않은 임시 파일은 지워진다`() {
        val first = idOf(upload(session, file("1.jpg", jpeg())))
        val second = idOf(upload(session, file("2.jpg", jpeg())))
        val dropped = idOf(upload(session, file("3.jpg", jpeg())))

        complete(session, """{"mediaIds": ["$second", "$first"]}""").andExpect { status { isOk() } }

        assertEquals(
            listOf(second to 1, first to 2),
            jdbc.query("SELECT id, sort_order FROM workout_media ORDER BY sort_order") { rs, _ ->
                rs.getString("id") to rs.getInt("sort_order")
            },
        )
        assertEquals(emptyList(), storage.keys("${prefix(session)}media/$dropped/"))
        assertEquals(4, storage.keys(prefix(session)).size)
    }

    @Test
    fun `BR-007 건너뛰기로 완료하면 올려 둔 임시 파일이 모두 지워진다`() {
        upload(session, file("1.jpg", jpeg()))

        complete(session, body = null).andExpect { status { isOk() } }

        assertEquals(0, mediaCount())
        assertEquals(emptyList(), storage.keys(prefix(session)))
    }

    @Test
    fun `ERR-002 허용하지 않는 형식은 올리지 않는다`() {
        upload(session, file("photo.jpg", "not an image".toByteArray())).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MEDIA_UNSUPPORTED_TYPE") }
        }
        assertEquals(0, mediaCount())
        assertEquals(emptyList(), storage.keys())
    }

    @Test
    fun `ERR-003 사진이 20MB를 넘으면 크기 상한 오류다`() {
        val jpegHeaderThenZeros = ByteArray(20 * 1024 * 1024 + 1).also {
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()).copyInto(it)
        }

        upload(session, file("big.jpg", jpegHeaderThenZeros)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MEDIA_LIMIT_EXCEEDED") }
            jsonPath("$.message") { value("사진은 20MB까지 올릴 수 있습니다.") }
            jsonPath("$.details.limit") { value("FILE_SIZE") }
            jsonPath("$.details.max") { value(20 * 1024 * 1024) }
        }
    }

    @Test
    fun `ERR-003 1분이 넘는 동영상은 길이 상한 오류다`() {
        upload(session, file("long.mp4", video(seconds = 61))).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MEDIA_LIMIT_EXCEEDED") }
            jsonPath("$.details.limit") { value("DURATION") }
        }
    }

    @Test
    fun `ERR-003 한 세션에 5개까지 올릴 수 있다`() {
        val photo = jpeg()
        repeat(5) { upload(session, file("$it.jpg", photo)).andExpect { status { isCreated() } } }

        upload(session, file("6.jpg", photo)).andExpect {
            status { isBadRequest() }
            jsonPath("$.details.limit") { value("COUNT") }
            jsonPath("$.message") { value("사진·동영상은 5개까지 올릴 수 있습니다.") }
        }
    }

    @Test
    fun `ERR-005 완료된 세션에는 올릴 수 없다`() {
        complete(session, body = null)

        upload(session, file("1.jpg", jpeg())).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("WORKOUT_SESSION_NOT_EDITABLE") }
        }
    }

    @Test
    fun `ERR-004 다른 사용자의 세션에는 올릴 수 없다`() {
        val others = sessionWithSet(users.signIn("other.user"))

        upload(others, file("1.jpg", jpeg())).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        assertEquals(emptyList(), storage.keys())
    }

    @Test
    fun `파일 없이 올리면 입력값 오류다`() {
        mockMvc.multipart("/api/v1/workout-sessions/$session/media") { header("Authorization", "Bearer ${me.token}") }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }

    @Test
    fun `BR-004 운동을 취소하면 올려 둔 파일도 지워진다`() {
        upload(session, file("1.jpg", jpeg()))

        mockMvc.delete("/api/v1/workout-sessions/$session") { header("Authorization", "Bearer ${me.token}") }
            .andExpect { status { isNoContent() } }

        assertEquals(0, mediaCount())
        assertEquals(emptyList(), storage.keys(prefix(session)))
    }

    @Test
    fun `BR-007 6시간이 지나 자동 완료된 세션은 임시 파일을 붙이지 않고 지운다`() {
        upload(session, file("1.jpg", jpeg()))
        clock.advance(Duration.ofHours(7))

        mockMvc.post("/api/v1/workout-sessions") {
            header("Authorization", "Bearer ${me.token}")
            header("X-Time-Zone", "Asia/Seoul")
        }.andExpect { status { isCreated() } }

        assertEquals(0, mediaCount())
        assertEquals(emptyList(), storage.keys(prefix(session)))
    }

    @Test
    fun `NFR-SEC-002 완료 요청에 다른 세션의 파일을 넣을 수 없다`() {
        val other = users.signIn("other.user")
        val othersSession = sessionWithSet(other)
        val othersMedia = idOf(upload(othersSession, file("1.jpg", jpeg()), other))

        complete(session, """{"mediaIds": ["$othersMedia"]}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
        assertEquals(listOf("${prefix(othersSession, other)}media/$othersMedia/original"),
            storage.keys(prefix(othersSession, other)).filter { it.endsWith("original") })
    }

    @Test
    fun `REQ-MEDIA-002 붙은 사진의 미리보기는 캐시할 수 있는 JPEG로 받는다`() {
        val id = idOf(upload(session, file("photo.png", png())))
        complete(session, """{"mediaIds": ["$id"]}""")

        val body = download(id, "preview").andExpect {
            status { isOk() }
            content { contentType(MediaType.IMAGE_JPEG) }
            header { string("Cache-Control", "private, max-age=31536000, immutable") }
        }.andReturn().response.contentAsByteArray
        assertEquals(listOf(0xFF, 0xD8, 0xFF), body.take(3).map { it.toInt() and 0xFF })
    }

    @Test
    fun `REQ-MEDIA-003 원본은 올린 파일 그대로 받고 Range를 보내면 그 범위만 받는다`() {
        val photo = jpeg()
        val id = idOf(upload(session, file("photo.jpg", photo)))
        complete(session, """{"mediaIds": ["$id"]}""")

        val whole = download(id, "original").andExpect {
            status { isOk() }
            content { contentType(MediaType.IMAGE_JPEG) }
            header { string("Accept-Ranges", "bytes") }
            header { longValue("Content-Length", photo.size.toLong()) }
        }.andReturn().response.contentAsByteArray
        assertEquals(photo.toList(), whole.toList())

        val part = download(id, "original", range = "bytes=0-99").andExpect {
            status { isPartialContent() }
            header { string("Content-Range", "bytes 0-99/${photo.size}") }
        }.andReturn().response.contentAsByteArray
        assertEquals(photo.take(100), part.toList())
    }

    @Test
    fun `ERR-007 ERR-004 붙지 않은 임시 파일과 지운 날의 파일은 찾을 수 없고 다른 사용자의 파일은 받을 수 없다`() {
        val staged = idOf(upload(session, file("staged.jpg", jpeg())))
        download(staged, "preview").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("MEDIA_NOT_FOUND") }
        }

        complete(session, """{"mediaIds": ["$staged"]}""")
        download(staged, "original", users.signIn("other.user")).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }

        mockMvc.delete("/api/v1/workout-days/2026-10-05") { header("Authorization", "Bearer ${me.token}") }
            .andExpect { status { isNoContent() } }
        download(staged, "original").andExpect { jsonPath("$.code") { value("MEDIA_NOT_FOUND") } }
    }

    private fun prefix(sessionId: String, user: SignedInUser = me) = "users/${user.id}/workout-sessions/$sessionId/"

    private fun mediaCount() = jdbc.queryForObject("SELECT count(*) FROM workout_media", Int::class.java)

    private fun sessionWithSet(user: SignedInUser): String {
        val auth = "Bearer ${user.token}"
        val sessionId = idOf(mockMvc.post("/api/v1/workout-sessions") {
            header("Authorization", auth)
            header("X-Time-Zone", "Asia/Seoul")
        })
        val sessionExercise = idOf(mockMvc.post("/api/v1/workout-sessions/$sessionId/exercises") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"exerciseId": "${operator.exerciseId("벤치프레스", user.id)}"}"""
        }, "sessionExerciseId")
        mockMvc.post("/api/v1/workout-sessions/$sessionId/exercises/$sessionExercise/sets") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"weight": 60, "repetitions": 10}"""
        }.andExpect { status { isCreated() } }
        return sessionId
    }

    private fun download(mediaId: String, kind: String, user: SignedInUser = me, range: String? = null): ResultActionsDsl =
        mockMvc.get("/api/v1/media/$mediaId/$kind") {
            header("Authorization", "Bearer ${user.token}")
            if (range != null) header("Range", range)
        }

    private fun upload(sessionId: String, file: MockMultipartFile, user: SignedInUser = me): ResultActionsDsl =
        mockMvc.multipart("/api/v1/workout-sessions/$sessionId/media") {
            file(file)
            header("Authorization", "Bearer ${user.token}")
        }

    private fun complete(sessionId: String, body: String?) =
        mockMvc.post("/api/v1/workout-sessions/$sessionId/complete") {
            header("Authorization", "Bearer ${me.token}")
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }

    private fun idOf(result: ResultActionsDsl, field: String = "id"): String =
        objectMapper.readTree(result.andReturn().response.contentAsString).get(field).asString()

    // The type a client claims is ignored; the server reads the bytes (architecture 5).
    private fun file(name: String, bytes: ByteArray) = MockMultipartFile("file", name, "application/octet-stream", bytes)

    private fun jpeg() = image("jpg")

    private fun png() = image("png")

    private fun image(format: String): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB), format, it) }.toByteArray()

    private fun video(seconds: Int): ByteArray {
        val out = Files.createTempFile("clip-", ".mp4")
        try {
            val process = ProcessBuilder(
                "ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=duration=$seconds:size=64x48:rate=5",
                "-pix_fmt", "yuv420p", out.toString(),
            ).redirectErrorStream(true).start()
            check(process.waitFor() == 0) { process.inputStream.bufferedReader().readText() }
            return Files.readAllBytes(out)
        } finally {
            Files.deleteIfExists(out)
        }
    }
}

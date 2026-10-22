package cloud.jjoon.workout.exercise.controller

import cloud.jjoon.workout.common.error.BusinessException
import cloud.jjoon.workout.common.error.ErrorCode
import cloud.jjoon.workout.common.error.ErrorResponse
import cloud.jjoon.workout.exercise.repository.ExerciseCategoryRow
import cloud.jjoon.workout.exercise.repository.ExerciseQueryRepository
import cloud.jjoon.workout.exercise.repository.ExerciseRepository
import cloud.jjoon.workout.exercise.repository.ExerciseRow
import cloud.jjoon.workout.exercise.service.ExerciseOrderRequest
import cloud.jjoon.workout.exercise.service.ExerciseRequest
import cloud.jjoon.workout.exercise.service.ExerciseResponse
import cloud.jjoon.workout.exercise.service.ExerciseService
import cloud.jjoon.workout.session.repository.ExerciseRecordPage
import cloud.jjoon.workout.session.repository.WorkoutSessionQueryRepository
import cloud.jjoon.workout.session.service.ExpiredSessionCleaner
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ExerciseController(
    private val exerciseService: ExerciseService,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseQueryRepository: ExerciseQueryRepository,
    private val sessionQueryRepository: WorkoutSessionQueryRepository,
    private val expiredSessionCleaner: ExpiredSessionCleaner,
) {

    /** API-EXERCISE-004: body parts for the home cards, with the user's exercise counts. */
    @GetMapping("/api/v1/exercise-categories")
    fun categories(@AuthenticationPrincipal userId: UUID): List<ExerciseCategoryRow> =
        exerciseQueryRepository.findCategories(userId)

    /** API-EXERCISE-001: one body part's exercises of the user; also the exercise management list (REQ-EXERCISE-003). */
    @GetMapping("/api/v1/exercises")
    fun exercises(@AuthenticationPrincipal userId: UUID, @RequestParam categoryId: UUID): List<ExerciseRow> {
        expiredSessionCleaner.cleanUp(userId) // so the last performed dates include auto-completed sessions
        if (!exerciseQueryRepository.categoryExists(categoryId)) throw BusinessException(ErrorCode.EXERCISE_CATEGORY_NOT_FOUND)
        return exerciseQueryRepository.findByCategory(userId, categoryId)
    }

    /** API-EXERCISE-005 */
    @PostMapping("/api/v1/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal userId: UUID, @RequestBody request: ExerciseRequest): ExerciseResponse =
        exerciseService.create(userId, request)

    /** API-EXERCISE-006 */
    @PutMapping("/api/v1/exercises/{exerciseId}")
    fun update(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable exerciseId: UUID,
        @RequestBody request: ExerciseRequest,
    ): ExerciseResponse = exerciseService.update(userId, exerciseId, request)

    /** API-EXERCISE-007: sessions may be deleted with it, so abandoned ones are settled first (design 3.2). */
    @DeleteMapping("/api/v1/exercises/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal userId: UUID, @PathVariable exerciseId: UUID) {
        expiredSessionCleaner.cleanUp(userId)
        exerciseService.delete(userId, exerciseId)
    }

    /** API-EXERCISE-008 */
    @PutMapping("/api/v1/exercise-categories/{categoryId}/exercise-order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(@AuthenticationPrincipal userId: UUID, @PathVariable categoryId: UUID, @RequestBody request: ExerciseOrderRequest) {
        exerciseService.reorder(userId, categoryId, request.exerciseIds)
    }

    /** API-SET-006: the previous records popup, one completed session per page; none is an empty page (ERR-013, DEC-WORKOUT-032). */
    @GetMapping("/api/v1/exercises/{exerciseId}/records")
    fun records(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable exerciseId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
    ): ExerciseRecordPage {
        if (page < 0) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, errors = listOf(ErrorResponse.FieldError("page", "0 이상이어야 합니다.")))
        }
        expiredSessionCleaner.cleanUp(userId)
        val exercise = exerciseRepository.findByIdOrNull(exerciseId) ?: throw BusinessException(ErrorCode.EXERCISE_NOT_FOUND)
        if (exercise.userId != userId) throw BusinessException(ErrorCode.FORBIDDEN) // BR-022
        return sessionQueryRepository.findRecords(userId, exerciseId, page)
    }
}

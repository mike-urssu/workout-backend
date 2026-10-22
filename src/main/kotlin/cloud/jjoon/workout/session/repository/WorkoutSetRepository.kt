package cloud.jjoon.workout.session.repository

import cloud.jjoon.workout.session.domain.WorkoutSet
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkoutSetRepository : JpaRepository<WorkoutSet, UUID> {
    fun findByIdAndWorkoutSessionExerciseId(id: UUID, workoutSessionExerciseId: UUID): WorkoutSet?
}

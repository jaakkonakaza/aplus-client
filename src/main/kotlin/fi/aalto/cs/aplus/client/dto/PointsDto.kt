package fi.aalto.cs.aplus.client.dto

import fi.aalto.cs.aplus.client.AplusRoutes
import kotlinx.serialization.Serializable

/**
 * `GET /courses/{id}/points/{userId|me}`.
 *
 * Totals and the module, exercise, and submission hierarchy for one user. Stock A+ sends
 * all properties on this class.
 *
 * @property id the user id.
 */
@Serializable
public data class PointsDto(
    val id: Long,
    val url: String? = null,
    val username: String,
    val studentId: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val isExternal: Boolean,
    val tags: List<UserTagDto> = emptyList(),
    val role: String? = null,
    val maxPoints: Int = 0,
    val maxPointsByDifficulty: Map<String, Int> = emptyMap(),
    val submissionCount: Int = 0,
    val points: Int = 0,
    val pointsByDifficulty: Map<String, Int> = emptyMap(),
    val modules: List<PointsModuleDto> = emptyList(),
) {
    /** Every exercise in the tree, flattened, in module order. */
    public fun allExercises(): List<PointsExerciseDto> = modules.flatMap { it.exercises }
}

/**
 * An exercise round inside [PointsDto].
 */
@Serializable
public data class PointsModuleDto(
    val id: Long,
    val name: String,
    val maxPoints: Int = 0,
    val pointsToPass: Int = 0,
    val submissionCount: Int = 0,
    val points: Int = 0,
    val pointsByDifficulty: Map<String, Int> = emptyMap(),
    val passed: Boolean = false,
    val exercises: List<PointsExerciseDto> = emptyList(),
)

/**
 * An exercise inside [PointsModuleDto].
 *
 * @property bestSubmission URL of the best submission, or `null` when nothing was submitted.
 */
@Serializable
public data class PointsExerciseDto(
    val id: Long,
    val url: String? = null,
    val name: String,
    val difficulty: String = "",
    val maxPoints: Int = 0,
    val pointsToPass: Int = 0,
    val submissionCount: Int = 0,
    val points: Int = 0,
    val passed: Boolean = false,
    val official: Boolean = true,
    val bestSubmission: String? = null,
    val submissions: List<String> = emptyList(),
    val submissionsWithPoints: List<SubmissionBriefDto> = emptyList(),
) {
    /**
     * The best submission id parsed from [bestSubmission].
     *
     * Returns `null` if nothing was submitted or the URL has no numeric final segment.
     */
    public val resolvedBestSubmissionId: Long?
        get() = AplusRoutes.idFromApiUrl(bestSubmission)
}

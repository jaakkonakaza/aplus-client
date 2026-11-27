package fi.aalto.cs.aplus.client.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `GET /submissions/{id}`.
 *
 * @property grader the assistant who graded manually, or `null` for automatic grading.
 * @property submissionData submitted form values echoed by the grader. The shape is grader-specific.
 * @property gradingData grader-supplied result data. Null until feedback is revealed.
 * @property grade zero until feedback is revealed, then the awarded grade.
 *
 *   Lowering an exercise's maximum leaves stored grades unchanged, so `grade` can exceed `maxPoints`.
 *   Ratios can exceed one. The manual scale_grade_to command clamps stored grades.
 *   See `exercise/submission_models.py` and `exercise/cache/points.py` in A+.
 * @property feedback `null` until revealed. Empty when revealed with no grader feedback.
 *   The server column is non-null, so [feedbackRevealed] can use this distinction.
 * @property assistantFeedback manual feedback, hidden under the same rule as [feedback].
 */
@Serializable
public data class SubmissionDto(
    val id: Long,
    val url: String? = null,
    val htmlUrl: String? = null,
    val submissionTime: String? = null,
    val grade: Int = 0,
    val status: String? = null,
    val latePenaltyApplied: Double? = null,
    val gradingTime: String? = null,
    val feedback: String? = null,
    val assistantFeedback: String? = null,
    val exercise: SubmissionExerciseDto? = null,
    val submitters: List<UserBriefDto> = emptyList(),
    val files: List<SubmittedFileDto> = emptyList(),
    val grader: UserBriefDto? = null,
    val submissionData: JsonElement? = null,
    val gradingData: JsonElement? = null,
) {
    /**
     * Whether A+ has revealed this submission's feedback to its submitters.
     *
     * A+ withholds [feedback] until it is revealed. The underlying column is never null,
     * so a non-null value indicates revealed feedback.
     */
    public val feedbackRevealed: Boolean get() = feedback != null

}

/** The exercise stub embedded in [SubmissionDto]. */
@Serializable
public data class SubmissionExerciseDto(
    val id: Long,
    val url: String? = null,
    val htmlUrl: String? = null,
    val displayName: String? = null,
    val maxPoints: Int = 0,
    val maxSubmissions: Int = 0,
    val hierarchicalName: String? = null,
    val difficulty: String = "",
)

/**
 * A file attached to a submission.
 *
 * @property paramName the `form_spec` key the file was submitted under.
 */
@Serializable
public data class SubmittedFileDto(
    val id: Long,
    val url: String? = null,
    val filename: String,
    val paramName: String? = null,
)

/**
 * Upstream SubmissionBriefSerializer, also the rows of [PointsExerciseDto.submissionsWithPoints].
 *
 * @property grade zero while feedback is hidden, then the awarded points. Can exceed the exercise's
 *   maximum after a teacher lowers it, as described by [SubmissionDto.grade]. Exercise, module, and
 *   course totals in [PointsDto] also sum these unclamped values.
 */
@Serializable
public data class SubmissionBriefDto(
    public val id: Long,
    public val url: String? = null,
    public val submissionTime: String? = null,
    public val grade: Int = 0,
)

/** Values of upstream A+ Submission.STATUS. DTOs retain raw strings for future statuses. */
public enum class SubmissionStatusDto(public val value: String) {
    INITIALIZED("initialized"),
    WAITING("waiting"),
    READY("ready"),
    ERROR("error"),
    REJECTED("rejected"),
    INVALIDATED("invalidated"),
    UNOFFICIAL("unofficial");

    public companion object {
        public fun parse(value: String?): SubmissionStatusDto? = entries.find { it.value == value }
        public fun isKnown(value: String?): Boolean = parse(value) != null
    }
}

/**
 * Creates a new graded submission using a teacher token.
 *
 * A+'s form has no `grading_data` field, and drops one if sent.
 */
@Serializable
public data class GradeSubmissionRequest(
    public val studentsByUserId: List<Long>,
    public val points: Int,
    public val feedback: String = "",
    public val assistantFeedback: String? = null,
    public val submissionTime: String? = null,
) {
    init {
        require(studentsByUserId.isNotEmpty() && studentsByUserId.all { it > 0 }) { "Positive submitter user ids are required" }
        require(points >= 0) { "Points must not be negative" }
    }
}

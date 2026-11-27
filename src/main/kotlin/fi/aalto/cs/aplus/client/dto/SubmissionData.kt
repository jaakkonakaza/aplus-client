package fi.aalto.cs.aplus.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of the legacy `GET /courses/{id}/submissiondata` export.
 *
 * Columns use PascalCase. Additional per-file columns named after form_spec keys are
 * unmodeled. Group submissions have one row per submitter. [groupedBySubmission]
 * collects those rows.
 *
 * @property grade zero until feedback is revealed, then the stored grade. In
 *   `exercise/api/csv/submission_sheet.py`, submissions_sheet writes
 *   `s.grade if exercise.id in revealed_ids else 0`. Zero can therefore mean hidden
 *   feedback. A revealed grade can exceed the exercise's current maximum.
 * @property tags tag slugs joined with `|`, or an empty string for no tags. A+ always writes
 *   this column with `'|'.join(tags)`. Null means the field was omitted.
 */
@Serializable
public data class SubmissionDataRowDto(
    @SerialName("SubmissionID") val submissionId: Long,
    @SerialName("ExerciseID") val exerciseId: Long,
    @SerialName("UserID") val userId: Long,
    @SerialName("Status") val status: String,
    @SerialName("Grade") val grade: Int = 0,
    @SerialName("Penalty") val penalty: Double? = null,
    @SerialName("Category") val category: String? = null,
    @SerialName("Exercise") val exercise: String? = null,
    @SerialName("Time") val time: String? = null,
    @SerialName("Graded") val gradedTime: String? = null,
    @SerialName("StudentID") val studentId: String? = null,
    @SerialName("Email") val email: String? = null,
    @SerialName("Tags") val tags: String? = null,
    @SerialName("GraderEmail") val graderEmail: String? = null,
    @SerialName("Notified") val notified: Boolean = false,
    @SerialName("NSeen") val notificationSeen: Boolean = false,
)

/**
 * Groups export rows by [SubmissionDataRowDto.submissionId] in server order.
 *
 * The export fills UserID, StudentID, and Email per submitter, giving each group member a
 * row. Submission columns are shared across those rows.
 */
public fun List<SubmissionDataRowDto>.groupedBySubmission(): Map<Long, List<SubmissionDataRowDto>> =
    groupBy { it.submissionId }

/**
 * Submitter ids grouped by submission in server order.
 *
 * Solo submissions map to a single id. See [groupedBySubmission] for the export's
 * per-submitter row format.
 */
public fun List<SubmissionDataRowDto>.submitterIdsBySubmission(): Map<Long, List<Long>> =
    groupedBySubmission().mapValues { (_, rows) -> rows.map { it.userId } }

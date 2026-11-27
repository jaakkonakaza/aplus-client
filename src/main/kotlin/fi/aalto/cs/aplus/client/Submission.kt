package fi.aalto.cs.aplus.client

import kotlinx.serialization.Serializable
import java.io.File

/**
 * One part of a multipart submission.
 *
 * @property fieldName the `form_spec` key of the field, e.g. `file1`.
 * @property fileName the file name shown in A+.
 * @property content the bytes to upload.
 */
public class SubmissionFile(
    public val fieldName: String,
    public val fileName: String,
    public val content: ByteArray,
) {
    public constructor(fieldName: String, file: File) : this(fieldName, file.name, file.readBytes())

    override fun toString(): String = "SubmissionFile($fieldName -> $fileName, ${content.size} bytes)"
}

/**
 * What A+ answered to an accepted submission.
 *
 * @property location the API URL of the new submission, from the `Location` header.
 * @property errors what went wrong while A+ graded the submission. A+ still created it, so it
 *   counts against the submission limit. Empty when grading went through.
 */
public data class SubmitResult(val location: String?, val errors: List<String> = emptyList())

@Serializable
internal class SubmitErrors(val errors: List<String> = emptyList())

package fi.aalto.cs.aplus.client.dto

import kotlinx.serialization.Serializable

/**
 * `GET /courses/{id}`.
 *
 * Most properties are hyperlinks to sub-resources. Client functions cover common requests,
 * and URLs remain available for discovery.
 *
 * @property htmlUrl nullable, as is [endingTime], so consumers can handle missing values without
 *   failing the entire course decode. Both fields are present on stock A+.
 */
@Serializable
public data class CourseDto(
    val id: Long,
    val url: String? = null,
    val htmlUrl: String? = null,
    val code: String? = null,
    val name: String? = null,
    val instanceName: String? = null,
    val language: String? = null,
    val startingTime: String? = null,
    val endingTime: String? = null,
    val visibleToStudents: Boolean = true,
    val groupWorkAllowed: Boolean = false,
    val image: String? = null,
    val exercises: String? = null,
    val tree: String? = null,
    val points: String? = null,
    val students: String? = null,
    val taggings: String? = null,
    val usertags: String? = null,
    val myPoints: String? = null,
    val myData: String? = null,
    val data: String? = null,
    val pendingSubmissionsData: String? = null,
    val aggregateData: String? = null,
    val resultsData: String? = null,
    val bestResultsData: String? = null,
    val groups: String? = null,
    val myGroups: String? = null,
    val news: String? = null,
    val statistics: String? = null,
)

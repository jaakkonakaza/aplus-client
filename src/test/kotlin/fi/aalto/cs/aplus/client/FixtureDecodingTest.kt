package fi.aalto.cs.aplus.client

import fi.aalto.cs.aplus.client.dto.ExerciseBriefDto
import fi.aalto.cs.aplus.client.dto.NewsItemDto
import fi.aalto.cs.aplus.client.dto.groupedBySubmission
import fi.aalto.cs.aplus.client.dto.submitterIdsBySubmission
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * DRF cannot infer fields in exercise listings, points trees or submission exports.
 * These fixtures cover those gaps. Provenance is in `src/test/resources/fixtures/README.md`.
 * [OpenApiConformanceTest] detects when an endpoint gains a schema and can use field comparisons.
 */
class FixtureDecodingTest {

    @Test
    fun `users me decodes the profile and enrolments`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val user = fixtureClient("users_me.json", requests).me()

        assertEquals(517L, user.id)
        assertEquals("student001", user.username)
        assertEquals("s1000001", user.studentId)
        assertEquals("Student 001", user.fullName)
        assertEquals("Student 001", user.displayName)
        assertTrue(user.isExternal)
        assertEquals(listOf(3L), user.enrolledCourses.map { it.id })
        assertEquals("test-course", user.enrolledCourses.single().code)
        assertTrue(user.staffCourses.isEmpty())

        assertEquals("/api/v2/users/me", requests.single().url.encodedPath)
        assertAuthorized(requests.single())
    }

    @Test
    fun `course decodes metadata and sub-resource links`(): Unit = runBlocking {
        val course = fixtureClient("course.json").course(COURSE_ID)

        assertEquals(3L, course.id)
        assertEquals("test-course", course.code)
        assertEquals("Test Course", course.name)
        assertEquals("en", course.language)
        assertTrue(course.visibleToStudents)
        assertTrue(course.groupWorkAllowed)
        assertNull(course.image)
        assertEquals("http://localhost:8000/api/v2/courses/3/points/me", course.myPoints)
        assertEquals("http://localhost:8000/api/v2/courses/3/mygroups", course.myGroups)
        assertEquals("2030-12-31T12:00:00+02:00", course.endingTime)
    }

    @Test
    fun `points decode totals and the submission hierarchy`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val points = fixtureClient("points_plain.json", requests).points(COURSE_ID)

        assertTrue(requests.single().url.parameters.isEmpty())

        assertEquals(517L, points.id)
        assertEquals("Student", points.role)
        assertEquals(1050, points.maxPoints)
        assertEquals(754, points.points)
        assertEquals(27, points.submissionCount)
        assertEquals(listOf("bo", "en", "jaakko"), points.tags.map { it.slug })
        assertEquals(6, points.modules.size)

        val module = points.modules.first()
        assertEquals("1. Python foundations", module.name)
        assertEquals(70, module.points)

        val exercise = module.exercises.first()
        assertEquals(24L, exercise.id)
        assertEquals("1.1 Sequence laboratory", exercise.name)
        assertEquals(40, exercise.maxPoints)
        assertEquals(20, exercise.points)

        val submission = exercise.submissionsWithPoints.single()
        assertEquals(21126L, submission.id)
        assertEquals(20, submission.grade)
        assertEquals(21126L, exercise.resolvedBestSubmissionId)
    }

    @Test
    fun `exercise listing decodes modules and exercises`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val modules = fixtureClient("exercises.json", requests).exerciseModules(COURSE_ID)

        assertEquals(6, modules.size)
        assertTrue(requests.single().url.parameters.isEmpty())

        val module = modules.first()
        assertEquals(11L, module.id)
        assertEquals("1. Python foundations", module.displayName)
        assertTrue(module.isOpen)
        assertFalse(module.isHidden)
        assertNull(module.readingOpeningTime)
        assertEquals("2020-07-01T09:00:00Z", module.openingTime)

        val exercise = module.exercises.first()
        assertEquals(24L, exercise.id)
        assertEquals("1.1 Sequence laboratory", exercise.displayName)
        assertEquals(40, exercise.maxPoints)
        assertEquals(10, exercise.maxSubmissions)
        assertEquals(10, exercise.submissionLimit)
        assertTrue(exercise.hasSubmittableFiles)
        assertNull(ExerciseBriefDto(id = 1, displayName = "e", maxSubmissions = 0).submissionLimit)
    }

    @Test
    fun `exercise detail decodes the grader form`(): Unit = runBlocking {
        val exercise = fixtureClient("exercise_detail.json").exercise(24L)

        assertEquals(24L, exercise.id)
        assertEquals("1.1 Sequence laboratory", exercise.displayName)
        assertEquals("Sequence laboratory", exercise.name)
        assertTrue(exercise.isSubmittable)
        assertEquals(1, exercise.consecutiveOrder)
        assertNull(exercise.parentName)
        assertEquals("unlisted", exercise.status)
        assertEquals(3L, assertNotNull(exercise.course).id)
        assertEquals(
            listOf("file1", "file2", "file3", "file4", "file5", "file6"),
            assertNotNull(exercise.exerciseInfo).formSpec.map { it.key },
        )
    }

    @Test
    fun `form_i18n is keyed by field title and then by language`(): Unit = runBlocking {

        val info = assertNotNull(fixtureClient("exercise_detail.json").exercise(24L).exerciseInfo)

        assertEquals(info.formSpec.map { it.title }.toSet(), info.formI18n.keys)
        assertEquals(mapOf("en" to "main.py"), info.formI18n.getValue("main.py"))
        assertEquals(setOf(LocalizedString.DEFAULT_LANGUAGE), info.fileFieldsByLanguage().keys)
    }

    @Test
    fun `submission detail decodes feedback, submitters and files`(): Unit = runBlocking {
        val submission = fixtureClient("submission_detail.json").submission(21126L)

        assertEquals(21126L, submission.id)
        assertTrue(submission.feedbackRevealed)
        assertEquals(20, submission.grade)
        assertEquals("ready", submission.status)
        assertNull(submission.latePenaltyApplied)
        assertEquals("<p>Deterministic fixture result: 20/40.</p>", submission.feedback)
        assertEquals("", submission.assistantFeedback)
        assertNull(submission.grader)
        assertNull(submission.submissionData)
        assertEquals(24L, assertNotNull(submission.exercise).id)
        assertEquals(listOf(517L), submission.submitters.map { it.id })
        assertEquals("student001", submission.submitters.single().username)
        assertEquals(6, submission.files.size)
        assertEquals("main.py", submission.files.first().filename)
        assertEquals("file1", submission.files.first().paramName)
        assertEquals("""{"fixture":true}""", assertNotNull(submission.gradingData).toString())
    }

    @Test
    fun `legacy submissiondata decodes the PascalCase columns`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val rows = fixtureClient("submissiondata.json", requests).submissionData(COURSE_ID)

        val request = requests.single()
        assertEquals("/api/v2/courses/3/submissiondata/me", request.url.encodedPath)
        assertEquals("no", request.url.parameters["best"])
        assertEquals("json", request.url.parameters["format"])

        assertEquals(27, rows.size)
        val row = rows.first()
        assertEquals(21525L, row.submissionId)
        assertEquals(16L, row.exerciseId)
        assertEquals(517L, row.userId)
        assertEquals("ready", row.status)
        assertEquals(0, row.grade)
        assertNull(row.penalty)
        assertEquals("submit", row.category)
        assertEquals("4.1 Domain model", row.exercise)
        assertEquals("s1000001", row.studentId)
        assertEquals("student001@example.test", row.email)
        assertFalse(row.notified)
    }

    @Test
    fun `per-exercise submissiondata decodes one row per submitter`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val rows = fixtureClient("submissiondata_exercise.json", requests)
            .exerciseSubmissionData(COURSE_ID, exerciseId = 24L)

        val request = requests.single()
        assertEquals("/api/v2/courses/3/submissiondata", request.url.encodedPath)
        assertEquals("24", request.url.parameters["exercise_id"])
        assertEquals("no", request.url.parameters["best"])
        assertEquals("json", request.url.parameters["format"])

        assertEquals(3, rows.size)
        val own = rows.first()
        assertEquals(21126L, own.submissionId)
        assertEquals(24L, own.exerciseId)
        assertEquals(517L, own.userId)
        assertEquals(20, own.grade)
        assertEquals("en|jaakko", own.tags)

        // Group members share submission columns but have separate UserID, StudentID, and Email columns.

        val group = rows.drop(1)
        assertEquals(listOf(21200L, 21200L), group.map { it.submissionId })
        assertEquals(listOf(518L, 519L), group.map { it.userId })
        assertEquals(listOf(null, "s1000003"), group.map { it.studentId })
        assertEquals(0.5, group.first().penalty)
        assertEquals("teacher@example.test", group.first().graderEmail)
        assertTrue(group.first().notified)
        assertFalse(group.first().notificationSeen)
    }

    @Test
    fun `the best filter is sent on the per-exercise export too`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        fixtureClient("submissiondata_exercise.json", requests)
            .exerciseSubmissionData(COURSE_ID, 24L, best = true)

        assertEquals("yes", requests.single().url.parameters["best"])
    }

    @Test
    fun `the student roster decodes tags and course-scoped links`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val students = fixtureClient("students.json", requests).students(COURSE_ID)

        assertEquals("/api/v2/courses/3/students", requests.single().url.encodedPath)
        assertAuthorized(requests.single())

        assertEquals(listOf(517L, 518L), students.map { it.id })
        val first = students.first()
        assertEquals("student001", first.username)
        assertEquals("s1000001", first.studentId)
        assertEquals("student001@example.test", first.email)
        assertEquals("Student 001", first.fullName)
        assertEquals(true, first.isExternal)
        assertEquals(listOf("user-external", "en", "jaakko"), first.tagSlugs)
        assertEquals(
            "http://localhost:8000/test-course/master/teachers/participants/517",
            first.summaryHtml,
        )
        assertEquals("http://localhost:8000/api/v2/courses/3/points/517", first.points)
        assertEquals("http://localhost:8000/api/v2/courses/3/submissiondata/517", first.data)

        assertNull(students.last().studentId)
        assertEquals(listOf("user-internal"), students.last().tagSlugs)
    }

    @Test
    fun `the usertag listing decodes the hardcoded tags without an id`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val tags = fixtureClient("usertags.json", requests).usertags(COURSE_ID)

        assertEquals("/api/v2/courses/3/usertags", requests.single().url.encodedPath)
        assertEquals(listOf("user-internal", "user-external", "en", "jaakko"), tags.map { it.slug })

        // HardcodedUserTag instances are unsaved and have no primary key. Their slugs identify them.

        val internal = tags.first()
        assertNull(internal.id)
        assertNull(internal.url)
        assertNull(internal.taggings)
        assertEquals("internal", internal.name)
        assertTrue(internal.visibleToStudents)
        assertEquals("#006cb4", internal.color)
        assertTrue(internal.fontWhite)

        val created = tags.last()
        assertEquals(14L, created.id)
        assertEquals("http://localhost:8000/api/v2/courses/3/usertags/14", created.url)
        assertEquals("http://localhost:8000/api/v2/courses/3/taggings?tag_id=14", created.taggings)
        assertEquals("", created.description)
        assertFalse(created.visibleToStudents)
        assertEquals("#000", created.fontColor)
        assertFalse(created.fontWhite)
    }

    @Test
    fun `the points tree still decodes tags that carry only the four brief fields`(): Unit = runBlocking {
        // The points tree omits fields present in the full tag listing, so those fields need defaults.

        val tag = fixtureClient("points_plain.json").points(COURSE_ID).tags.first()

        assertEquals(11L, tag.id)
        assertEquals("bo", tag.slug)
        assertEquals("Bo", tag.name)
        assertEquals("", tag.description)
        assertNull(tag.color)
        assertNull(tag.fontColor)
        assertFalse(tag.fontWhite)
        assertFalse(tag.visibleToStudents)
        assertNull(tag.taggings)
    }

    @Test
    fun `user search sends the query and decodes the matching row`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val found = fixtureClient("users_search.json", requests).userSearch("student001@example.test")

        val request = requests.single()
        assertEquals("/api/v2/users", request.url.encodedPath)
        assertEquals("student001@example.test", request.url.parameters["search"])
        assertAuthorized(request)

        val user = found.single()
        assertEquals(517L, user.id)
        assertEquals("student001", user.username)
        assertEquals("student001@example.test", user.email)
        assertEquals("Student 001", user.fullName)
        assertEquals(true, user.isExternal)
    }

    @Test
    fun `empty paginated news and groups decode`(): Unit = runBlocking {
        assertTrue(fixtureClient("news.json").news(COURSE_ID).isEmpty())
        assertTrue(fixtureClient("mygroups.json").myGroups(COURSE_ID).isEmpty())
    }

    @Test
    fun `unrevealed submission detail hides feedback and grade but not the penalty`(): Unit = runBlocking {
        val unrevealed = fixtureClient("submission_unrevealed.json").submission(21126L)
        assertFalse(unrevealed.feedbackRevealed)
        assertNull(unrevealed.feedback)
        assertNull(unrevealed.assistantFeedback)
        assertNull(unrevealed.gradingData)
        assertEquals(0, unrevealed.grade)
        assertEquals(0.4, unrevealed.latePenaltyApplied)
    }

    @Test
    fun `stock news decodes`(): Unit = runBlocking {
        val items = fixtureClient("news_items.json").news(COURSE_ID)

        assertEquals(2, items.size)
        val pinned = items.first()
        assertEquals(5L, pinned.id)
        assertEquals("Course starts", pinned.title)
        assertEquals("<p>Welcome to the course.</p>", pinned.body)
        assertEquals(3, pinned.audience)
        assertEquals(NewsItemDto.LANGUAGE_ALL, pinned.language)
        assertTrue(pinned.pin)
        assertTrue(pinned.appliesTo("fi"))

        val finnish = items.last()
        assertEquals("fi", finnish.language)
        assertFalse(finnish.pin)
        assertTrue(finnish.appliesTo("fi"))
        assertFalse(finnish.appliesTo("en"))
    }

    @Test
    fun `stock mygroups decode`(): Unit = runBlocking {
        val group = fixtureClient("mygroups_members.json").myGroups(COURSE_ID).single()

        assertEquals(41L, group.id)
        assertEquals("2026-01-12T14:05:00+02:00", group.timestamp)
        assertEquals(listOf("Student 001", "Student 002"), group.members.map { it.fullName })
        assertEquals(listOf(1L, 2L), group.members.map { it.id })
        assertEquals(listOf(517L, 518L), group.members.map { it.userIdOrNull })
    }

    @Test
    fun `submission exports retain all submitters in server order`(): Unit = runBlocking {
        val rows = fixtureClient("submissiondata_exercise.json").exerciseSubmissionData(COURSE_ID, 24)
        assertEquals(listOf(21126L, 21200L), rows.groupedBySubmission().keys.toList())
        assertEquals(mapOf(21126L to listOf(517L), 21200L to listOf(518L, 519L)), rows.submitterIdsBySubmission())
    }
}

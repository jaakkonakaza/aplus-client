package fi.aalto.cs.aplus.courseconfig

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

public object CourseConfig {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * A course manifest. IDE settings are optional for template-only consumers such as Hanami.
     * JSON input requires [id], [name], [modules] and [exerciseModules]. Empty module lists and
     * exercise mappings are always written because existing consumers require these fields.
     * The collection defaults apply when constructing a document in Kotlin.
     *
     * JSON examples:
     * [minimal catalog](https://jaakkonakaza.github.io/aplus-client/examples/course-config/minimal.json),
     * [Scala course](https://jaakkonakaza.github.io/aplus-client/examples/course-config/scala.json),
     * [Python course](https://jaakkonakaza.github.io/aplus-client/examples/course-config/python.json).
     *
     * @sample fi.aalto.cs.aplus.courseconfig.samples.minimalManifest
     * @sample fi.aalto.cs.aplus.courseconfig.samples.pluginManifest
     * @property id Course's positive A+ ID within Long range.
     * @property name Course name shown to students.
     * @property modules Downloadable modules, in display order. Student-created modules need no entry.
     * @property exerciseModules Exercise API IDs mapped to language codes and module names. A module
     * may be student-created and therefore absent from [modules].
     * @property aPlusUrl A+ server base URL.
     * @property languages Course language codes, such as fi and en.
     * @property version Plugin version used for compatibility checks, parsed by [ConfigVersion.parse].
     * A+ Courses blocks setup for an older major. Other mismatches trigger notifications.
     * @property resources Optional IDE settings and feedback styling downloads.
     * @property requiredPlugins IntelliJ plugins required by the course.
     * @property vmOptions JVM system properties applied by the plugin.
     * @property optionalCategories Exercise categories excluded from mandatory progress.
     * @property autoInstall Names of downloadable modules installed during course setup.
     * @property scalaRepl Per-module Scala REPL setup.
     * @property callbacks Plugin callback identifiers.
     * @property hiddenElements A+ element IDs hidden from the course view.
     * @property grading Grade progression rules. Omit to hide grade progression.
     * @property alwaysShowGroups Whether to display exercise groups even when their heading is redundant.
     */
    @Serializable
    public data class Document(
        public val id: String,
        public val name: String,
        @Required public val modules: List<Module> = emptyList(),
        @Required public val exerciseModules: Map<Long, Map<String, String>> = emptyMap(),
        public val aPlusUrl: String? = null,
        public val languages: List<String> = emptyList(),
        public val version: String = "1.0",
        public val resources: Resources? = null,
        public val requiredPlugins: List<RequiredPlugin> = emptyList(),
        public val vmOptions: Map<String, String> = emptyMap(),
        public val optionalCategories: List<String> = emptyList(),
        public val autoInstall: List<String> = emptyList(),
        public val scalaRepl: ScalaRepl? = null,
        public val callbacks: Callbacks? = null,
        public val hiddenElements: List<Long> = emptyList(),
        public val grading: Grading? = null,
        public val alwaysShowGroups: Boolean = false,
    ) {
        /**
         * Validates and serializes this manifest as formatted JSON.
         * @throws IllegalArgumentException when authoring validation fails.
         * @see validationErrors
         */
        public fun toJson(): String {
            validate(this)
            return json.encodeToString(serializer(), this)
        }
    }

    /**
     * Downloadable module archive. [version] is parsed by [ConfigVersion.parse].
     * A null [language] supports all course languages.
     *
     * A+ Courses expects a ZIP containing a top-level directory matching [name].
     * For example, Hello.zip contains Hello/Hello.iml and the module's source files.
     * An sbt module contains Hello/build.sbt and its sources under Hello/.
     * @property name Directory and module name used in exercise mappings.
     * @property url Download URL of the module ZIP archive.
     * @property changelog Changes shown to students when a module update is available.
     * @property sbt Import the extracted directory as an sbt project. False loads the module's `.iml` file.
     */
    @Serializable
    public data class Module(
        public val name: String,
        public val url: String,
        public val version: String = "1.0",
        public val language: String? = null,
        public val changelog: String? = null,
        public val sbt: Boolean = false,
    )

    /**
     * Download URLs. Null fields omit the corresponding resources.
     * @property ideSettings Fallback IntelliJ settings archive when the platform-specific URL is absent.
     * @property ideSettingsMac IntelliJ settings archive for macOS.
     * @property ideSettingsLinux IntelliJ settings archive for Linux.
     * @property ideSettingsWindows IntelliJ settings archive for Windows.
     * @property projectSettings Shared IntelliJ project settings archive.
     * @property customProperties A .properties file overriding plugin UI text.
     * @property feedbackCss Stylesheet for rendered exercise feedback.
     */
    @Serializable
    public data class Resources(
        public val ideSettings: String? = null,
        public val ideSettingsMac: String? = null,
        public val ideSettingsLinux: String? = null,
        public val ideSettingsWindows: String? = null,
        public val projectSettings: String? = null,
        public val customProperties: String? = null,
        public val feedbackCss: String? = null,
    )

    /** IntelliJ marketplace/plugin ID and its human-readable display name. */
    @Serializable
    public data class RequiredPlugin(public val name: String, public val id: String)

    /** Commands keyed by module name, plus command-line arguments for the Scala REPL. */
    @Serializable
    public data class ScalaRepl(
        public val initialCommands: Map<String, List<String>> = emptyMap(),
        public val arguments: String? = null,
    )

    /**
     * Callback identifiers run by the plugin after downloading a module.
     * See the plugin's
     * [supported callbacks](https://github.com/Aalto-LeTech/aplus-courses/blob/main/src/main/kotlin/fi/aalto/cs/apluscourses/utils/callbacks/Callbacks.kt).
     * AddModuleWatermark adds student and course details to Python files. Unknown identifiers are ignored.
     */
    @Serializable
    public data class Callbacks(
        public val postDownloadModule: List<String> = emptyList(),
    )

    /**
     * Grade requirements in ascending progression order. Map iteration order is significant.
     * The [O1] style uses A/B/C categories. Higher categories fill lower-category requirements.
     * The [TOTAL] style sums points across categories.
     * Unknown styles are preserved so newer manifests remain readable by older consumers.
     */
    @Serializable
    public data class Grading(public val style: String, public val points: Map<String, Map<String, Int>>) {
        /** Validates known grading styles. Unknown styles remain readable. */
        public fun validate() {
            val errors = gradingErrors(this)
            require(errors.isEmpty()) { errors.joinToString("\n") }
        }

        /**
         * Returns the grade reached with [earned] points per category and what the next grade requires.
         * Returns null for invalid rules, unknown styles, and missing O1 earned categories.
         * Total-style deficits above [Int.MAX_VALUE] are capped at that value.
         * @sample fi.aalto.cs.aplus.courseconfig.samples.gradeProgression
         */
        public fun progress(earned: Map<String, Int>): GradeProgress? = when {
            gradingErrors(this).isNotEmpty() -> null
            style == O1 -> o1Progress(points, earned)
            style == TOTAL -> totalProgress(points, earned)
            else -> null
        }

        public companion object {
            /** A/B/C categories, where surplus B and C fill A, then remaining C fills B. */
            public const val O1: String = "o1"

            /** Thresholds use the `total` key and are compared with the sum of all earned categories. */
            public const val TOTAL: String = "total"
        }
    }

    /**
     * A grade and the next grade's requirements per category. [Grading.TOTAL] reports one `total` category.
     * @property grade Highest grade reached, or null below the lowest grade.
     * @property pointsUntilNext Points still missing for the next grade. Zero at the highest grade.
     * @property maxOfNext Points the next grade requires. Zero at the highest grade.
     */
    public data class GradeProgress(
        public val grade: String?,
        public val pointsUntilNext: Map<String, Int>,
        public val maxOfNext: Map<String, Int>,
    )

    /**
     * Decodes known fields without authoring validation. Consumers validate the fields they require.
     * @throws kotlinx.serialization.SerializationException when JSON cannot be decoded as a manifest.
     * @see validate
     * @see validationErrors
     */
    @JvmStatic
    public fun parse(value: String): Document = json.decodeFromString(Document.serializer(), value)

    /**
     * Reports all manifest validation errors. Consumers apply resource limits.
     * @throws IllegalArgumentException when authoring validation fails.
     * @see validationErrors
     */
    @JvmStatic
    public fun validate(document: Document) {
        val errors = validationErrors(document)
        require(errors.isEmpty()) { errors.joinToString("\n") }
    }

    /** Returns field paths and explanations. Exercise targets may be student-created modules. */
    @JvmStatic
    public fun validationErrors(document: Document): List<String> = buildList {
        fun check(condition: Boolean, path: String, message: String) {
            if (!condition) add("$path: $message")
        }
        check(document.id.isNotBlank(), "id", "must not be blank")
        if (document.id.isNotBlank()) check(document.id.toLongOrNull()?.let { it > 0 } == true, "id", "must be a positive integer within Long range")
        check(document.name.isNotBlank(), "name", "must not be blank")
        check(ConfigVersion.parseOrNull(document.version) != null, "version", ConfigVersion.FORMAT)
        val names = HashSet<String>()
        document.modules.forEachIndexed { index, module ->
            val path = "modules[$index]"
            check(module.name.isNotBlank(), "$path.name", "must not be blank")
            check(module.url.isNotBlank(), "$path.url", "must not be blank")
            check(names.add(module.name), "$path.name", "duplicate module name: ${module.name}")
            check(ConfigVersion.parseOrNull(module.version) != null, "$path.version", ConfigVersion.FORMAT)
        }
        document.exerciseModules.forEach { (id, mappings) ->
            check(id > 0, "exerciseModules[$id]", "exercise id must be positive")
            mappings.forEach { (language, module) ->
                check(language.isNotBlank(), "exerciseModules[$id]", "language must not be blank")
                check(module.isNotBlank(), "exerciseModules[$id][$language]", "module must not be blank")
            }
        }
        document.autoInstall.forEachIndexed { index, name ->
            check(name in names, "autoInstall[$index]", "unknown downloadable module: $name")
        }
        document.grading?.let { addAll(gradingErrors(it)) }
    }

    private const val A = "A"
    private const val B = "B"
    private const val C = "C"

    private fun o1Progress(grades: Map<String, Map<String, Int>>, earned: Map<String, Int>): GradeProgress? {
        var a = earned[A] ?: return null
        var b = earned[B] ?: return null
        var c = earned[C] ?: return null
        val highest = grades.values.lastOrNull() ?: return null

        val neededA = maxOf(0, (highest[A] ?: return null) - a)
        val bForA = minOf(b, neededA)
        val cForA = minOf(c, neededA - bForA)
        a += bForA + cForA
        b -= bForA
        c -= cForA
        val cForB = minOf(c, maxOf(0, (highest[B] ?: return null) - b))
        b += cForB
        c -= cForB

        val effective = mapOf(A to a, B to b, C to c)
        val current = grades.entries.findLast { (_, required) ->
            effective.all { (category, points) -> points >= (required[category] ?: 0) }
        }
        val next = grades.values.firstOrNull { required ->
            effective.any { (category, points) -> points < (required[category] ?: 0) }
        }
        return GradeProgress(
            grade = current?.key,
            pointsUntilNext = next?.mapValues { (category, required) -> maxOf(0, required - (effective[category] ?: 0)) }
                ?: effective.mapValues { 0 },
            maxOfNext = effective.mapValues { (category, _) -> next?.get(category) ?: 0 },
        )
    }

    private fun totalProgress(grades: Map<String, Map<String, Int>>, earned: Map<String, Int>): GradeProgress {
        val total = earned.values.sumOf { it.toLong() }
        val current = grades.entries.findLast { (_, required) -> total >= (required[Grading.TOTAL] ?: 0) }
        val next = grades.values.firstOrNull { required -> total < (required[Grading.TOTAL] ?: 0) }?.get(Grading.TOTAL)
        return GradeProgress(
            grade = current?.key,
            pointsUntilNext = mapOf(
                Grading.TOTAL to ((next?.toLong() ?: total) - total).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            ),
            maxOfNext = mapOf(Grading.TOTAL to (next ?: 0)),
        )
    }

    private fun gradingErrors(grading: Grading): List<String> = buildList {
        val categories = when (grading.style) {
            Grading.O1 -> setOf(A, B, C)
            Grading.TOTAL -> setOf(Grading.TOTAL)
            else -> return@buildList
        }

        fun check(condition: Boolean, path: String, message: String) {
            if (!condition) add("$path: $message")
        }
        check(grading.points.isNotEmpty(), "grading.points", "must contain at least one grade")
        var previous: Map<String, Int>? = null
        grading.points.forEach { (grade, points) ->
            val path = "grading.points[$grade]"
            check(grade.isNotBlank(), path, "grade must not be blank")
            check(points.keys == categories, path, "expected categories: ${categories.joinToString()}")
            points.forEach { (category, value) ->
                check(value >= 0, "$path.$category", "must not be negative")
                check(value >= (previous?.get(category) ?: 0), "$path.$category", "must not decrease between grades")
            }
            previous = points
        }
    }
}

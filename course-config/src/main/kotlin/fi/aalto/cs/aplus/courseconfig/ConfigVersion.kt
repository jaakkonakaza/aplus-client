package fi.aalto.cs.aplus.courseconfig

/**
 * A `major.minor` version, the format of the manifest's `version` fields.
 */
public data class ConfigVersion(public val major: Int, public val minor: Int) : Comparable<ConfigVersion> {
    init {
        require(major >= 0 && minor >= 0) { "version components must not be negative" }
    }

    override fun compareTo(other: ConfigVersion): Int =
        compareValuesBy(this, other, ConfigVersion::major, ConfigVersion::minor)

    override fun toString(): String = "$major.$minor"

    public companion object {
        /**
         * Parses two dot-separated decimal integers, such as `4.12` or `04.012`.
         * Returns null for signs, whitespace, other component counts and values beyond [Int.MAX_VALUE].
         */
        @JvmStatic
        public fun parseOrNull(value: String): ConfigVersion? {
            val parts = value.split('.')
            if (parts.size != 2 || parts.any { part -> part.isEmpty() || part.any { it !in '0'..'9' } }) return null
            return ConfigVersion(parts[0].toIntOrNull() ?: return null, parts[1].toIntOrNull() ?: return null)
        }

        /** Parses like [parseOrNull], throwing [IllegalArgumentException] for invalid input. */
        @JvmStatic
        public fun parse(value: String): ConfigVersion =
            requireNotNull(parseOrNull(value)) { "invalid version \"$value\": $FORMAT" }

        internal const val FORMAT = "expected two non-negative integer components"
    }
}

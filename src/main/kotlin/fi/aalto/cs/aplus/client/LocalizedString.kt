package fi.aalto.cs.aplus.client

/**
 * An A+ multilingual string.
 *
 * A+ stores translations inline as `prefix|en:Text|fi:Teksti|suffix`, where the prefix and
 * suffix sit outside the pipes and belong to every variant. A string without pipes is
 * not localized and is used as-is.
 *
 * The parsing rules are ported from `lib/localization_syntax.py` in the A+ server.
 *
 * ```kotlin
 * LocalizedString("1. |en:Intro|fi:Johdanto|").pick("fi") // "1. Johdanto"
 * LocalizedString("Plain title").pick("fi")               // "Plain title"
 * ```
 */
@JvmInline
public value class LocalizedString(public val raw: String) {

    /**
     * Picks text for [language].
     *
     * Falls back to [DEFAULT_LANGUAGE], then the first tagged variant, then [raw] unchanged.
     */
    public fun pick(language: String): String {
        val parts = raw.split(SEPARATOR)
        if (parts.size <= 2) return raw

        val prefix = parts.first()
        val suffix = parts.last()
        val variants = parts.subList(1, parts.size - 1)

        matching(variants, language)?.let { return prefix + it + suffix }
        if (language != DEFAULT_LANGUAGE) {
            matching(variants, DEFAULT_LANGUAGE)?.let { return prefix + it + suffix }
        }
        variants.firstOrNull { it.contains(TAG_SEPARATOR) }
            ?.let { return prefix + it.substringAfter(TAG_SEPARATOR) + suffix }
        return raw
    }

    override fun toString(): String = raw

    public companion object {
        private const val SEPARATOR = '|'
        private const val TAG_SEPARATOR = ':'

        /** Language A+ falls back to when the requested one is missing. */
        public const val DEFAULT_LANGUAGE: String = "en"

        private fun matching(variants: List<String>, language: String): String? =
            variants.firstOrNull { it.startsWith("$language$TAG_SEPARATOR") }
                ?.substring(language.length + 1)
    }
}

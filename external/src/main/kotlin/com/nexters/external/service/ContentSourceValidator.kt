package com.nexters.external.service

/**
 * Guards the canonical content write path from accidentally persisting local
 * file commands instead of article source text.
 */
object ContentSourceValidator {
    private val transientFilePlaceholder = Regex(
        "^\\s*\\(cat\\s+/tmp/trans_[^\\s)]+\\)\\s*$",
    )

    fun isInvalidSource(content: String): Boolean =
        transientFilePlaceholder.matches(content)
}

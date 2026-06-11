package com.nexters.external.dto

import org.jsoup.Jsoup
import java.time.LocalDateTime

data class RssFeed(
    val title: String,
    val description: String?,
    val link: String,
    val language: String?,
    val publishedDate: LocalDateTime?,
    val items: List<RssItem>
)

data class RssItem(
    val title: String,
    val description: String?,
    val link: String,
    val publishedDate: LocalDateTime?,
    val author: String?,
    val categories: List<String>,
    val content: String?,
    val imageUrl: String? = null,
)

fun RssItem.toContentText(): String =
    buildList {
        description
            ?.cleanHtml()
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it) }

        content
            ?.cleanHtml()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it !in this }
            ?.let { add(it) }

        author
            ?.cleanHtml()
            ?.takeIf { it.isNotBlank() }
            ?.let { add("Author: $it") }

        categories
            .map { it.cleanHtml() }
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.let {
                add("Categories: ${it.joinToString(", ")}")
            }
    }.joinToString("\n\n").trim()

private fun String.cleanHtml(): String {
    if (isBlank()) {
        return ""
    }

    return Jsoup
        .parse(this)
        .text()
        .replace(Regex("\\s+"), " ")
        .trim()
}

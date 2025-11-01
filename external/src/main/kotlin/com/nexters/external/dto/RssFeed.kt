package com.nexters.external.dto

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
    val content: String?
)

fun RssItem.toContentText(): String =
    buildString {
        description?.let {
            val cleanDescription = it.cleanHtml()
            if (cleanDescription.isNotBlank()) {
                append("$cleanDescription\n\n")
            }
        }
        content?.let {
            val cleanContent = it.cleanHtml()
            if (cleanContent.isNotBlank()) {
                append("$cleanContent\n\n")
            }
        }
        author?.let { append("Author: $it\n") }
        if (categories.isNotEmpty()) {
            append("Categories: ${categories.joinToString(", ")}\n")
        }
    }.trim()

private fun String.cleanHtml(): String =
    this
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

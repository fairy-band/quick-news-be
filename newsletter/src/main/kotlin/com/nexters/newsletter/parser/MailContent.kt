package com.nexters.newsletter.parser

data class MailContent(
    val title: String,
    val content: String,
    val link: String,
    val section: String? = null,
)

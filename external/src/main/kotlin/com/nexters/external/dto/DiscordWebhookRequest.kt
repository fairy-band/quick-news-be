package com.nexters.external.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class DiscordWebhookRequest(
    val username: String? = null,
    @JsonProperty("avatar_url")
    val avatarUrl: String? = null,
    val content: String? = null,
    val embeds: List<DiscordEmbed>? = null
)

data class DiscordEmbed(
    val title: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val footer: DiscordEmbedFooter? = null,
    val timestamp: String? = null,
    val fields: List<DiscordEmbedField>? = null
)

data class DiscordEmbedFooter(
    val text: String,
    @JsonProperty("icon_url")
    val iconUrl: String? = null
)

data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean = false
)

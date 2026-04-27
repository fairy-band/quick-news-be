package com.nexters.api.enums

enum class ExploreSortType {
    REGISTERED,
    PUBLISHED,
    ;

    companion object {
        fun from(value: String?): ExploreSortType =
            when (value) {
                null -> REGISTERED
                "published" -> PUBLISHED
                else -> throw IllegalArgumentException("Invalid sort value: '$value'. Allowed values: published")
            }
    }
}

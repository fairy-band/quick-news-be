package com.nexters.api.dto

data class OgImageRequest(
    val title: String,
    val tag: String,
    val newsletterName: String,
    val textColor: String = "#DCFF64"
)

enum class TextColorTheme(
    val color: String,
    val displayName: String
) {
    LIME_GREEN("#DCFF64", "라임 그린"),
    CYAN("#00FFFF", "시안"),
    YELLOW("#FFFF00", "옐로우"),
    ORANGE("#FFA500", "오렌지"),
    PINK("#FF69B4", "핑크"),
    LIGHT_BLUE("#87CEEB", "라이트 블루"),
    LIGHT_GREEN("#90EE90", "라이트 그린"),
    GOLD("#FFD700", "골드"),
    CORAL("#FF7F50", "코랄"),
    VIOLET("#EE82EE", "바이올렛"),
    TURQUOISE("#40E0D0", "터콰이즈"),
    LIME("#32CD32", "라임"),
    MAGENTA("#FF00FF", "마젠타"),
    SPRING_GREEN("#00FF7F", "스프링 그린"),
    DEEP_SKY_BLUE("#00BFFF", "딥 스카이 블루"),
    HOT_PINK("#FF1493", "핫 핑크"),
    LIGHT_SALMON("#FFA07A", "라이트 살몬"),
    PALE_GREEN("#98FB98", "페일 그린")
}

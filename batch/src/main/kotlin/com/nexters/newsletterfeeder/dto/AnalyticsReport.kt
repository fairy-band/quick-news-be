package com.nexters.newsletterfeeder.dto

import java.time.LocalDate

data class AnalyticsReport(
    val date: LocalDate,
    val totalUsers: Long,
    val newUsers: Long,
    val returningUsers: Long,
    val returningUserRate: Double,
    val sessions: Long,
    val pageViews: Long,
    val topNewsletterClicks: List<NewsletterClick>,
    val startDate: LocalDate? = null, // 주간 리포트용
    val endDate: LocalDate? = null, // 주간 리포트용
    val yesterdayTotalUsers: Long? = null, // 어제 총 방문자 수 (재방문율 계산용)
) {
    fun toDiscordMessage(): String =
        """
            📊 **일일 웹사이트 통계 리포트** ($date)

            👥 **방문자 현황**
            • 총 방문자: **${totalUsers.formatNumber()}명**
            • 신규 방문자: **${newUsers.formatNumber()}명**
            • 재방문자: **${returningUsers.formatNumber()}명**
            ${yesterdayTotalUsers?.let { "• 어제 방문자: **${it.formatNumber()}명**" } ?: ""}
            • 재방문율: **${String.format("%.1f", returningUserRate)}%** (어제 방문자 대비)

            📈 **활동 현황**
            • 총 세션: **${sessions.formatNumber()}회**
            • 페이지뷰: **${pageViews.formatNumber()}회**

            📰 **인기 뉴스레터 클릭 TOP 5**
            ${
            topNewsletterClicks.take(5).mapIndexed { index, click ->
                "• ${index + 1}위: **${click.objectId}** (${click.clickCount.formatNumber()}회)"
            }.joinToString("\n            ")
        }
        """.trimIndent()

    fun toWeeklyDiscordMessage(): String =
        """
            📊 **주간 웹사이트 통계 리포트** ($startDate ~ $endDate)

            👥 **방문자 현황**
            • 총 방문자: **${totalUsers.formatNumber()}명**
            • 신규 방문자: **${newUsers.formatNumber()}명**
            • 재방문자: **${returningUsers.formatNumber()}명**
            ${yesterdayTotalUsers?.let { "• 지난 주 방문자: **${it.formatNumber()}명**" } ?: ""}
            • 재방문율: **${String.format("%.1f", returningUserRate)}%** (지난 주 방문자 대비)

            📈 **활동 현황**
            • 총 세션: **${sessions.formatNumber()}회**
            • 페이지뷰: **${pageViews.formatNumber()}회**
            • 일평균 방문자: **${(totalUsers / 7.0).let { String.format("%.1f", it) }}명**
            • 일평균 세션: **${(sessions / 7.0).let { String.format("%.1f", it) }}회**

            📰 **인기 뉴스레터 클릭 TOP 10**
            ${
            topNewsletterClicks.take(10).mapIndexed { index, click ->
                "• ${index + 1}위: **${click.objectId}** (${click.clickCount.formatNumber()}회)"
            }.joinToString("\n            ")
        }
        """.trimIndent()

    private fun Long.formatNumber(): String =
        when {
            this >= 1000000 -> String.format("%.1fM", this / 1000000.0)
            this >= 1000 -> String.format("%.1fK", this / 1000.0)
            else -> this.toString()
        }
}

data class NewsletterClick(
    val objectId: String,
    val clickCount: Long,
)

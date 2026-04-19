package com.nexters.api.batch.service

import com.nexters.external.entity.PopularNewsletterSnapshot
import com.nexters.external.enums.PopularNewsletterSegmentType
import com.nexters.external.enums.PopularNewsletterSnapshotStatus
import com.nexters.external.service.PopularNewsletterObjectIdResolverService
import com.nexters.external.service.PopularNewsletterSnapshotService
import com.nexters.external.service.SavePopularNewsletterSnapshotCommand
import com.nexters.external.service.SavePopularNewsletterSnapshotItemCommand
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class PopularNewsletterRankingBatchService(
    private val googleAnalyticsService: GoogleAnalyticsService,
    private val popularNewsletterObjectIdResolverService: PopularNewsletterObjectIdResolverService,
    private val popularNewsletterSnapshotService: PopularNewsletterSnapshotService,
) {
    private val logger = LoggerFactory.getLogger(PopularNewsletterRankingBatchService::class.java)

    fun rebuildGlobalRanking(
        endDate: LocalDate = LocalDate.now(),
        lookbackDays: Int = 365,
        limit: Int = 20,
    ): PopularNewsletterSnapshot {
        val windowStartDate = endDate.minusDays(lookbackDays.toLong() - 1)

        return try {
            val clicks = googleAnalyticsService.getTopNewsletterClicksForRollingWindow(endDate, lookbackDays, limit)
            val items =
                clicks.mapIndexed { index, click ->
                    val resolution = popularNewsletterObjectIdResolverService.resolveObjectId(click.objectId)

                    SavePopularNewsletterSnapshotItemCommand(
                        rank = index + 1,
                        rawObjectId = click.objectId,
                        clickCount = click.clickCount,
                        resolvedContentId = resolution.resolvedContentId,
                        resolvedExposureContentId = resolution.resolvedExposureContentId,
                        resolutionStatus = resolution.resolutionStatus,
                    )
                }

            val resolvedItemCount = items.count { it.resolvedExposureContentId != null }

            popularNewsletterSnapshotService
                .saveSnapshot(
                    SavePopularNewsletterSnapshotCommand(
                        segmentType = PopularNewsletterSegmentType.GLOBAL,
                        windowStartDate = windowStartDate,
                        windowEndDate = endDate,
                        sourceEventName = NEWSLETTER_CLICK_EVENT_NAME,
                        candidateLimit = limit,
                        resolvedItemCount = resolvedItemCount,
                        status = PopularNewsletterSnapshotStatus.SUCCESS,
                        items = items,
                    ),
                ).also { snapshot ->
                    logger.info(
                        "인기 뉴스레터 랭킹 스냅샷 저장 완료: snapshotId={}, resolvedItemCount={}, candidateLimit={}",
                        snapshot.id,
                        resolvedItemCount,
                        limit,
                    )
                }
        } catch (e: Exception) {
            logger.error("인기 뉴스레터 랭킹 스냅샷 생성 중 오류 발생", e)

            popularNewsletterSnapshotService.saveSnapshot(
                SavePopularNewsletterSnapshotCommand(
                    segmentType = PopularNewsletterSegmentType.GLOBAL,
                    windowStartDate = windowStartDate,
                    windowEndDate = endDate,
                    sourceEventName = NEWSLETTER_CLICK_EVENT_NAME,
                    candidateLimit = limit,
                    resolvedItemCount = 0,
                    status = PopularNewsletterSnapshotStatus.FAILED,
                ),
            )

            throw IllegalStateException("인기 뉴스레터 랭킹 스냅샷 생성에 실패했습니다.", e)
        }
    }

    companion object {
        const val NEWSLETTER_CLICK_EVENT_NAME: String = "click_newsletter"
    }
}

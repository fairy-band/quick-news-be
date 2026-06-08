package com.nexters.newsletter.resolver

import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.CategoryService
import com.nexters.external.service.DailyContentArchiveService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock

@Service
class DailyContentArchiveResolver(
    private val userService: UserService,
    private val categoryService: CategoryService,
    private val dailyContentArchiveService: DailyContentArchiveService,
    private val possibleContentsResolver: PossibleContentsResolver,
    private val recommendationCandidateSelector: RecommendationCandidateSelector,
    private val exposureContentService: ExposureContentService,
) {
    private val lock = ReentrantLock() // instance가 늘어나면 락 구현체 변경 필요, 분산 락으로 전환

    @Transactional
    fun resolveTodayContentArchive(
        userId: Long,
        date: LocalDate = LocalDate.now(),
    ): DailyContentArchive {
        // cache hit 인 경우 락 진입 없이 lock-free 로 반환
        dailyContentArchiveService.findByDateAndUserId(userId, date)?.let { return it }

        lock.lock()
        try {
            // 락 대기 중 다른 스레드가 채워 넣었을 수 있으므로 한 번 더 확인
            dailyContentArchiveService.findByDateAndUserId(userId, date)?.let { return it }

            val user = userService.getUserById(userId)
            val userCategories = user.categories.map { it.id!! }
            val categoryIds =
                userCategories.ifEmpty {
                    categoryService.getAllCategories().map { it.id!! }
                }

            val contents = resolveTodayContents(userId, categoryIds)

            val dailyContentArchive =
                DailyContentArchive(
                    user = DailyContentArchive.UserSnapshot.from(user),
                    date = date,
                    exposureContents = contents,
                )

            return dailyContentArchiveService.saveWithHistory(dailyContentArchive)
        } finally {
            lock.unlock()
        }
    }

    @Transactional
    fun refreshTodayArchives(
        userId: Long,
        date: LocalDate = LocalDate.now()
    ) {
        lock.lock()
        try {
            val archive = dailyContentArchiveService.findByDateAndUserId(userId, date) ?: return

            if (!dailyContentArchiveService.isRefreshAvailable(userId, date)) {
                throw RefreshNotAvailableException("이미 새로고침을 사용했습니다. 하루에 한 번만 가능합니다.")
            }

            dailyContentArchiveService.deleteByDateAndUserId(userId, date)
        } finally {
            lock.unlock()
        }
    }

    private fun resolveTodayContents(
        userId: Long,
        categoryIds: List<Long>,
    ): List<ExposureContent> {
        // TODO: 1차 MVP 유저 정보가 필요할지?
        val candidatePool = possibleContentsResolver.resolveCandidatePoolByCategoryIds(userId, categoryIds)
        val possibleContents = candidatePool.candidates.map { it.candidate }
        val candidateSignalsByExposureContentId =
            candidatePool.candidates.associate { poolItem ->
                poolItem.candidate.exposureContentId to poolItem.signals
            }

        if (possibleContents.isEmpty()) {
            logger.warn("추천 후보가 없습니다. userId: {}", userId)
            return emptyList()
        }

        val keywordWeights = categoryService.getKeywordWeightsByCategoryIds(categoryIds)
        val selectedContents =
            recommendationCandidateSelector.select(
                RecommendationCandidateSelectionRequest(
                    candidates = possibleContents,
                    candidateSignalsByExposureContentId = candidateSignalsByExposureContentId,
                    keywordWeightsByKeyword = keywordWeights,
                    categoryIds = categoryIds,
                    limit = MAX_CONTENT_SIZE,
                ),
            )

        if (selectedContents.size < MAX_CONTENT_SIZE) {
            logger.warn(
                "추천할 콘텐츠가 부족합니다. userId: {}, 선택된 콘텐츠 수: {}, 가능한 콘텐츠 수: {}, 최대 콘텐츠 크기: {}",
                userId,
                selectedContents.size,
                possibleContents.size,
                MAX_CONTENT_SIZE,
            )
        }

        return fetchExposureContents(selectedContents)
    }

    private fun fetchExposureContents(candidates: List<ExposureContentRecommendationCandidateRow>): List<ExposureContent> =
        exposureContentService.getExposureContentsByIdsPreservingOrder(
            candidates.map { it.exposureContentId },
        )

    companion object {
        private val logger = LoggerFactory.getLogger(DailyContentArchiveResolver::class.java)
        private const val MAX_CONTENT_SIZE = 6
    }
}

class RefreshNotAvailableException(
    message: String
) : RuntimeException(message)

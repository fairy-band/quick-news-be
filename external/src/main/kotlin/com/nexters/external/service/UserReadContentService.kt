package com.nexters.external.service

import com.nexters.external.entity.UserReadContent
import com.nexters.external.repository.UserReadContentRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserReadContentService(
    private val userReadContentRepository: UserReadContentRepository,
) {
    private val logger = LoggerFactory.getLogger(UserReadContentService::class.java)

    @Transactional
    fun recordRead(userId: Long, exposureContentId: Long, contentId: Long): UserReadContent {
        val existing = userReadContentRepository.findByUserIdAndExposureContentId(userId, exposureContentId)
        if (existing != null) {
            existing.incrementReadCount()
            return userReadContentRepository.save(existing)
        }

        return try {
            userReadContentRepository.save(
                UserReadContent(
                    userId = userId,
                    exposureContentId = exposureContentId,
                    contentId = contentId,
                    readCount = 1,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            logger.warn("중복 읽기 동시 요청 감지 (userId: $userId, exposureContentId: $exposureContentId), 재조회 후 갱신")
            val record = userReadContentRepository.findByUserIdAndExposureContentId(userId, exposureContentId)
            if (record != null) {
                record.incrementReadCount()
                userReadContentRepository.save(record)
            } else {
                throw e
            }
        }
    }

    @Transactional(readOnly = true)
    fun getReadExposureContentIds(userId: Long, exposureContentIds: Collection<Long>): Set<Long> {
        if (exposureContentIds.isEmpty()) return emptySet()
        return userReadContentRepository
            .findAllByUserIdAndExposureContentIdIn(userId, exposureContentIds)
            .map { it.exposureContentId }
            .toSet()
    }

    @Transactional(readOnly = true)
    fun getRecentReadContentIds(userId: Long, limit: Int = 20): List<Long> {
        return userReadContentRepository.findRecentReadContentIdsByUserId(
            userId = userId,
            pageable = PageRequest.of(0, limit)
        )
    }
}

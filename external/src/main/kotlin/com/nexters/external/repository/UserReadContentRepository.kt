package com.nexters.external.repository

import com.nexters.external.entity.UserReadContent
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserReadContentRepository : JpaRepository<UserReadContent, Long> {

    fun findByUserIdAndExposureContentId(userId: Long, exposureContentId: Long): UserReadContent?

    fun findAllByUserIdAndExposureContentIdIn(
        userId: Long,
        exposureContentIds: Collection<Long>
    ): List<UserReadContent>

    @Query(
        """
        SELECT r.contentId FROM UserReadContent r
        WHERE r.userId = :userId
        ORDER BY r.createdAt DESC
        """
    )
    fun findRecentReadContentIdsByUserId(
        @Param("userId") userId: Long,
        pageable: Pageable
    ): List<Long>
}

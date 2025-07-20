package com.nexters.external.repository

import com.nexters.external.entity.ReservedKeyword
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ReservedKeywordRepository : JpaRepository<ReservedKeyword, Long> {
    fun findByName(name: String): ReservedKeyword?

    @Query(
        """
        SELECT rk from ReservedKeyword rk
            JOIN CategoryKeywordMapping ckm on ckm.keyword.id = rk.id
            JOIN Category c on ckm.category.id = c.id
            where c.id = :categoryId
            ORDER BY ckm.weight
        """
    )
    fun findReservedKeywordsByCategoryId(categoryId: Long): List<ReservedKeyword>
}

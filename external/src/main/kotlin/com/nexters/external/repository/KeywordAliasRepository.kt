package com.nexters.external.repository

import com.nexters.external.entity.KeywordAlias
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface KeywordAliasRepository : JpaRepository<KeywordAlias, Long> {
    fun findByEnabledTrue(): List<KeywordAlias>
}

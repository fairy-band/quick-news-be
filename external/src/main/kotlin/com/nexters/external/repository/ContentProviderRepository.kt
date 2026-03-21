package com.nexters.external.repository

import com.nexters.external.entity.ContentProvider
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository

@Repository
interface ContentProviderRepository : JpaRepository<ContentProvider, Long> {
    fun findByName(name: String): ContentProvider?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByNameWithLock(name: String): ContentProvider?
}

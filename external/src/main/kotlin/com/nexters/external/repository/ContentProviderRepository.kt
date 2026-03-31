package com.nexters.external.repository

import com.nexters.external.entity.ContentProvider
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ContentProviderRepository : JpaRepository<ContentProvider, Long> {
    fun findByName(name: String): ContentProvider?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cp FROM ContentProvider cp WHERE cp.name = :name")
    fun findByNameWithLock(name: String): ContentProvider?
}

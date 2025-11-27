package com.nexters.external.repository

import com.nexters.external.entity.ContentProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContentProviderRepository : JpaRepository<ContentProvider, Long> {
    fun findByName(name: String): ContentProvider?
}

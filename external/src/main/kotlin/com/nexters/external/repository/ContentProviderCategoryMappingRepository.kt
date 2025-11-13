package com.nexters.external.repository

import com.nexters.external.entity.ContentProviderCategoryMapping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContentProviderCategoryMappingRepository : JpaRepository<ContentProviderCategoryMapping, Long>

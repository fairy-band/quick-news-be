package com.nexters.external.repository

import com.nexters.external.entity.WalkthroughCategoryMapping
import org.springframework.data.jpa.repository.JpaRepository

interface WalkthroughCategoryMappingRepository : JpaRepository<WalkthroughCategoryMapping, Long>

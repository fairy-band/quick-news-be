package com.nexters.external.repository

import com.nexters.external.entity.UserExposedContentMapping
import org.springframework.data.jpa.repository.JpaRepository

interface UserExposedContentMappingRepository : JpaRepository<UserExposedContentMapping, Long>

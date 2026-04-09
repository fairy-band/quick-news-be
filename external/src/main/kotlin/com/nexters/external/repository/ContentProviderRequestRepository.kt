package com.nexters.external.repository

import com.nexters.external.entity.ContentProviderRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContentProviderRequestRepository : JpaRepository<ContentProviderRequest, Long>

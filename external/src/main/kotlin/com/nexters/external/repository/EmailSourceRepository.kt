package com.nexters.external.repository

import com.nexters.external.entity.EmailSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EmailSourceRepository : JpaRepository<EmailSource, Long> {
    fun findByIsActiveTrueOrderByPriorityDesc(): List<EmailSource>
    fun findBySenderEmail(senderEmail: String): EmailSource?
}

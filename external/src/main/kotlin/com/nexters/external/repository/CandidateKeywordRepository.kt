package com.nexters.external.repository

import com.nexters.external.entity.CandidateKeyword
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CandidateKeywordRepository : JpaRepository<CandidateKeyword, Long>

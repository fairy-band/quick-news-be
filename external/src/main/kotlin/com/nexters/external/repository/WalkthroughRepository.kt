package com.nexters.external.repository

import com.nexters.external.entity.Walkthrough
import org.springframework.data.jpa.repository.JpaRepository

interface WalkthroughRepository : JpaRepository<Walkthrough, Long>

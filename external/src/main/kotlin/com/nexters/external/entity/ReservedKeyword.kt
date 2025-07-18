package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "reserved_keywords")
class ReservedKeyword(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val name: String,
    @ManyToMany
    @JoinTable(
        name = "keyword_mappings",
        joinColumns = [JoinColumn(name = "reserved_keyword_id")],
        inverseJoinColumns = [JoinColumn(name = "candidate_keyword_id")]
    )
    val candidateKeywords: MutableSet<CandidateKeyword> = mutableSetOf(),
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

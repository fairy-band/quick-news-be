package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users") // Using "users" instead of "user" as "user" is a reserved keyword in PostgreSQL
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val deviceToken: String,
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_category_mappings",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "category_id")]
    )
    val categories: Set<Category> = emptySet(),
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

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

@Entity
@Table(name = "content_provider")
class ContentProvider(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    val name: String,
    // 구독 url
    @Column(nullable = false)
    val channel: String,
    // 언어
    @Column(nullable = false, length = 10)
    val language: String,
    @ManyToMany
    @JoinTable(
        name = "content_provider_category",
        joinColumns = [JoinColumn(name = "content_provider_id")],
        inverseJoinColumns = [JoinColumn(name = "category_id")]
    )
    var categories: MutableSet<Category> = mutableSetOf(),
)

package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "walkthrough_category_mappings")
class WalkthroughCategoryMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "walkthrough_id", nullable = false)
    val walkthroughId: Long,

    @Column(name = "category_id", nullable = false)
    val categoryId: Long,
)

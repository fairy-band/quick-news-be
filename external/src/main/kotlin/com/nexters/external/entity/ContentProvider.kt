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

/**
 * 뉴스레터 메타 데이터 저장
 * 원본 데이터 -> 몽고 조회
 * 어떤 뉴스레터가 있는지, 그리고 distinct하여 유니크하게
 * 어떤 뉴스레터가 어떤 카테로리에 적합한지가 중요
 * 카드 원문 언어
 * Calculator가 우선 순위
 */

@Entity
@Table(name = "content_provider")
class ContentProvider(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = true, name = "newsletter_source_id")
    val newsletterSourceId: String? = null,
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_category_mappings",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "category_id")]
    )
    var categories: MutableSet<Category> = mutableSetOf<Category>(),
    @Column(nullable = false)
    val name: String,
    @Column(nullable = false)
    val channel: String,
    @Column(nullable = false, length = 10)
    val language: String,
){

}

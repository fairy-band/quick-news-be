package com.nexters.external.entity

import com.nexters.external.enums.PopularNewsletterSegmentType
import com.nexters.external.enums.PopularNewsletterSnapshotStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Comment("인기 뉴스레터 랭킹 스냅샷 메타데이터")
@Table(
    name = "popular_newsletter_snapshots",
    indexes = [
        Index(
            name = "idx_popular_newsletter_snapshot_segment_generated_at",
            columnList = "segment_type, segment_key, generated_at",
        ),
    ],
)
class PopularNewsletterSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("스냅샷 식별자")
    val id: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "segment_type", nullable = false, length = 20)
    @Comment("랭킹 집계 세그먼트 유형")
    val segmentType: PopularNewsletterSegmentType,
    @Column(name = "segment_key")
    @Comment("세그먼트 상세 키")
    val segmentKey: String? = null,
    @Column(name = "window_start_date", nullable = false)
    @Comment("랭킹 집계 시작일")
    val windowStartDate: LocalDate,
    @Column(name = "window_end_date", nullable = false)
    @Comment("랭킹 집계 종료일")
    val windowEndDate: LocalDate,
    @Column(name = "generated_at", nullable = false)
    @Comment("랭킹 스냅샷 생성 시각")
    val generatedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "source_event_name", nullable = false)
    @Comment("랭킹 집계에 사용한 이벤트 이름")
    val sourceEventName: String,
    @Column(name = "candidate_limit", nullable = false)
    @Comment("랭킹 후보 최대 개수")
    val candidateLimit: Int,
    @Column(name = "resolved_item_count", nullable = false)
    @Comment("실제 콘텐츠로 해석된 아이템 수")
    val resolvedItemCount: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Comment("스냅샷 생성 결과 상태")
    val status: PopularNewsletterSnapshotStatus,
)

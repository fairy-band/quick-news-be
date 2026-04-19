package com.nexters.external.entity

import com.nexters.external.enums.PopularNewsletterResolutionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

@Entity
@Comment("인기 뉴스레터 스냅샷 개별 랭킹 아이템")
@Table(
    name = "popular_newsletter_snapshot_items",
    indexes = [
        Index(
            name = "idx_popular_newsletter_snapshot_items_snapshot_resolution_rank",
            columnList = "snapshot_id, resolution_status, rank_order",
        ),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_popular_newsletter_snapshot_item_snapshot_rank",
            columnNames = ["snapshot_id", "rank_order"],
        ),
    ],
)
class PopularNewsletterSnapshotItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.EAGER)
    val snapshot: PopularNewsletterSnapshot,
    @Column(name = "rank_order", nullable = false)
    @Comment("랭킹 순위")
    val rank: Int,
    @Column(name = "raw_object_id", nullable = false)
    @Comment("GA 이벤트에 실려 올라온 raw한 object_id")
    val rawObjectId: String,
    @Column(name = "click_count", nullable = false)
    @Comment("집계된 클릭 수")
    val clickCount: Long,
    @Column(name = "resolved_content_id")
    @Comment("해석된 콘텐츠 id")
    val resolvedContentId: Long? = null,
    @Column(name = "resolved_exposure_content_id")
    @Comment("해석된 노출 콘텐츠 id")
    val resolvedExposureContentId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false, length = 20)
    @Comment("object id 해석 상태")
    val resolutionStatus: PopularNewsletterResolutionStatus,
    @Column(nullable = false, name = "created_at")
    @Comment("레코드 생성 시각")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false, name = "updated_at")
    @Comment("레코드 수정 시각")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

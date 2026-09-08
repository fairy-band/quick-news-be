package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_read_contents",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_user_read_contents_user_exposure",
            columnNames = ["user_id", "exposure_content_id"]
        )
    ]
)
class UserReadContent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, name = "user_id")
    val userId: Long,

    @Column(nullable = false, name = "exposure_content_id")
    val exposureContentId: Long,

    @Column(nullable = false, name = "content_id")
    val contentId: Long,

    @Column(nullable = false, name = "read_count")
    var readCount: Int = 1,

    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false, name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun incrementReadCount() {
        this.readCount += 1
        this.updatedAt = LocalDateTime.now()
    }
}

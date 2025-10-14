package com.nexters.external.repository

import com.nexters.external.entity.DailyContentArchive
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface DailyContentArchiveRepository : MongoRepository<DailyContentArchive, String> {
    // 날짜와 사용자 ID로 조회
    fun findByDateAndUserId(
        date: LocalDate,
        userId: Long
    ): DailyContentArchive?

    // 사용자 ID와 날짜로 데이터가 존재하는지 확인
    fun existsByUserIdAndDate(
        userId: Long,
        date: LocalDate
    ): Boolean

    // 날짜와 사용자 ID로 삭제
    fun deleteByDateAndUserId(
        date: LocalDate,
        userId: Long
    )
}

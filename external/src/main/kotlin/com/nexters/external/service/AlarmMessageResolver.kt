package com.nexters.external.service

import org.springframework.stereotype.Service
import java.time.LocalTime
import kotlin.math.abs

@Service
class AlarmMessageResolver {
    fun resolveTodayMessage(deviceToken: String): String {
        val currentHour = LocalTime.now().hour

        val messages =
            if (isNightTime(currentHour)) {
                // 밤 시간대 메시지 (18시 ~ 5시)
                listOf(
                    "하루를 마무리하며 오늘의 뉴스를 확인해보세요 🌙",
                    "오늘 놓친 중요한 소식들이 있어요 ⭐",
                    "잠들기 전 세상의 이야기를 들어보세요 📰",
                    "하루의 마지막, 오늘의 핫한 뉴스 체크 🔥",
                    "내일을 위한 정보 업데이트가 도착했어요 🌃"
                )
            } else {
                // 아침/낮 시간대 메시지 (6시 ~ 17시)
                listOf(
                    "좋은 아침! 오늘의 핫한 뉴스가 기다리고 있어요 ☀️",
                    "새로운 하루, 새로운 소식을 확인해보세요 🌅",
                    "오늘 하루 시작하기 전, 세상 소식 체크하기 📱",
                    "아침 커피와 함께 오늘의 뉴스는 어떠세요? ☕",
                    "활기찬 하루를 위한 최신 소식이 도착했어요 🚀"
                )
            }

        // deviceToken 해시를 이용해 사용자별로 일관된 메시지 선택
        val messageIndex = abs(deviceToken.hashCode()) % messages.size

        // TODO: 메시지를 오늘의 추천 컨텐츠와 연계할지 결정이 필요. 연계하면 항상 오늘의 추천 컨텐츠가 새로 생성되므로 비용이 증가
        return messages[messageIndex]
    }

    private fun isNightTime(hour: Int): Boolean {
        // 밤 시간: 18시 ~ 23시, 0시 ~ 5시
        return hour >= 18 || hour <= 5
    }
}

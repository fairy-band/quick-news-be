package com.nexters.external.service

import org.springframework.stereotype.Service

@Service
class AlarmMessageResolver {
    fun resolveTodayMessage(deviceToken: String): String {
        // 아침 알람 메시지만 사용 (매일 8시 발송)
        val messages =
            listOf(
                "좋은 아침! 오늘의 핫한 뉴스가 기다리고 있어요 ☀️",
                "새로운 하루, 새로운 소식을 확인해보세요 🌅",
                "오늘 하루 시작하기 전, 세상 소식 체크하기 📱",
                "아침 커피와 함께 오늘의 뉴스는 어떠세요? ☕",
                "활기찬 하루를 위한 최신 소식이 도착했어요 🚀",
                "모닝 브리핑! 오늘 알아야 할 소식들이 준비됐어요 📰",
                "상쾌한 아침, 따끈따끈한 뉴스로 시작해보세요 🌤️",
                "오늘도 화이팅! 최신 소식과 함께 하루를 열어요 💪",
                "일어나세요~ 세상이 어떻게 바뀌었는지 확인해보세요 🌍",
                "굿모닝! 오늘의 트렌드를 놓치지 마세요 📈",
                "새벽 이슬처럼 신선한 뉴스가 도착했어요 🌿",
                "하루의 시작, 똑똑한 정보 업데이트 받아보세요 🧠",
                "아침 햇살과 함께 온 따뜻한 소식들이에요 🌞",
                "오늘 하루를 더 풍성하게 만들어줄 뉴스 📚",
                "모든 것이 새로운 아침, 새로운 이야기를 만나보세요 ✨"
            )

        // 매번 랜덤하게 메시지 선택
        val messageIndex = messages.indices.random()

        // TODO: 메시지를 오늘의 추천 컨텐츠와 연계할지 결정이 필요. 연계하면 항상 오늘의 추천 컨텐츠가 새로 생성되므로 비용이 증가
        return messages[messageIndex]
    }
}

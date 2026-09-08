package com.nexters.external.filter

import com.nexters.external.entity.Content

/**
 * 광고성, 프로모션, 채용 공고, 시스템 확인 메일 등을 차단하는 필터
 */
class AdAndPromotionalFilter : ContentFilter {
    override fun filter(content: Content): FilterResult {
        val check = checkPromotional(content.title, content.content)
        return if (check != null) {
            FilterResult.Fail(check)
        } else {
            FilterResult.Pass
        }
    }

    override fun getName(): String = "AdAndPromotionalFilter"

    companion object {
        private val AD_TITLE_REGEXES = listOf(
            Regex("""\[광고\]|\(광고\)|\[AD\]|\(AD\)|\[Sponsored\]|\bSponsored\b|\bSponsor\b|\bPROMO\b""", RegexOption.IGNORE_CASE),
            Regex("""Special Offer|\bDiscount\b|\bSale\b|\bEarly Bird\b|얼리버드|특가\b""", RegexOption.IGNORE_CASE),
            Regex("""수강생 모집|웨비나 신청|사전 등록|설문조사|참가자 모집""", RegexOption.IGNORE_CASE),
            Regex("""구독 확인|이메일 인증|Welcome to|Confirm your subscription|Subscription Confirmed""", RegexOption.IGNORE_CASE),
            Regex("""채용 공고|\b(Job Openings|We're hiring)\b""", RegexOption.IGNORE_CASE),
            Regex("""이벤트 안내|\b(쿠폰|기프티콘|사은품|무료 증정)\b""", RegexOption.IGNORE_CASE),
        )

        private val PURE_CONFIRMATION_REGEX = Regex(
            """confirm your subscription|welcome to|subscription confirmed|인증번호|구독을 환영합니다""",
            RegexOption.IGNORE_CASE
        )

        private val SHORT_BODY_PROMO_KEYWORDS = listOf("sponsor", "sponsored", "discount", "use code", "coupon", "할인", "수신거부")

        /**
         * 제목과 본문을 기반으로 광고/스폰서 여부를 검사합니다.
         * @return 광고인 경우 사유 문자열, 정상인 경우 null
         */
        fun checkPromotional(title: String?, content: String?): String? {
            val trimmedTitle = title?.trim().orEmpty()
            val trimmedContent = content?.trim().orEmpty()

            // 1. 제목에 명시적 광고/프로모션 키워드 포함 검사
            for (regex in AD_TITLE_REGEXES) {
                val match = regex.find(trimmedTitle)
                if (match != null) {
                    return "Title matched promotional pattern: '${match.value}'"
                }
            }

            // 2. 시스템/구독 확인 메일 검사
            if (PURE_CONFIRMATION_REGEX.containsMatchIn(trimmedTitle)) {
                return "Title matched system confirmation pattern"
            }

            // 3. 본문 300자 미만의 짧은 홍보/스폰서 글
            if (trimmedContent.length in 1..300) {
                val lowerContent = trimmedContent.lowercase()
                for (kw in SHORT_BODY_PROMO_KEYWORDS) {
                    if (lowerContent.contains(kw)) {
                        return "Short body (${trimmedContent.length} chars) contains promo keyword: '$kw'"
                    }
                }
            }

            return null
        }

        /**
         * 단순 Boolean 판별 메서드
         */
        fun isPromotional(title: String?, content: String?): Boolean =
            checkPromotional(title, content) != null
    }
}

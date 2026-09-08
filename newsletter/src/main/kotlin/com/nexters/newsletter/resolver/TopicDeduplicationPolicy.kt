package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import org.springframework.stereotype.Component

@Component
class TopicDeduplicationPolicy {

    /**
     * 이미 선정된 후보군(selected)과 비교하여 해당 candidate의 토픽 중복 감쇠 계수(Multiplier)를 계산합니다.
     * MMR(Maximal Marginal Relevance) 기반으로 코사인 거리가 가까울수록(유사도가 높을수록) 점수를 감쇠시킵니다.
     */
    fun calculateDampingMultiplier(
        candidate: ExposureContentRecommendationCandidateRow,
        selected: Collection<ExposureContentRecommendationCandidateRow>,
        similarityMap: Map<Long, Map<Long, Double>> = emptyMap(),
    ): Double {
        if (selected.isEmpty()) return 1.0

        val maxCosineSimilarity = getMaxCosineSimilarity(candidate, selected, similarityMap)
        if (maxCosineSimilarity > 0.0) {
            return if (maxCosineSimilarity > SIMILARITY_THRESHOLD) {
                val ratio = (maxCosineSimilarity - SIMILARITY_THRESHOLD) / (1.0 - SIMILARITY_THRESHOLD)
                maxOf(MIN_MULTIPLIER, 1.0 - ratio * DAMPING_SLOPE)
            } else {
                1.0
            }
        }

        // 임베딩 정보가 없는 경우(콜드 스타트) 어휘적 토픽 중복 감지로 폴백
        return if (hasLexicalTopicOverlap(candidate, selected)) {
            LEXICAL_DUPLICATE_MULTIPLIER
        } else {
            1.0
        }
    }

    /**
     * candidate가 이미 선택된 목록(selected)과 동일한 토픽인지 판별합니다.
     */
    fun isTopicDuplicate(
        candidate: ExposureContentRecommendationCandidateRow,
        selected: Collection<ExposureContentRecommendationCandidateRow>,
        similarityMap: Map<Long, Map<Long, Double>> = emptyMap(),
    ): Boolean {
        if (selected.isEmpty()) return false

        // 1. 임베딩 유사도 기반 판별 (코사인 거리가 임계치 이하로 매우 가까운 경우)
        val maxCosineSimilarity = getMaxCosineSimilarity(candidate, selected, similarityMap)
        if (maxCosineSimilarity >= SIMILARITY_THRESHOLD) {
            return true
        }

        // 2. 어휘적 키워드/토큰 중복 기반 판별 (임베딩 미생성 글 폴백 및 명시적 키워드 일치)
        return hasLexicalTopicOverlap(candidate, selected)
    }

    fun getMaxCosineSimilarity(
        candidate: ExposureContentRecommendationCandidateRow,
        selected: Collection<ExposureContentRecommendationCandidateRow>,
        similarityMap: Map<Long, Map<Long, Double>>,
    ): Double {
        if (selected.isEmpty() || similarityMap.isEmpty()) return 0.0
        val candidateSimilarities = similarityMap[candidate.contentId] ?: return 0.0

        var maxSim = 0.0
        for (item in selected) {
            val sim = candidateSimilarities[item.contentId] ?: 0.0
            if (sim > maxSim) {
                maxSim = sim
            }
        }
        return maxSim
    }

    fun hasLexicalTopicOverlap(
        candidate: ExposureContentRecommendationCandidateRow,
        selected: Collection<ExposureContentRecommendationCandidateRow>,
        overlapThreshold: Int = 2,
    ): Boolean {
        val candidateTokens = extractKeyTokens("${candidate.title} ${candidate.provocativeHeadline}")
        if (candidateTokens.isEmpty()) return false

        val normalizedCandidateTitle = normalizeTitle(candidate.title)

        for (item in selected) {
            // 제목 완전 포함 관계 검사 (예: "React 19 릴리즈" in "React 19 릴리즈 소식과 마이그레이션")
            val normalizedItemTitle = normalizeTitle(item.title)
            if (normalizedCandidateTitle.length >= 10 && normalizedItemTitle.length >= 10) {
                if (normalizedCandidateTitle.contains(normalizedItemTitle) || normalizedItemTitle.contains(normalizedCandidateTitle)) {
                    return true
                }
            }

            // 고유 기술 토큰 교집합 검사
            val itemTokens = extractKeyTokens("${item.title} ${item.provocativeHeadline}")
            val intersection = candidateTokens.intersect(itemTokens)
            if (intersection.size >= overlapThreshold) {
                return true
            }
        }
        return false
    }

    private fun normalizeTitle(title: String): String =
        title.lowercase()
            .replace(Regex("[^a-zA-Z0-9가-힣]"), "")

    fun extractKeyTokens(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9가-힣\\s]"), " ")
            .split(Regex("\\s+"))
            .map { canonicalizeAlias(it) }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()
    }

    private fun canonicalizeAlias(token: String): String =
        TECH_ALIASES[token] ?: token

    companion object {
        const val SIMILARITY_THRESHOLD = 0.80
        const val DAMPING_SLOPE = 0.90
        const val MIN_MULTIPLIER = 0.10
        const val LEXICAL_DUPLICATE_MULTIPLIER = 0.20

        private val TECH_ALIASES = mapOf(
            "리액트" to "react",
            "스프링" to "spring",
            "스프링부트" to "spring",
            "도커" to "docker",
            "쿠버네티스" to "k8s",
            "kubernetes" to "k8s",
            "포스트그레스" to "postgres",
            "postgresql" to "postgres",
            "카프카" to "kafka",
            "파이썬" to "python",
            "자바" to "java",
            "코틀린" to "kotlin",
            "타입스크립트" to "typescript",
            "typescript" to "typescript",
            "자바스크립트" to "javascript",
            "javascript" to "javascript",
            "앤트로픽" to "anthropic",
            "클로드" to "claude",
            "오픈에이아이" to "openai",
        )

        private val STOP_WORDS = setOf(
            "개발", "위한", "어떻게", "하는", "방법", "정리", "가이드", "소개", "알아보기", "이유",
            "활용", "분석", "적용", "이야기", "후기", "기초", "프로젝트", "서비스", "시스템", "엔지니어링",
            "비교", "문제", "해결", "노하우", "원리", "실무", "작성", "사용법", "도입", "구축", "출시",
            "버전", "업데이트", "신규", "새로운", "톺아보기", "공개",
            "with", "the", "and", "for", "how", "what", "from", "into", "that", "this",
            "about", "guide", "release", "version", "update", "new"
        )
    }
}

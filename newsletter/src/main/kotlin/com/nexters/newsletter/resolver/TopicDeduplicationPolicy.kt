package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.repository.KeywordAliasRepository
import com.nexters.external.repository.ReservedKeywordRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TopicDeduplicationPolicy(
    private val keywordAliasRepository: KeywordAliasRepository? = null,
    private val reservedKeywordRepository: ReservedKeywordRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(TopicDeduplicationPolicy::class.java)

    @Volatile
    private var aliasCache: Map<String, String> = DEFAULT_TECH_ALIASES

    @PostConstruct
    fun initAliases() {
        try {
            if (keywordAliasRepository != null && reservedKeywordRepository != null) {
                val keywords = reservedKeywordRepository.findAll().associateBy { it.id }
                val aliases = keywordAliasRepository.findByEnabledTrue()
                val dynamicMap = mutableMapOf<String, String>()
                for (alias in aliases) {
                    val canonicalName = keywords[alias.keywordId]?.name?.lowercase() ?: continue
                    dynamicMap[alias.alias.lowercase()] = canonicalName
                    dynamicMap[alias.normalizedAlias.lowercase()] = canonicalName
                }
                if (dynamicMap.isNotEmpty()) {
                    aliasCache = DEFAULT_TECH_ALIASES + dynamicMap
                    logger.info("TopicDeduplicationPolicy: Loaded {} keyword aliases from database", dynamicMap.size)
                }
            }
        } catch (e: Exception) {
            logger.warn("TopicDeduplicationPolicy: Failed to load dynamic keyword aliases, using defaults: {}", e.message)
        }
    }

    /**
     * 이미 선정된 후보군(selected)과 비교하여 해당 candidate의 토픽 중복 감쇠 계수(Multiplier)를 계산합니다.
     * MMR(Maximal Marginal Relevance) 기반으로 코사인 거리가 가까울수록(유사도가 높을수록) 점수를 감쇠시킵니다.
     */
    fun calculateDampingMultiplier(
        candidate: ExposureContentRecommendationCandidateRow,
        selected: Collection<ExposureContentRecommendationCandidateRow>,
        similarityMap: Map<Long, Map<Long, Double>> = emptyMap(),
        contentKeywordsMap: Map<Long, Set<String>> = emptyMap(),
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
        return if (hasLexicalTopicOverlap(candidate, selected, contentKeywordsMap = contentKeywordsMap)) {
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
        contentKeywordsMap: Map<Long, Set<String>> = emptyMap(),
    ): Boolean {
        if (selected.isEmpty()) return false

        // 1. 임베딩 유사도 기반 판별 (코사인 거리가 임계치 이하로 매우 가까운 경우)
        val maxCosineSimilarity = getMaxCosineSimilarity(candidate, selected, similarityMap)
        if (maxCosineSimilarity >= SIMILARITY_THRESHOLD) {
            return true
        }

        // 2. 어휘적 키워드/토큰 중복 기반 판별 (임베딩 미생성 글 폴백 및 명시적 키워드 일치)
        return hasLexicalTopicOverlap(candidate, selected, contentKeywordsMap = contentKeywordsMap)
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
        contentKeywordsMap: Map<Long, Set<String>> = emptyMap(),
    ): Boolean {
        // 1. 제목 완전 포함 관계 검사 (예: "React 19 릴리즈" in "React 19 릴리즈 소식과 마이그레이션")
        val normalizedCandidateTitle = normalizeTitle(candidate.title)
        for (item in selected) {
            val normalizedItemTitle = normalizeTitle(item.title)
            if (normalizedCandidateTitle.length >= 10 && normalizedItemTitle.length >= 10) {
                if (normalizedCandidateTitle.contains(normalizedItemTitle) || normalizedItemTitle.contains(normalizedCandidateTitle)) {
                    return true
                }
            }
        }

        // 2. DB 키워드 기반 세부 기술(Sub-stack) 1건 일치 검사
        val candidateKeywords = contentKeywordsMap[candidate.contentId].orEmpty()
        val candidateSpecificKeywords = candidateKeywords.filter { !isBroadKeyword(it) }.toSet()
        if (candidateSpecificKeywords.isNotEmpty()) {
            for (item in selected) {
                val itemKeywords = contentKeywordsMap[item.contentId].orEmpty()
                val itemSpecificKeywords = itemKeywords.filter { !isBroadKeyword(it) }.toSet()
                if (candidateSpecificKeywords.intersect(itemSpecificKeywords).isNotEmpty()) {
                    return true
                }
            }
        }

        // 3. 고유 기술 토큰(Title + Headline) 교집합 검사 (한국어 조사 정규화 & Alias 반영)
        val candidateTokens = extractKeyTokens("${candidate.title} ${candidate.provocativeHeadline}")
        if (candidateTokens.isEmpty()) return false

        for (item in selected) {
            val itemTokens = extractKeyTokens("${item.title} ${item.provocativeHeadline}")
            val intersection = candidateTokens.intersect(itemTokens)

            // 세부 기술(Specific Sub-stack) 단어 1건이라도 일치하면 토픽 중복으로 즉시 판정 (피드 다양성 보장)
            if (intersection.any { isSpecificTechToken(it) }) {
                return true
            }

            // 일반 어휘 토큰은 기존처럼 overlapThreshold(기본 2건) 이상 겹칠 때 중복 판정
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
        val cleanText = text.lowercase().replace(Regex("[^a-zA-Z0-9가-힣\\s]"), " ")
        val rawTokens = cleanText.split(Regex("\\s+"))

        val tokens = mutableSetOf<String>()
        for (raw in rawTokens) {
            val stripped = stripJosa(raw)
            val canonical = canonicalizeAlias(stripped)
            if (canonical.length >= 2 && canonical !in STOP_WORDS) {
                tokens.add(canonical)
            }
            if (stripped != raw) {
                val rawCanonical = canonicalizeAlias(raw)
                if (rawCanonical.length >= 2 && rawCanonical !in STOP_WORDS) {
                    tokens.add(rawCanonical)
                }
            }
        }

        // 공백이 포함된 다단어 별칭 지원 (예: "jetpack compose", "spring boot", "react native")
        for ((alias, canonical) in aliasCache) {
            if (alias.contains(' ') && cleanText.contains(alias)) {
                tokens.add(canonical)
            }
        }

        return tokens
    }

    private fun stripJosa(token: String): String {
        val match = JOSA_REGEX.matchEntire(token)
        if (match != null && match.groupValues[1].length >= 2) {
            return match.groupValues[1]
        }
        return token
    }

    private fun canonicalizeAlias(token: String): String =
        aliasCache[token] ?: token

    private fun isSpecificTechToken(token: String): Boolean {
        val lower = token.lowercase()
        return lower !in BROAD_KEYWORDS && (aliasCache.containsKey(lower) || aliasCache.containsValue(lower))
    }

    private fun isBroadKeyword(keyword: String): Boolean =
        keyword.lowercase() in BROAD_KEYWORDS

    companion object {
        const val SIMILARITY_THRESHOLD = 0.80
        const val DAMPING_SLOPE = 0.90
        const val MIN_MULTIPLIER = 0.10
        const val LEXICAL_DUPLICATE_MULTIPLIER = 0.20

        private val JOSA_REGEX = Regex(
            "^([a-zA-Z0-9가-힣]+?)(?:에서는|에서|으로|부터|까지|에게|한테|이나|보다|처럼|마다|의|를|을|가|이|는|은|로|와|과|도|만)$"
        )

        val BROAD_KEYWORDS = setOf(
            "ai", "backend", "frontend", "ios", "android", "경험", "개발문화", "api", "http", "ui", "ux",
            "mobile", "cloud", "architecture", "아키텍처", "데이터베이스", "인프라", "보안", "성능", "자동화", "test", "library",
            "javascript", "typescript", "python", "java", "kotlin", "swift"
        )

        val DEFAULT_TECH_ALIASES = mapOf(
            // 기존 19개
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
            // 주요 모바일/FE/BE 핵심 스택 별칭
            "컴포즈" to "compose",
            "젯팩 컴포즈" to "compose",
            "jetpack compose" to "compose",
            "compose" to "compose",
            "jetpack" to "jetpack",
            "스위프트" to "swift",
            "swift" to "swift",
            "스위프트ui" to "swiftui",
            "swiftui" to "swiftui",
            "uikit" to "uikit",
            "tuist" to "tuist",
            "튜이스트" to "tuist",
            "코루틴" to "coroutine",
            "coroutine" to "coroutine",
            "fastapi" to "fastapi",
            "패스트api" to "fastapi",
            "zustand" to "zustand",
            "주스탠드" to "zustand",
            "kmp" to "kmp",
            "hilt" to "hilt",
            "힐트" to "hilt",
            "레디스" to "redis",
            "redis" to "redis",
            "duckdb" to "duckdb",
            "graphql" to "graphql",
            "nextjs" to "next.js",
            "next.js" to "next.js",
            "tailwind" to "tailwind css",
            "tailwindcss" to "tailwind css",
            "vue" to "vue.js",
            "뷰" to "vue.js",
            "svelte" to "svelte",
            "spm" to "spm",
            "xcode" to "xcode",
            "엑스코드" to "xcode",
            "mcp" to "mcp",
            "gradle" to "gradle",
            "그레이들" to "gradle",
            "jpa" to "jpa",
            "hibernate" to "hibernate",
            "하이버네이트" to "hibernate",
            "grpc" to "grpc",
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

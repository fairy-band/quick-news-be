package com.nexters.external.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI

@Service
class CrawlerSourceVerificationService {
    private val logger = LoggerFactory.getLogger(CrawlerSourceVerificationService::class.java)

    /**
     * 크롤러 본문 및 이미지 추출 대상 URL이 사전 검증된 신뢰할 수 있는 소스인지 검사합니다.
     */
    fun isVerifiedForCrawl(url: String): Boolean {
        val domain = extractDomain(url)?.lowercase() ?: return false

        // 1. 명시적 차단 도메인 (비아티클, 트래킹 리다이렉트 등)
        if (isBlockedDomain(domain)) {
            logger.debug("URL is in blocked domain list: domain={}, url={}", domain, url)
            return false
        }

        // 2. 완전 검증된 도메인 화이트리스트 일치 여부
        val isVerified = VERIFIED_DOMAINS.any { verifiedDomain ->
            domain == verifiedDomain || domain.endsWith(".$verifiedDomain")
        }

        if (!isVerified) {
            logger.debug("URL domain is not in verified list: domain={}, url={}", domain, url)
        }
        return isVerified
    }

    /**
     * URL에서 도메인(호스트명)을 추출합니다.
     */
    fun extractDomain(url: String): String? {
        return try {
            val uri = URI(url.trim())
            uri.host?.lowercase()?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 크롤링이 불필요하거나 오작동을 유발하는 제외 대상 도메인인지 확인합니다.
     */
    fun isBlockedDomain(domain: String): Boolean {
        val cleanDomain = domain.removePrefix("www.")
        return BLOCKED_DOMAINS.any { blocked ->
            cleanDomain == blocked || cleanDomain.endsWith(".$blocked")
        }
    }

    companion object {
        /**
         * 크롤링 및 이미지 수집 품질과 안정성이 100% 입증된 검증 도메인 목록
         */
        val VERIFIED_DOMAINS = setOf(
            // 1. 핵심 링크형 뉴스레터
            "maeil-mail.kr",

            // 2. 검증된 서브스택 및 인디 뉴스레터
            "substack.com",
            "fatbobman.com",
            "weekly.fatbobman.com",
            "jacobstechtavern.com",
            "blog.jacobstechtavern.com",
            "pragmaticengineer.com",
            "newsletter.pragmaticengineer.com",
            "architecture-weekly.com",

            // 3. 국내외 주요 기업 테크 블로그
            "samsungsds.com",
            "woowahan.com",
            "techblog.woowahan.com",
            "thefarmersfront.github.io",
            "helloworld.kurly.com",
            "lycorp.co.jp",
            "techblog.lycorp.co.jp",
            "cloud.nongshim.co.kr",
            "tech.cloud.nongshim.co.kr",
            "jetbrains.com",
            "blog.jetbrains.com",
            "d2.naver.com",
            "tech.kakao.com",
            "techblog.gccompany.co.kr",
            "techblog.musinsa.com",
            "meetup.nhncloud.com",
            "zuminternet.github.io",

            // 4. 개발자 전문 커뮤니티 및 개인 기술 블로그
            "dev.to",
            "velog.io",
            "kentcdodds.com",
            "joshuakgoldberg.com",
            "44bits.io",
            "blog.outsider.ne.kr",

            // 5. 신뢰할 수 있는 글로벌 기술 미디어 & 클라우드
            "itworld.co.kr",
            "aws.amazon.com",
            "devblogs.microsoft.com",
            "confluent.io",
            "medium.com",
        )

        /**
         * 크롤러 호출을 엄격히 금지하는 비아티클 및 트래킹 리다이렉트 도메인
         */
        val BLOCKED_DOMAINS = setOf(
            // 메일 클릭 트래킹 리다이렉트 (무작위 3rd party 분산 및 타임아웃 방지)
            "links.tldrnewsletter.com",
            "click.kit-mail6.com",
            "rs6.net",
            "feedpress.me",
            "eot.webtoolsweekly.com",
            "eot.vscode.email",
            "bit.ly",
            "tinyurl.com",

            // 동영상 / 미디어
            "youtube.com",
            "youtu.be",
            "vimeo.com",
            "spotify.com",

            // SNS
            "twitter.com",
            "x.com",
            "facebook.com",
            "instagram.com",
            "linkedin.com",

            // 코드 저장소 / 패키지 매니저
            "github.com",
            "gitlab.com",
            "npmjs.com",
            "npm.im",
            "crates.io",
            "maven.org",
        )
    }
}

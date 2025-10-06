package com.nexters.newsletter.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeeknewsWeeklyParserTest {
    private val parser = GeeknewsWeeklyParser()

    @Test
    fun `긱뉴스 위클리 sender를 감지한다`() {
        assertThat(parser.isTarget("news@hada.io")).isTrue()
        assertThat(parser.isTarget("긱뉴스 <news@hada.io>")).isTrue()
        assertThat(parser.isTarget("other@example.com")).isFalse()
    }

    @Test
    fun `긱뉴스 위클리 plainText를 파싱하여 articles로 변환한다`() {
        val separator = "-".repeat(72)
        val plainText =
            """
            소프트웨어 관련 글과 책은 수도 없이 많지만, 인생의 방향을 바꿀 만한 글을 만나기는 쉽지 않습니다.
            
            $separator
            나를 만들어준 소프트웨어 에세이들   * https://news.hada.io/topic?id=23406
            $separator
            Joel Spolsky의 "Joel Test" (2000)
            Alexis King의 "Parse, don't validate" (2019)
            
            $separator
            소프트웨어 엔지니어링에서의 "좋은 취향"이란 무엇인가?   * https://news.hada.io/topic?id=23365
            $separator
            소프트웨어 엔지니어링에서의 "좋은 취향"은 단순한 기술적 실력과 구분되며, 프로젝트 요구에 맞는 엔지니어링 가치를 유연하게 선택하고 조율하는 능력을 의미합니다.
            """.trimIndent()

        val result = parser.parse(plainText)

        assertThat(result).hasSize(2)

        assertThat(result[0].title).isEqualTo("나를 만들어준 소프트웨어 에세이들")
        assertThat(result[0].link).isEqualTo("https://news.hada.io/topic?id=23406")
        assertThat(result[0].content).contains("Joel Spolsky")
        assertThat(result[0].section).isEqualTo("Article")

        assertThat(result[1].title).isEqualTo("소프트웨어 엔지니어링에서의 \"좋은 취향\"이란 무엇인가?")
        assertThat(result[1].link).isEqualTo("https://news.hada.io/topic?id=23365")
        assertThat(result[1].content).contains("소프트웨어 엔지니어링에서의")
        assertThat(result[1].section).isEqualTo("Article")
    }
}

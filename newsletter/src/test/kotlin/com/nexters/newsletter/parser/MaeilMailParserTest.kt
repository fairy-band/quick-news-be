package com.nexters.newsletter.parser

import com.nexters.external.apiclient.ArticleExtractResponse
import com.nexters.external.apiclient.CrawlerServiceClient
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MaeilMailParserTest {

    @Test
    fun `supports should recognize maeil-mail sender`() {
        val parser = MaeilMailParser()
        assertThat(parser.supports("noreply@maeil-mail.kr", "매일메일")).isTrue()
        assertThat(parser.supports("other@example.com", "기타")).isFalse()
    }

    @Test
    fun `parse should extract question url and fetch content and imageUrl via CrawlerServiceClient`() {
        val crawlerClient = mockk<CrawlerServiceClient>()
        val questionUrl = "https://www.maeil-mail.kr/question/17"

        every { crawlerClient.extractArticle(questionUrl) } returns ArticleExtractResponse(
            url = questionUrl,
            success = true,
            title = "HTTP와 HTTPS의 차이는 무엇인가요?",
            content = "HTTP는 암호화되지 않은 평문 통신을 수행하지만, HTTPS는 SSL/TLS를 통해 암호화됩니다.",
            imageUrl = "https://dp71rnme1p14w.cloudfront.net/maeil-mail-ogImage.png",
            length = 56,
        )

        val parser = MaeilMailParser(crawlerServiceClient = crawlerClient)
        val context = MailParseContext(
            content = "오늘의 질문입니다: https://www.maeil-mail.kr/question/17 지금 확인해보세요.",
            subject = "[매일메일] HTTP와 HTTPS의 차이는?",
        )

        val results = parser.parse(context)

        assertThat(results).hasSize(1)
        val content = results[0]
        assertThat(content.title).isEqualTo("HTTP와 HTTPS의 차이는?")
        assertThat(content.content).contains("HTTP는 암호화되지 않은")
        assertThat(content.link).isEqualTo(questionUrl)
        assertThat(content.imageUrl).isEqualTo("https://dp71rnme1p14w.cloudfront.net/maeil-mail-ogImage.png")
    }
}

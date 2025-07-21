package com.nexters.newsletterfeeder.parser

import com.nexters.external.entity.NewsletterSource
import com.nexters.external.entity.Content
import com.nexters.newsletterfeeder.dto.EmailMessage

/**
 * 메일을 파싱해서 NewsletterSource(MongoDB)와 Content(RDB)로 변환하는 파서 인터페이스
 */
interface NewsletterParser {
    fun parse(emailMessage: EmailMessage): ParsedNewsletter
    fun supports(sender: String): Boolean
}

/**
 * 파싱 결과: NewsletterSource(원본) + 기사(여러 개)
 */
data class ParsedNewsletter(
    val source: NewsletterSource,
    val contents: List<Content>
)

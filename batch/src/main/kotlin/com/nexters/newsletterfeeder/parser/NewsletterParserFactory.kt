package com.nexters.newsletterfeeder.parser

import com.nexters.newsletterfeeder.dto.EmailMessage

object NewsletterParserFactory {
    fun getParser(sender: String): NewsletterParser {
        return when {
            sender.lowercase().contains("react@cooperpress.com") -> ReactStatusParser()
            else -> DefaultNewsletterParser()
        }
    }
}

class DefaultNewsletterParser : NewsletterParser {
    override fun parse(emailMessage: EmailMessage): ParsedNewsletter {
        // TODO: 기본 파싱 로직 구현
        throw NotImplementedError("기본 파서가 구현되어 있지 않습니다.")
    }
}


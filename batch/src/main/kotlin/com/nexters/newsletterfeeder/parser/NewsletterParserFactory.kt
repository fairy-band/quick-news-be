package com.nexters.newsletterfeeder.parser

import com.nexters.newsletterfeeder.dto.EmailMessage


object NewsletterParserFactory {
    private val parsers = listOf(
        ReactStatusParser(),
        // 다른 파서들 추가
        DefaultNewsletterParser()
    )

    fun getParser(sender: String): NewsletterParser {
        return parsers.firstOrNull { it.supports(sender) } ?: DefaultNewsletterParser()
    }
}

class DefaultNewsletterParser : NewsletterParser {
    override fun parse(emailMessage: EmailMessage): ParsedNewsletter {
        throw NotImplementedError("기본 파서가 구현되어 있지 않습니다.")
    }

    override fun supports(sender: String): Boolean {
        return false // Default parser does not support any specific sender
    }
}


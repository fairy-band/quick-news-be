package com.nexters.newsletter.service

import com.nexters.external.entity.Content

interface NewsletterContentGroupingService {
    fun groupNewsletterSource(newsletterSourceId: String): List<Content>
}

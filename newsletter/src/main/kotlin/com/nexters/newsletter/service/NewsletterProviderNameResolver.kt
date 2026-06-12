package com.nexters.newsletter.service

import com.nexters.external.entity.NewsletterSource

object NewsletterProviderNameResolver {
    fun resolve(newsletterSource: NewsletterSource): String =
        newsletterSource.providerNameFromHint()
            ?: newsletterSource.sender.takeIf { sender -> sender.isNotBlank() }
            ?: newsletterSource.senderEmail

    private fun NewsletterSource.providerNameFromHint(): String? {
        val haystack =
            listOf(
                senderEmail.normalized(),
                sender.normalized(),
                subject.orEmpty().normalized(),
                headers["RSS-Feed-URL"].orEmpty().normalized(),
                headers["RSS-Item-URL"].orEmpty().normalized(),
            ).joinToString(separator = "\n")

        return PROVIDER_NAME_HINTS.entries.firstOrNull { (hint, _) -> haystack.contains(hint) }?.value
    }

    private fun String.normalized(): String = trim().lowercase()

    private val PROVIDER_NAME_HINTS =
        linkedMapOf(
            "awesome ios weekly" to "Awesome iOS Weekly",
            "iosdevweekly.com" to "iOS Dev Weekly",
            "dave verwer" to "iOS Dev Weekly",
            "ioscoffeebreak.com" to "iOS Coffee Break",
            "swiftuiweekly@substack.com" to "SwiftUI Weekly",
            "majid jabrayilov" to "SwiftUI Weekly",
            "swiftwithvincent.com" to "Swift with Vincent",
            "vincent pradeilles" to "Swift with Vincent",
            "swiftlee" to "SwiftLee Weekly",
            "avanderlee.com" to "SwiftLee Weekly",
            "donnywals.com" to "Donny Wals Newsletter",
            "donny wals" to "Donny Wals Newsletter",
            "hackingwithswift.com" to "HackingWithSwift Newsletter",
            "hacking with swift" to "HackingWithSwift Newsletter",
            "let'swift" to "Let'Swift NewLetter",
            "letswift" to "Let'Swift NewLetter",
            "jacobbartlett@substack.com" to "Jacob's Tech Tavern",
            "jacob's tech tavern" to "Jacob's Tech Tavern",
            "fatbobman@substack.com" to "Fatbobman's Swift Weekly",
            "fatbobman's swift weekly" to "Fatbobman's Swift Weekly",
            "ios.libhunt.com" to "Awesome iOS Weekly",
            "awesome android newsletter" to "Awesome Android Newsletter",
            "android.libhunt.com" to "Awesome Android Newsletter",
            "awesome kotlin weekly" to "Awesome Kotlin Weekly",
            "kotlin.libhunt.com" to "Awesome Kotlin Weekly",
            "awesome java newsletter" to "Awesome Java Newsletter",
            "awesome java weekly" to "Awesome Java Newsletter",
            "java.libhunt.com" to "Awesome Java Newsletter",
            "jsw@peterc.org" to "JavaScript Weekly",
            "javascript weekly" to "JavaScript Weekly",
            "java weekly" to "Java Weekly",
            "kotlinweekly.net" to "Kotlin Weekly",
            "kotlin weekly" to "Kotlin Weekly",
            "news@hada.io" to "GeekNews Weekly",
            "noreply@maeil-mail.kr" to "Maeil Mail",
            "kofearticle@substack.com" to "Korean FE Article",
            "dan@tldrnewsletter.com" to "TLDR",
            "eugen@baeldung.com" to "Baeldung",
            "yozm_help@wishket.com" to "Yozm",
            "tyler@ui.dev" to "Bytes",
            "submissions@webtoolsweekly.com" to "Web Tools Weekly",
            "submissions@vscode.email" to "VS Code Email",
            "pragmaticengineer@substack.com" to "The Pragmatic Engineer",
            "pragmaticengineer+deepdives@substack.com" to "The Pragmatic Engineer",
            "thepracticalstack461@substack.com" to "The Practical Stack",
            "thecodercafe+concepts@substack.com" to "The Coder Cafe",
            "architectureweekly@substack.com" to "Architecture Weekly",
            "react@cooperpress.com" to "React Status",
            "frontend@cooperpress.com" to "Frontend Focus",
            "node@cooperpress.com" to "Node Weekly",
            "postgres@cooperpress.com" to "Postgres Weekly",
            "peter@golangweekly.com" to "Go Weekly",
            "rahul@pythonweekly.com" to "Python Weekly",
            "contact@androidweekly.net" to "Android Weekly",
            "itworld@techlibrary.co.kr" to "ITWorld Korea",
            "css-weekly@beehiiv.com" to "CSS Weekly",
            "ilbuntok.com" to "일분톡",
        )
}

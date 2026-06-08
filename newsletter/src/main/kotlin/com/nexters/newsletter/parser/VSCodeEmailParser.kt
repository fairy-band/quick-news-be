package com.nexters.newsletter.parser

class VSCodeEmailParser :
    NumberedLinkNewsletterParser(
        targetSender = "submissions@vscode.email",
        sectionNames =
            setOf(
                "VS CODE TOOLS",
                "VS CODE THEME OF THE WEEK",
                "VS CODE ARTICLES & VIDEOS",
                "BEST OF THE REST",
            ),
        maxArticleCount = 20,
    )

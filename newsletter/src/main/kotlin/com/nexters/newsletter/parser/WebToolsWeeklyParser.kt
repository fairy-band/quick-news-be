package com.nexters.newsletter.parser

class WebToolsWeeklyParser :
    NumberedLinkNewsletterParser(
        targetSender = "submissions@webtoolsweekly.com",
        sectionNames =
            setOf(
                "CSS & HTML TOOLS",
                "JAVASCRIPT UTILITIES",
                "TESTING & DEBUGGING TOOLS",
                "JAVASCRIPT LIBRARIES & FRAMEWORKS",
                "REACT TOOLS",
                "AI TOOLS, LLMS, ETC.",
                "MEDIA TOOLS (SVG, VIDEO, ETC.)",
                "THE UNCATEGORIZABLES",
            ),
    )

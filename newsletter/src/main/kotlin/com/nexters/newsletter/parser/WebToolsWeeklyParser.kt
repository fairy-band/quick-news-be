package com.nexters.newsletter.parser

class WebToolsWeeklyParser :
    NumberedLinkNewsletterParser(
        targetSender = "submissions@webtoolsweekly.com",
        sectionNames = SECTION_NAMES,
        aggregateSectionsAsLibraries = SECTION_NAMES,
    ) {
    companion object {
        private val SECTION_NAMES =
            setOf(
                "CSS & HTML TOOLS",
                "JAVASCRIPT UTILITIES",
                "TESTING & DEBUGGING TOOLS",
                "JAVASCRIPT LIBRARIES & FRAMEWORKS",
                "REACT TOOLS",
                "AI TOOLS, LLMS, ETC.",
                "MEDIA TOOLS (SVG, VIDEO, ETC.)",
                "THE UNCATEGORIZABLES",
            )
    }
}

package com.nexters.newsletter.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CssWeeklyParserTest {
    private val parser = CssWeeklyParser()

    @Test
    fun `CSS Weekly 뉴스레터 파싱 테스트`() {
        // Given - 실제 CSS Weekly 뉴스레터 예시
        val emailContent =
            """
# Headlines

## [:has() Is More Than a Parent Selector](https://www.youtube.com/watch?v=cxSowU9sDdU)

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/9cb464df-2653-45a5-b09f-4e32b35ce26d/has-is-more-than-a-parent-selector.png?t=1752754917)
Follow image link: (https://www.youtube.com/watch?v=cxSowU9sDdU)
Caption:

Kevin Powell explores some creative ways to utilize `:has()` pseudo-class.

[Watch video](https://www.youtube.com/watch?v=cxSowU9sDdU)

## [The Gap Strikes Back: Now Stylable](https://css-tricks.com/the-gap-strikes-back-now-stylable/)

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/b4ba31fc-6b8d-4480-bf2d-206409e54986/the-gap-strikes-back-now-stylable.png?t=1752754941)
Follow image link: (https://css-tricks.com/the-gap-strikes-back-now-stylable/)
Caption:

Patrick Brosset explains how a new CSS feature enables you to style gap areas.

[Read more](https://css-tricks.com/the-gap-strikes-back-now-stylable/)

# What Have I Been up To

## [AI Developer Weekly](https://aideveloperweekly.com/)

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/e3d20fc1-1961-4a0d-afcb-d83ef288dbd7/ai-develoeper-weekly.jpg?t=1752239406)
Follow image link: (https://aideveloperweekly.com/)
Caption:

I've been exploring various coding approaches using AI tools recently and realized that there's much more potential in those tools than I initially thought. I want to dig deeper and explore more, and as with CSS, I want to share what I learn with you. It will be hand-curated with only the top-quality content.

The first issue is just around the corner, so sign up today so you don't miss it.

[Learn more](https://aideveloperweekly.com/)

# Articles & Tutorials

## [Custom Select (That Comes Up From the Bottom on Mobile)](https://frontendmasters.com/blog/custom-select-that-comes-up-from-the-bottom-on-mobile/)

Chris Coyier demonstrates how to create a beautiful and functional custom select.

[Read more](https://frontendmasters.com/blog/custom-select-that-comes-up-from-the-bottom-on-mobile/)

## [CSS Intelligence: Speculating On The Future Of A Smarter Language](https://www.smashingmagazine.com/2025/07/css-intelligence-speculating-future-smarter-language/)

Gabriel Shoyombo explores how smart CSS has become over the years, where it is heading, the challenges it addresses, whether it is becoming too complex, and how developers are reacting to this shift.

[Read more](https://www.smashingmagazine.com/2025/07/css-intelligence-speculating-future-smarter-language/)

## [A revisit of the Every Layout sidebar with :has() and selector performance](https://piccalil.li/blog/a-revisit-of-the-every-layout-sidebar-with-has-and-selector-performance/)

Heydon Pickering explores how modern CSS selectors can improve some common layout patterns.

[Read more](https://piccalil.li/blog/a-revisit-of-the-every-layout-sidebar-with-has-and-selector-performance/)

## [Playing With the New Caret CSS Properties](https://blogs.igalia.com/mrego/playing-with-the-new-caret-css-properties/)

Manuel Rego Casasnovas gives a brief introduction to the new `caret-animation` and `caret-shape` CSS properties.

[Read more](https://blogs.igalia.com/mrego/playing-with-the-new-caret-css-properties/)

## [Setting Line Length in CSS (and Fitting Text to a Container)](https://css-tricks.com/setting-line-length-in-css-and-fitting-text-to-a-container/)

Daniel Schwarz explores different ways to control line length when working with text, including two proposed properties that could make it easier in the future.

[Read more](https://css-tricks.com/setting-line-length-in-css-and-fitting-text-to-a-container/)

# Tools & Resources

## [](https://andreruffert.github.io/syntax-highlight-element/)

A custom element that uses the CSS Custom Highlight API for syntax highlighting.

[Check it out](https://andreruffert.github.io/syntax-highlight-element/)

## [Unencumbered  Web Component](https://github.com/zachleat/line-numbers)

A web component to add line numbers next to various HTML elements.

[Check it out](https://github.com/zachleat/line-numbers)

# Inspiration

## [AI Keys ](https://codepen.io/jh3y/pen/OPyPRLK)✨

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/587dfcc1-151a-4fbb-a93a-eb3b024fe43e/ai-keys.jpg?t=1752755198)
Follow image link: (https://codepen.io/jh3y/pen/OPyPRLK)
Caption:

Another stunning, realistic demo created by Jhey Tompkins. _(Speaking of AI, have you already subscribed to my new __[AI Developer newsletter](https://aideveloperweekly.com/)__?)_

[Get inspired](https://codepen.io/jh3y/pen/OPyPRLK)
            """.trimIndent()

        // When
        val result = parser.parse(emailContent)
        result.forEachIndexed { idx, it -> println("[DEBUG] $idx: $it") }

        // Then - 주요 섹션별로 대표 기사 검증
        assertTrue(
            result.any {
                it.title.contains(":has() Is More Than a Parent Selector") &&
                    it.link == "https://www.youtube.com/watch?v=cxSowU9sDdU" &&
                    it.section == "Headlines"
            },
        )
        assertTrue(
            result.any {
                it.title.contains("The Gap Strikes Back") &&
                    it.link == "https://css-tricks.com/the-gap-strikes-back-now-stylable/" &&
                    it.section == "Headlines"
            },
        )
        assertTrue(
            result.any {
                it.title.contains("AI Developer Weekly") &&
                    it.link == "https://aideveloperweekly.com/" &&
                    it.section == "What Have I Been up To"
            },
        )
        assertTrue(
            result.any {
                it.title.contains("Custom Select") &&
                    it.link == "https://frontendmasters.com/blog/custom-select-that-comes-up-from-the-bottom-on-mobile/" &&
                    it.section == "Articles & Tutorials"
            },
        )
        assertTrue(
            result.any {
                it.title.contains("CSS Intelligence") &&
                    it.link == "https://www.smashingmagazine.com/2025/07/css-intelligence-speculating-future-smarter-language/" &&
                    it.section == "Articles & Tutorials"
            },
        )
        assertTrue(
            result.any {
                it.title.contains("Unencumbered  Web Component") &&
                    it.link == "https://github.com/zachleat/line-numbers" &&
                    it.section == "Tools & Resources"
            },
        )
    }

    @Test
    fun `CSS Weekly 뉴스레터 파싱 상세 테스트`() {
        // Given
        val emailContent =
            """
# Headlines

## [:has() Is More Than a Parent Selector](https://www.youtube.com/watch?v=cxSowU9sDdU)

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/9cb464df-2653-45a5-b09f-4e32b35ce26d/has-is-more-than-a-parent-selector.png?t=1752754917)
Follow image link: (https://www.youtube.com/watch?v=cxSowU9sDdU)
Caption:

Kevin Powell explores some creative ways to utilize `:has()` pseudo-class.

[Watch video](https://www.youtube.com/watch?v=cxSowU9sDdU)

## [The Gap Strikes Back: Now Stylable](https://css-tricks.com/the-gap-strikes-back-now-stylable/)

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/b4ba31fc-6b8d-4480-bf2d-206409e54986/the-gap-strikes-back-now-stylable.png?t=1752754941)
Follow image link: (https://css-tricks.com/the-gap-strikes-back-now-stylable/)
Caption:

Patrick Brosset explains how a new CSS feature enables you to style gap areas.

[Read more](https://css-tricks.com/the-gap-strikes-back-now-stylable/)

# What Have I Been up To

## [AI Developer Weekly](https://aideveloperweekly.com/)

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/e3d20fc1-1961-4a0d-afcb-d83ef288dbd7/ai-develoeper-weekly.jpg?t=1752239406)
Follow image link: (https://aideveloperweekly.com/)
Caption:

I've been exploring various coding approaches using AI tools recently and realized that there's much more potential in those tools than I initially thought. I want to dig deeper and explore more, and as with CSS, I want to share what I learn with you. It will be hand-curated with only the top-quality content.

The first issue is just around the corner, so sign up today so you don't miss it.

[Learn more](https://aideveloperweekly.com/)

# Articles & Tutorials

## [Custom Select (That Comes Up From the Bottom on Mobile)](https://frontendmasters.com/blog/custom-select-that-comes-up-from-the-bottom-on-mobile/)

Chris Coyier demonstrates how to create a beautiful and functional custom select.

[Read more](https://frontendmasters.com/blog/custom-select-that-comes-up-from-the-bottom-on-mobile/)

## [CSS Intelligence: Speculating On The Future Of A Smarter Language](https://www.smashingmagazine.com/2025/07/css-intelligence-speculating-future-smarter-language/)

Gabriel Shoyombo explores how smart CSS has become over the years, where it is heading, the challenges it addresses, whether it is becoming too complex, and how developers are reacting to this shift.

[Read more](https://www.smashingmagazine.com/2025/07/css-intelligence-speculating-future-smarter-language/)

# Tools & Resources

## [](https://andreruffert.github.io/syntax-highlight-element/)

A custom element that uses the CSS Custom Highlight API for syntax highlighting.

[Check it out](https://andreruffert.github.io/syntax-highlight-element/)

## [Unencumbered  Web Component](https://github.com/zachleat/line-numbers)

A web component to add line numbers next to various HTML elements.

[Check it out](https://github.com/zachleat/line-numbers)

# Inspiration

## [AI Keys ](https://codepen.io/jh3y/pen/OPyPRLK)✨

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/587dfcc1-151a-4fbb-a93a-eb3b024fe43e/ai-keys.jpg?t=1752755198)
Follow image link: (https://codepen.io/jh3y/pen/OPyPRLK)
Caption:

Another stunning, realistic demo created by Jhey Tompkins. _(Speaking of AI, have you already subscribed to my new __[AI Developer newsletter](https://aideveloperweekly.com/)__?)_

[Get inspired](https://codepen.io/jh3y/pen/OPyPRLK)
            """.trimIndent()

        // When
        val result = parser.parse(emailContent)

        // Headlines
        val headlines = result.filter { it.section == "Headlines" }
        assertTrue(
            headlines.any {
                it.title == ":has() Is More Than a Parent Selector" &&
                    it.link == "https://www.youtube.com/watch?v=cxSowU9sDdU" &&
                    it.content.contains("Kevin Powell explores")
            },
        )
        assertTrue(
            headlines.any {
                it.title == "The Gap Strikes Back: Now Stylable" &&
                    it.link == "https://css-tricks.com/the-gap-strikes-back-now-stylable/" &&
                    it.content.contains("Patrick Brosset explains")
            },
        )

        // What Have I Been up To
        val whatUp = result.filter { it.section == "What Have I Been up To" }
        assertTrue(
            whatUp.any {
                it.title == "AI Developer Weekly" &&
                    it.link == "https://aideveloperweekly.com/" &&
                    it.content.contains("exploring various coding approaches")
            },
        )

        // Articles & Tutorials
        val articles = result.filter { it.section == "Articles & Tutorials" }
        assertTrue(
            articles.any {
                it.title == "Custom Select (That Comes Up From the Bottom on Mobile)" &&
                    it.link == "https://frontendmasters.com/blog/custom-select-that-comes-up-from-the-bottom-on-mobile/" &&
                    it.content.contains("Chris Coyier demonstrates")
            },
        )
        assertTrue(
            articles.any {
                it.title == "CSS Intelligence: Speculating On The Future Of A Smarter Language" &&
                    it.link == "https://www.smashingmagazine.com/2025/07/css-intelligence-speculating-future-smarter-language/" &&
                    it.content.contains("Gabriel Shoyombo explores")
            },
        )

        // Tools & Resources
        val tools = result.filter { it.section == "Tools & Resources" }
        assertTrue(
            tools.any {
                it.title == "" &&
                    it.link == "https://andreruffert.github.io/syntax-highlight-element/" &&
                    it.content.contains("CSS Custom Highlight API")
            },
        )
        assertTrue(
            tools.any {
                it.title == "Unencumbered  Web Component" &&
                    it.link == "https://github.com/zachleat/line-numbers" &&
                    it.content.contains("line numbers next to various HTML elements")
            },
        )
    }

    @Test
    fun `CSS Weekly 뉴스레터 전체 파싱 결과 검증`() {
        // Given
        val emailContent =
            """
# Headlines

## [:has() Is More Than a Parent Selector](https://www.youtube.com/watch?v=cxSowU9sDdU)

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/9cb464df-2653-45a5-b09f-4e32b35ce26d/has-is-more-than-a-parent-selector.png?t=1752754917)
Follow image link: (https://www.youtube.com/watch?v=cxSowU9sDdU)
Caption:

Kevin Powell explores some creative ways to utilize `:has()` pseudo-class.

[Watch video](https://www.youtube.com/watch?v=cxSowU9sDdU)

## [The Gap Strikes Back: Now Stylable](https://css-tricks.com/the-gap-strikes-back-now-stylable/)

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/b4ba31fc-6b8d-4480-bf2d-206409e54986/the-gap-strikes-back-now-stylable.png?t=1752754941)
Follow image link: (https://css-tricks.com/the-gap-strikes-back-now-stylable/)
Caption:

Patrick Brosset explains how a new CSS feature enables you to style gap areas.

[Read more](https://css-tricks.com/the-gap-strikes-back-now-stylable/)

# What Have I Been up To

## [AI Developer Weekly](https://aideveloperweekly.com/)

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/e3d20fc1-1961-4a0d-afcb-d83ef288dbd7/ai-develoeper-weekly.jpg?t=1752239406)
Follow image link: (https://aideveloperweekly.com/)
Caption:

I've been exploring various coding approaches using AI tools recently and realized that there's much more potential in those tools than I initially thought. I want to dig deeper and explore more, and as with CSS, I want to share what I learn with you. It will be hand-curated with only the top-quality content.

The first issue is just around the corner, so sign up today so you don't miss it.

[Learn more](https://aideveloperweekly.com/)

# Articles & Tutorials

## [Custom Select (That Comes Up From the Bottom on Mobile)](https://frontendmasters.com/blog/custom-select-that-comes-up-from-the-bottom-on-mobile/)

Chris Coyier demonstrates how to create a beautiful and functional custom select.

[Read more](https://frontendmasters.com/blog/custom-select-that-comes-up-from-the-bottom-on-mobile/)

## [CSS Intelligence: Speculating On The Future Of A Smarter Language](https://www.smashingmagazine.com/2025/07/css-intelligence-speculating-future-smarter-language/)

Gabriel Shoyombo explores how smart CSS has become over the years, where it is heading, the challenges it addresses, whether it is becoming too complex, and how developers are reacting to this shift.

[Read more](https://www.smashingmagazine.com/2025/07/css-intelligence-speculating-future-smarter-language/)

## [A revisit of the Every Layout sidebar with :has() and selector performance](https://piccalil.li/blog/a-revisit-of-the-every-layout-sidebar-with-has-and-selector-performance/)

Heydon Pickering explores how modern CSS selectors can improve some common layout patterns.

[Read more](https://piccalil.li/blog/a-revisit-of-the-every-layout-sidebar-with-has-and-selector-performance/)

## [Playing With the New Caret CSS Properties](https://blogs.igalia.com/mrego/playing-with-the-new-caret-css-properties/)

Manuel Rego Casasnovas gives a brief introduction to the new `caret-animation` and `caret-shape` CSS properties.

[Read more](https://blogs.igalia.com/mrego/playing-with-the-new-caret-css-properties/)

## [Setting Line Length in CSS (and Fitting Text to a Container)](https://css-tricks.com/setting-line-length-in-css-and-fitting-text-to-a-container/)

Daniel Schwarz explores different ways to control line length when working with text, including two proposed properties that could make it easier in the future.

[Read more](https://css-tricks.com/setting-line-length-in-css-and-fitting-text-to-a-container/)

# Tools & Resources

## [](https://andreruffert.github.io/syntax-highlight-element/)

A custom element that uses the CSS Custom Highlight API for syntax highlighting.

[Check it out](https://andreruffert.github.io/syntax-highlight-element/)

## [Unencumbered  Web Component](https://github.com/zachleat/line-numbers)

A web component to add line numbers next to various HTML elements.

[Check it out](https://github.com/zachleat/line-numbers)

# Inspiration

## [AI Keys ](https://codepen.io/jh3y/pen/OPyPRLK)✨

View image: (https://media.beehiiv.com/cdn-cgi/image/fit=scale-down,format=auto,onerror=redirect,quality=80/uploads/asset/file/587dfcc1-151a-4fbb-a93a-eb3b024fe43e/ai-keys.jpg?t=1752755198)
Follow image link: (https://codepen.io/jh3y/pen/OPyPRLK)
Caption:

Another stunning, realistic demo created by Jhey Tompkins. _(Speaking of AI, have you already subscribed to my new __[AI Developer newsletter](https://aideveloperweekly.com/)__?)_

[Get inspired](https://codepen.io/jh3y/pen/OPyPRLK)
            """.trimIndent()

        // When
        val result = parser.parse(emailContent)
        result.forEachIndexed { idx, it -> println("[DEBUG] $idx: $it") }

        // Then - 전체 기사 개수 및 순서 검증 (Headlines 2, What Have I Been up To 1, Articles 5, Tools 2, Inspiration 1)
        assertEquals(11, result.size) // AI Keys 포함해서 11개

        verifyArticle(
            result[0],
            ":has() Is More Than a Parent Selector",
            "https://www.youtube.com/watch?v=cxSowU9sDdU",
            "Headlines",
            "Kevin Powell explores",
        )
        verifyArticle(
            result[1],
            "The Gap Strikes Back: Now Stylable",
            "https://css-tricks.com/the-gap-strikes-back-now-stylable/",
            "Headlines",
            "Patrick Brosset explains",
        )
        verifyArticle(
            result[2],
            "AI Developer Weekly",
            "https://aideveloperweekly.com/",
            "What Have I Been up To",
            "exploring various coding approaches",
        )
        verifyArticle(
            result[3],
            "Custom Select (That Comes Up From the Bottom on Mobile)",
            "https://frontendmasters.com/blog/custom-select-that-comes-up-from-the-bottom-on-mobile/",
            "Articles & Tutorials",
            "Chris Coyier demonstrates",
        )
        verifyArticle(
            result[4],
            "CSS Intelligence: Speculating On The Future Of A Smarter Language",
            "https://www.smashingmagazine.com/2025/07/css-intelligence-speculating-future-smarter-language/",
            "Articles & Tutorials",
            "Gabriel Shoyombo explores",
        )
        verifyArticle(
            result[5],
            "A revisit of the Every Layout sidebar with :has() and selector performance",
            "https://piccalil.li/blog/a-revisit-of-the-every-layout-sidebar-with-has-and-selector-performance/",
            "Articles & Tutorials",
            "Heydon Pickering explores",
        )
        verifyArticle(
            result[6],
            "Playing With the New Caret CSS Properties",
            "https://blogs.igalia.com/mrego/playing-with-the-new-caret-css-properties/",
            "Articles & Tutorials",
            "Manuel Rego Casasnovas gives",
        )
        verifyArticle(
            result[7],
            "Setting Line Length in CSS (and Fitting Text to a Container)",
            "https://css-tricks.com/setting-line-length-in-css-and-fitting-text-to-a-container/",
            "Articles & Tutorials",
            "Daniel Schwarz explores",
        )
        verifyArticle(
            result[8],
            "",
            "https://andreruffert.github.io/syntax-highlight-element/",
            "Tools & Resources",
            "CSS Custom Highlight API",
        )
        verifyArticle(
            result[9],
            "Unencumbered  Web Component",
            "https://github.com/zachleat/line-numbers",
            "Tools & Resources",
            "line numbers next to various HTML elements",
        )
        verifyArticle(
            result[10],
            "AI Keys",
            "https://codepen.io/jh3y/pen/OPyPRLK",
            "Inspiration",
            "Jhey Tompkins",
        )
    }

    private fun verifyArticle(
        article: MailContent,
        expectedTitle: String,
        expectedLink: String,
        expectedSection: String,
        expectedContentSnippet: String,
    ) {
        assertNotNull(article, "Article with title '$expectedTitle' not found")
        assertEquals(expectedTitle, article.title)
        assertEquals(expectedLink, article.link)
        assertEquals(expectedSection, article.section)
        assertTrue(
            article.content.contains(expectedContentSnippet),
            "Article content should contain snippet '$expectedContentSnippet'",
        )
    }

    @Test
    fun `isTarget 테스트`() {
        assertTrue(parser.isTarget("css-weekly@beehiiv.com"))
        assertTrue(parser.isTarget("CSS Weekly"))
    }
}

package com.nexters.newsletterfeeder.parser

import com.nexters.external.parser.SwiftVincentParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SwiftVincentParserTest {
    private val parser = SwiftVincentParser()

    @Test
    fun `isTarget should return true for Swift with Vincent newsletter`() {
        assertTrue(parser.isTarget("Swift with Vincent <vincent@swiftwithvincent.com>"))
        assertTrue(parser.isTarget("newsletter@swiftwithvincent.com"))
    }

    @Test
    fun `parse should extract content from Swift Vincent newsletter`() {
        // Load test file
        val content = File("src/test/resources/swift-vincent").readText()

        // Parse content
        val result = parser.parse(content)

        // Check that the main article is extracted
        assertEquals("ChatGPT in Xcode 26: there’s a hidden prompt!", result[0].title)
    }
}

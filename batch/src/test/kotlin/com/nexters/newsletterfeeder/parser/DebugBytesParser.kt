package com.nexters.newsletterfeeder.parser

fun main() {
    val parser = BytesParser()

    val sampleEmail =
        """
        Content-Type: text/html; charset="utf-8"

        <!DOCTYPE html>
        <html>
            <body>
                <p>Welcome to #409.</p>

                <h2>The Main Thing</h2>
                <h3>America's NextJS Top Model</h3>
                <p>The Triangle Gods blessed us with <a href="https://nextjs.org/blog/next-15-4">Next.js 15.4</a> on Monday, and it comes with two riveting updates:</p>
                <p>Check out the <a href="https://aretweturboyet.com/">turbo build times</a> and see if they live up to the hype.</p>

                <h2>Cool Bits</h2>
                <ol>
                    <li>
                        <a href="https://hashbrown.dev/blog/2025-07-16-hashbrown-v-0-2-0">Hashbrown just released v0.2</a> of its framework for building AI-powered UIs in Angular and React.
                    </li>
                    <li>
                        Leerob wrote about <a href="https://leerob.com/vercel">5 things he learned from 5 years at Vercel</a>. Shoutout to the 🐐.
                    </li>
                    <li>
                        <a href="https://stack.convex.dev/convex-resend">Convex just launched a Resend Component</a> that lets you easily integrate Resend's DX-focused email service.
                    </li>
                </ol>

                <p><a href="https://workos.com/sponsor">WorkOS Sponsor Link</a></p>
                <p><a href="https://bytes.dev/advertise">Sponsored content</a></p>
            </body>
        </html>
        """.trimIndent()

    println("=== Debugging BytesParser ===")
    println("1. Is target: ${parser.isTarget("Bytes <noreply@ui.dev>")}")

    val result = parser.parse(sampleEmail)
    println("2. Parse result count: ${result.size}")

    result.forEachIndexed { index, content ->
        println("[$index] Title: '${content.title}'")
        println("[$index] Link: '${content.link}'")
        println("[$index] Section: '${content.section}'")
        println("[$index] Content preview: '${content.content.take(100)}...'")
        println("---")
    }

    println("\n=== Testing HtmlTextExtractor directly ===")
    val links = HtmlTextExtractor.extractLinks(sampleEmail)
    println("Direct link extraction count: ${links.size}")
    links.forEach { (title, url) ->
        println("- '$title' -> '$url'")
    }
}

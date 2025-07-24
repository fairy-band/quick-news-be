package com.nexters.newsletterfeeder.parser

class GoWeeklyParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val emailFromMatch = Regex("emailFrom=\\[([^\\]]+)\\]").find(content)
        val emailFrom = emailFromMatch?.groupValues?.get(1) ?: return emptyList()

        if (!isTarget(emailFrom)) {
            return emptyList()
        }

        val textContentStart = content.indexOf("emailTextContent=")
        val textContentEnd = content.lastIndexOf(", emailHtmlContent=")

        if (textContentStart == -1 || textContentEnd == -1 || textContentEnd <= textContentStart) {
            return emptyList()
        }

        val emailTextContent = content.substring(textContentStart + 17, textContentEnd).trim()

        val issueInfo = extractIssueInfo(emailTextContent)

        val articles = mutableListOf<MailContent>()

        articles.addAll(parseMainArticles(emailTextContent, issueInfo))

        articles.addAll(parseAdditionalArticles(emailTextContent, issueInfo))

        return articles
    }

    private fun parseMainArticles(
        content: String,
        issueInfo: IssueInfo,
    ): List<MailContent> {
        val articles = mutableListOf<MailContent>()
        val lines = content.lines()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("* ")) {
                val title = line.substring(2).trim()

                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (nextLine.startsWith("( ") && nextLine.endsWith(" )")) {
                        val url = nextLine.substring(2, nextLine.length - 2).trim()

                        if (url.startsWith("http")) {
                            val (description, author) = collectDescriptionAndAuthor(lines, i + 2)

                            if (!description.contains("SPONSOR", ignoreCase = true) &&
                                !author.contains("SPONSOR", ignoreCase = true)
                            ) {
                                val section = determineSection(content, i)

                                articles.add(
                                    MailContent(
                                        title = title,
                                        content = "[${issueInfo.displayName}] $description${
                                            if (author.isNotEmpty()) {
                                                " by $author"
                                            } else {
                                                ""
                                            }
                                        }",
                                        link = url,
                                        section = section,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            i++
        }

        return articles
    }

    private fun parseAdditionalArticles(
        content: String,
        issueInfo: IssueInfo,
    ): List<MailContent> {
        val articles = mutableListOf<MailContent>()
        val lines = content.lines()

        for (line in lines) {
            val trimmedLine = line.trim()

            if (trimmedLine.startsWith("📄 ")) {
                val restOfLine = trimmedLine.substring(2).trim()

                val urlStart = restOfLine.indexOf(" ( ")
                val urlEnd = restOfLine.indexOf(" ) ", urlStart)

                if (urlStart != -1 && urlEnd != -1) {
                    val title = restOfLine.substring(0, urlStart).trim()
                    val url = restOfLine.substring(urlStart + 3, urlEnd).trim()
                    val author = restOfLine.substring(urlEnd + 3).trim()

                    articles.add(
                        MailContent(
                            title = title,
                            content = "[${issueInfo.displayName}] Article${if (author.isNotEmpty()) " by $author" else ""}",
                            link = url,
                            section = "Article",
                        ),
                    )
                }
            }
        }

        return articles
    }

    private fun determineSection(
        content: String,
        articleLineIndex: Int,
    ): String {
        val lines = content.lines()

        for (i in articleLineIndex downTo 0) {
            val line = lines[i].trim()
            when {
                line.contains("🛠 CODE & TOOLS") || line.contains("CODE & TOOLS") -> return "Code & Tools"
                line.contains("GO WEEKLY") -> return "Go Weekly"
            }
        }

        return "Go Weekly" // 기본값
    }

    private fun collectDescriptionAndAuthor(
        lines: List<String>,
        startIndex: Int,
    ): Pair<String, String> {
        val descriptionLines = mutableListOf<String>()
        var author = ""
        var i = startIndex

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("* ") ||
                line.startsWith("--") ||
                line.contains("CODE & TOOLS") ||
                line.startsWith("📄")
            ) {
                break
            }

            if (line.startsWith("-- ")) {
                author = line.substring(3).trim()
                break
            }

            if (line.startsWith("— ")) {
                descriptionLines.add(line.substring(2).trim())
            } else if (line.contains(" — ")) {
                val descStart = line.indexOf(" — ") + 3
                descriptionLines.add(line.substring(descStart).trim())
            } else if (descriptionLines.isNotEmpty() && line.isNotEmpty()) {
                descriptionLines.add(line)
            }

            i++
        }

        return Pair(descriptionLines.joinToString(" ").trim(), author)
    }

    private fun extractIssueInfo(content: String): IssueInfo {
        var issueNumber = "Unknown"
        val hashMatch = Regex("#(\\d+)").find(content)
        if (hashMatch != null) {
            issueNumber = hashMatch.groupValues[1]
        }

        var issueDate = "Unknown date"
        val dateMatch = Regex("#\\d+ — ([^\\n]+)").find(content)
        if (dateMatch != null) {
            issueDate = dateMatch.groupValues[1].trim()
        }

        return IssueInfo(
            number = issueNumber,
            date = issueDate,
            displayName = "Go Weekly #$issueNumber",
        )
    }

    private data class IssueInfo(
        val number: String,
        val date: String,
        val displayName: String,
    )

    companion object {
        private const val NEWSLETTER_NAME = "Golang Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "golangweekly.com"
    }
}

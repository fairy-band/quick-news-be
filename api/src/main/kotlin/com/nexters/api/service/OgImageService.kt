package com.nexters.api.service

import org.springframework.stereotype.Service
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Service
class OgImageService {
    companion object {
        private const val OG_WIDTH = 1200
        private const val OG_HEIGHT = 630
        private const val INSTA_WIDTH = 1080
        private const val INSTA_HEIGHT = 1080
    }

    fun generateOgImage(
        title: String,
        tag: String,
        newsletterName: String,
        textColor: String = "#DCFF64"
    ): ByteArray {
        return generateImage(OG_WIDTH, OG_HEIGHT, 72, 64, title, tag, newsletterName, textColor, isInsta = false)
    }

    fun generateInstaImage(
        title: String,
        tag: String,
        newsletterName: String,
        textColor: String = "#DCFF64"
    ): ByteArray {
        // 인스타는 상하 여백을 더 넓게 잡아서 텍스트 블록이 중앙에 오도록 배치합니다.
        return generateImage(INSTA_WIDTH, INSTA_HEIGHT, 100, 320, title, tag, newsletterName, textColor, isInsta = true)
    }

    private fun generateImage(
        width: Int,
        height: Int,
        paddingX: Int,
        paddingTop: Int,
        title: String,
        tag: String,
        newsletterName: String,
        textColor: String,
        isInsta: Boolean
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        // Enable high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

        val accentColor = decodeColorOrDefault(textColor)

        // 1. Draw gradient background
        drawGradientBackground(g2d, width, height)

        // 2. Draw decorative glow orbs
        drawDecorativeOrbs(g2d, accentColor, width, height)

        // 3. Draw subtle noise texture for depth
        drawNoiseTexture(g2d, width, height)

        // 4. Content alignment logic
        var currentY = paddingTop
        if (isInsta) {
            // 인스타는 1:1 비율이므로 대략 세로 중앙을 기점으로 그리도록 조정
            currentY = (height - 450) / 2
        }

        // 5. Draw tag badge
        val tagBadgeBottomY = drawTagBadge(g2d, tag, paddingX, currentY, accentColor)

        // 6. Draw title
        val titleFontSize = if (isInsta) 84 else 72
        val titleEndY = drawTitle(g2d, title, paddingX, tagBadgeBottomY + (if (isInsta) 48 else 32), width, titleFontSize)

        // 7. Draw bottom section (separator + newsletter name)
        drawBottomSection(g2d, newsletterName, paddingX, width, height, accentColor, isInsta)

        g2d.dispose()

        // Convert to PNG
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", outputStream)
        return outputStream.toByteArray()
    }

    private fun decodeColorOrDefault(textColor: String): Color {
        val color = textColor.trim()
        val normalizedColor =
            when {
                color.startsWith("#") || color.startsWith("0x", ignoreCase = true) -> color
                else -> "#$color"
            }

        return runCatching { Color.decode(normalizedColor) }
            .getOrDefault(Color(0xDC, 0xFF, 0x64))
    }

    private fun drawGradientBackground(g2d: Graphics2D, width: Int, height: Int) {
        val gradient = GradientPaint(
            0f, 0f, Color(18, 18, 24), 
            width.toFloat(), height.toFloat(), Color(28, 22, 38)
        )
        g2d.paint = gradient
        g2d.fillRect(0, 0, width, height)
    }

    private fun drawDecorativeOrbs(g2d: Graphics2D, accentColor: Color, width: Int, height: Int) {
        val originalComposite = g2d.composite

        // Large accent glow — top right area
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f)
        val glowColor1 = Color(accentColor.red, accentColor.green, accentColor.blue)
        drawRadialGlow(g2d, width - 200, -80, 500, glowColor1)

        // Secondary subtle glow — bottom left
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.05f)
        val glowColor2 = Color(
            Math.min(255, accentColor.red + 40),
            Math.min(255, accentColor.green + 20),
            Math.min(255, accentColor.blue + 60)
        )
        drawRadialGlow(g2d, 100, height - 100, 400, glowColor2)

        g2d.composite = originalComposite
    }

    private fun drawRadialGlow(g2d: Graphics2D, cx: Int, cy: Int, radius: Int, color: Color) {
        val steps = 30
        for (i in steps downTo 0) {
            val ratio = i.toFloat() / steps
            val r = (radius * ratio).toInt()
            val alpha = ((1.0f - ratio) * 255).toInt().coerceIn(0, 255)
            g2d.color = Color(color.red, color.green, color.blue, alpha)
            g2d.fillOval(cx - r, cy - r, r * 2, r * 2)
        }
    }

    private fun drawNoiseTexture(g2d: Graphics2D, width: Int, height: Int) {
        val originalComposite = g2d.composite
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.03f)
        val random = java.util.Random(42) // deterministic seed
        val particleCount = (width * height) / 252 // scale particles to resolution
        for (i in 0 until particleCount) {
            val x = random.nextInt(width)
            val y = random.nextInt(height)
            val brightness = 128 + random.nextInt(128)
            g2d.color = Color(brightness, brightness, brightness)
            g2d.fillRect(x, y, 1, 1)
        }
        g2d.composite = originalComposite
    }

    private fun drawTagBadge(g2d: Graphics2D, tag: String, x: Int, y: Int, accentColor: Color): Int {
        val font = Font("SansSerif", Font.BOLD, 26)
        g2d.font = font
        val metrics = g2d.fontMetrics

        val displayTag = "#$tag"
        val textWidth = metrics.stringWidth(displayTag)
        val textHeight = metrics.height
        val paddingH = 22
        val paddingV = 12
        val badgeWidth = textWidth + paddingH * 2
        val badgeHeight = textHeight + paddingV * 2
        val badgeRadius = badgeHeight

        val originalComposite = g2d.composite
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f)
        g2d.color = accentColor
        g2d.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), badgeWidth.toFloat(), badgeHeight.toFloat(), badgeRadius.toFloat(), badgeRadius.toFloat()))

        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f)
        g2d.color = accentColor
        g2d.stroke = BasicStroke(1.2f)
        g2d.draw(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), badgeWidth.toFloat(), badgeHeight.toFloat(), badgeRadius.toFloat(), badgeRadius.toFloat()))
        g2d.composite = originalComposite

        g2d.color = accentColor
        g2d.drawString(displayTag, x + paddingH, y + paddingV + metrics.ascent)

        return y + badgeHeight
    }

    private fun drawTitle(g2d: Graphics2D, title: String, x: Int, y: Int, imageWidth: Int, fontSize: Int): Int {
        val baseFont = Font("SansSerif", Font.BOLD, fontSize)
        val attributes = HashMap<TextAttribute, Any>()
        attributes[TextAttribute.TRACKING] = -0.02f
        val font = baseFont.deriveFont(attributes)
        g2d.font = font

        g2d.color = Color.WHITE

        val maxWidth = imageWidth - (x * 2)
        val lineHeight = (fontSize * 1.25).toInt()
        val maxLines = 5

        val lines = wrapText(g2d, title, maxWidth)
        var currentY = y

        for ((index, line) in lines.withIndex()) {
            if (index >= maxLines) break
            val displayLine = if (index == maxLines - 1 && index < lines.size - 1) {
                truncateWithEllipsis(g2d, line, maxWidth)
            } else {
                line
            }
            g2d.drawString(displayLine, x, currentY + g2d.fontMetrics.ascent)
            currentY += lineHeight
        }

        return currentY
    }

    private fun drawBottomSection(
        g2d: Graphics2D, newsletterName: String, x: Int, 
        imageWidth: Int, imageHeight: Int, accentColor: Color, isInsta: Boolean
    ) {
        val bottomY = if (isInsta) imageHeight - 100 else imageHeight - 64
        val originalComposite = g2d.composite

        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f)
        g2d.color = Color.WHITE
        g2d.stroke = BasicStroke(1f)
        g2d.drawLine(x, bottomY - 44, imageWidth - x, bottomY - 44)
        g2d.composite = originalComposite

        val font = Font("SansSerif", Font.PLAIN, if (isInsta) 28 else 24)
        g2d.font = font
        g2d.color = Color(180, 180, 190)

        val displayText = "from $newsletterName"
        g2d.drawString(displayText, x, bottomY - 10)

        if (isInsta) {
            val ctaFont = Font("SansSerif", Font.BOLD, 26)
            g2d.font = ctaFont
            g2d.color = accentColor
            val ctaText = "옆으로 넘겨 요약 보기 👉"
            val ctaMetrics = g2d.fontMetrics
            g2d.drawString(ctaText, imageWidth - x - ctaMetrics.stringWidth(ctaText), bottomY - 10)
        } else {
            val badgeSize = 44
            val badgeCx = imageWidth - x - badgeSize / 2
            val badgeCy = bottomY - 28

            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f)
            g2d.color = accentColor
            g2d.fillOval(badgeCx - badgeSize / 2 - 4, badgeCy - badgeSize / 2 - 4, badgeSize + 8, badgeSize + 8)
            g2d.composite = originalComposite

            g2d.color = accentColor
            g2d.fillOval(badgeCx - badgeSize / 2, badgeCy - badgeSize / 2, badgeSize, badgeSize)

            val brandFont = Font("SansSerif", Font.BOLD, 22)
            g2d.font = brandFont
            g2d.color = Color(18, 18, 24)
            val brandText = "쏙"
            val brandMetrics = g2d.fontMetrics
            val textX = badgeCx - brandMetrics.stringWidth(brandText) / 2
            val textY = badgeCy + brandMetrics.ascent / 2 - 1
            g2d.drawString(brandText, textX, textY)
        }
    }

    private fun wrapText(g2d: Graphics2D, text: String, maxWidth: Int): List<String> {
        val metrics = g2d.fontMetrics
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""

        for (word in words) {
            if (currentLine.isEmpty()) {
                if (metrics.stringWidth(word) > maxWidth) {
                    lines.addAll(wrapByCharacter(g2d, word, maxWidth))
                    continue
                }
                currentLine = word
            } else {
                val testLine = "$currentLine $word"
                if (metrics.stringWidth(testLine) <= maxWidth) {
                    currentLine = testLine
                } else {
                    lines.add(currentLine)
                    if (metrics.stringWidth(word) > maxWidth) {
                        lines.addAll(wrapByCharacter(g2d, word, maxWidth))
                        currentLine = ""
                        continue
                    }
                    currentLine = word
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }

    private fun wrapByCharacter(g2d: Graphics2D, text: String, maxWidth: Int): List<String> {
        val metrics = g2d.fontMetrics
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (char in text) {
            val testLine = currentLine + char
            if (metrics.stringWidth(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = char.toString()
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }

    private fun truncateWithEllipsis(g2d: Graphics2D, text: String, maxWidth: Int): String {
        val metrics = g2d.fontMetrics
        if (metrics.stringWidth(text) <= maxWidth) return text

        val ellipsis = "…"
        val ellipsisWidth = metrics.stringWidth(ellipsis)
        var truncated = text

        while (truncated.isNotEmpty() && metrics.stringWidth(truncated) + ellipsisWidth > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return truncated + ellipsis
    }
}

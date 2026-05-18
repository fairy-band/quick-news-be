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
        private const val IMAGE_WIDTH = 1200
        private const val IMAGE_HEIGHT = 630
        private const val CONTENT_PADDING_X = 72
        private const val CONTENT_PADDING_TOP = 64
    }

    fun generateOgImage(
        title: String,
        tag: String,
        newsletterName: String,
        textColor: String = "#DCFF64"
    ): ByteArray {
        val image = BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        // Enable high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

        val accentColor = Color.decode(textColor)

        // 1. Draw gradient background
        drawGradientBackground(g2d, accentColor)

        // 2. Draw decorative glow orbs
        drawDecorativeOrbs(g2d, accentColor)

        // 3. Draw subtle noise texture for depth
        drawNoiseTexture(g2d)

        // 4. Draw tag badge
        val tagBadgeBottomY = drawTagBadge(g2d, tag, CONTENT_PADDING_X, CONTENT_PADDING_TOP, accentColor)

        // 5. Draw title
        val titleEndY = drawTitle(g2d, title, CONTENT_PADDING_X, tagBadgeBottomY + 32, accentColor)

        // 6. Draw bottom section (separator + newsletter name)
        drawBottomSection(g2d, newsletterName, CONTENT_PADDING_X, accentColor)

        g2d.dispose()

        // Convert to PNG
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", outputStream)
        return outputStream.toByteArray()
    }

    /**
     * 깊이감 있는 다크 그라데이션 배경
     */
    private fun drawGradientBackground(
        g2d: Graphics2D,
        accentColor: Color
    ) {
        // Main dark gradient: top-left to bottom-right
        val gradient =
            GradientPaint(
                0f,
                0f,
                Color(18, 18, 24),
                IMAGE_WIDTH.toFloat(),
                IMAGE_HEIGHT.toFloat(),
                Color(28, 22, 38)
            )
        g2d.paint = gradient
        g2d.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT)
    }

    /**
     * 배경에 은은한 글로우 효과 (accent 색상 기반)
     */
    private fun drawDecorativeOrbs(
        g2d: Graphics2D,
        accentColor: Color
    ) {
        val originalComposite = g2d.composite

        // Large accent glow — top right area
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f)
        val glowColor1 = Color(accentColor.red, accentColor.green, accentColor.blue)
        drawRadialGlow(g2d, IMAGE_WIDTH - 200, -80, 500, glowColor1)

        // Secondary subtle glow — bottom left
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.05f)
        val glowColor2 =
            Color(
                Math.min(255, accentColor.red + 40),
                Math.min(255, accentColor.green + 20),
                Math.min(255, accentColor.blue + 60)
            )
        drawRadialGlow(g2d, 100, IMAGE_HEIGHT - 100, 400, glowColor2)

        g2d.composite = originalComposite
    }

    /**
     * 원형 그라데이션 글로우 효과
     */
    private fun drawRadialGlow(
        g2d: Graphics2D,
        cx: Int,
        cy: Int,
        radius: Int,
        color: Color
    ) {
        val steps = 30
        for (i in steps downTo 0) {
            val ratio = i.toFloat() / steps
            val r = (radius * ratio).toInt()
            val alpha = ((1.0f - ratio) * 255).toInt().coerceIn(0, 255)
            g2d.color = Color(color.red, color.green, color.blue, alpha)
            g2d.fillOval(cx - r, cy - r, r * 2, r * 2)
        }
    }

    /**
     * 미세한 노이즈 텍스처로 깊이감 추가
     */
    private fun drawNoiseTexture(g2d: Graphics2D) {
        val originalComposite = g2d.composite
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.03f)
        val random = java.util.Random(42) // deterministic seed
        for (i in 0 until 3000) {
            val x = random.nextInt(IMAGE_WIDTH)
            val y = random.nextInt(IMAGE_HEIGHT)
            val brightness = 128 + random.nextInt(128)
            g2d.color = Color(brightness, brightness, brightness)
            g2d.fillRect(x, y, 1, 1)
        }
        g2d.composite = originalComposite
    }

    /**
     * 태그를 글래스모피즘 스타일 뱃지로 표시
     */
    private fun drawTagBadge(
        g2d: Graphics2D,
        tag: String,
        x: Int,
        y: Int,
        accentColor: Color
    ): Int {
        val font = Font("SansSerif", Font.BOLD, 22)
        g2d.font = font
        val metrics = g2d.fontMetrics

        val displayTag = "#$tag"
        val textWidth = metrics.stringWidth(displayTag)
        val textHeight = metrics.height
        val paddingH = 20
        val paddingV = 10
        val badgeWidth = textWidth + paddingH * 2
        val badgeHeight = textHeight + paddingV * 2
        val badgeRadius = badgeHeight

        // Frosted glass badge background
        val originalComposite = g2d.composite
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f)
        g2d.color = accentColor
        g2d.fill(
            RoundRectangle2D.Float(
                x.toFloat(),
                y.toFloat(),
                badgeWidth.toFloat(),
                badgeHeight.toFloat(),
                badgeRadius.toFloat(),
                badgeRadius.toFloat()
            )
        )

        // Badge border
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f)
        g2d.color = accentColor
        g2d.stroke = BasicStroke(1.2f)
        g2d.draw(
            RoundRectangle2D.Float(
                x.toFloat(),
                y.toFloat(),
                badgeWidth.toFloat(),
                badgeHeight.toFloat(),
                badgeRadius.toFloat(),
                badgeRadius.toFloat()
            )
        )
        g2d.composite = originalComposite

        // Badge text
        g2d.color = accentColor
        g2d.drawString(displayTag, x + paddingH, y + paddingV + metrics.ascent)

        return y + badgeHeight
    }

    /**
     * 메인 타이틀 - 크고 굵은 폰트, 자간 조절
     */
    private fun drawTitle(
        g2d: Graphics2D,
        title: String,
        x: Int,
        y: Int,
        accentColor: Color
    ): Int {
        // Create font with letter spacing (tracking)
        val baseFont = Font("SansSerif", Font.BOLD, 72)
        val attributes = HashMap<TextAttribute, Any>()
        attributes[TextAttribute.TRACKING] = -0.02f // slightly tighter
        val font = baseFont.deriveFont(attributes)
        g2d.font = font

        g2d.color = Color.WHITE

        val maxWidth = IMAGE_WIDTH - (CONTENT_PADDING_X * 2)
        val lineHeight = 88
        val maxLines = 4

        val lines = wrapText(g2d, title, maxWidth)
        var currentY = y

        for ((index, line) in lines.withIndex()) {
            if (index >= maxLines) break

            val displayLine =
                if (index == maxLines - 1 && index < lines.size - 1) {
                    // Truncate with ellipsis on last allowed line
                    truncateWithEllipsis(g2d, line, maxWidth)
                } else {
                    line
                }

            g2d.drawString(displayLine, x, currentY + g2d.fontMetrics.ascent)
            currentY += lineHeight
        }

        return currentY
    }

    /**
     * 하단 영역: 구분선 + 뉴스레터 이름 + 쏙 로고 뱃지
     */
    private fun drawBottomSection(
        g2d: Graphics2D,
        newsletterName: String,
        x: Int,
        accentColor: Color
    ) {
        val bottomY = IMAGE_HEIGHT - 64
        val originalComposite = g2d.composite

        // Subtle separator line
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f)
        g2d.color = Color.WHITE
        g2d.stroke = BasicStroke(1f)
        g2d.drawLine(x, bottomY - 44, IMAGE_WIDTH - CONTENT_PADDING_X, bottomY - 44)
        g2d.composite = originalComposite

        // Newsletter name
        val font = Font("SansSerif", Font.PLAIN, 24)
        g2d.font = font
        g2d.color = Color(180, 180, 190)

        val displayText = "from $newsletterName"
        g2d.drawString(displayText, x, bottomY - 10)

        // "쏙" circular logo badge on the right
        val badgeSize = 44
        val badgeCx = IMAGE_WIDTH - CONTENT_PADDING_X - badgeSize / 2
        val badgeCy = bottomY - 28

        // Badge glow (subtle outer glow)
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f)
        g2d.color = accentColor
        g2d.fillOval(badgeCx - badgeSize / 2 - 4, badgeCy - badgeSize / 2 - 4, badgeSize + 8, badgeSize + 8)
        g2d.composite = originalComposite

        // Badge filled circle
        g2d.color = accentColor
        g2d.fillOval(badgeCx - badgeSize / 2, badgeCy - badgeSize / 2, badgeSize, badgeSize)

        // "쏙" text inside badge (dark color for contrast)
        val brandFont = Font("SansSerif", Font.BOLD, 22)
        g2d.font = brandFont
        g2d.color = Color(18, 18, 24)
        val brandText = "쏙"
        val brandMetrics = g2d.fontMetrics
        val textX = badgeCx - brandMetrics.stringWidth(brandText) / 2
        val textY = badgeCy + brandMetrics.ascent / 2 - 1
        g2d.drawString(brandText, textX, textY)
    }

    /**
     * 텍스트 줄바꿈 처리 (단어 및 글자 단위)
     */
    private fun wrapText(
        g2d: Graphics2D,
        text: String,
        maxWidth: Int
    ): List<String> {
        val metrics = g2d.fontMetrics
        val lines = mutableListOf<String>()

        // 먼저 공백으로 단어 분리 시도
        val words = text.split(" ")
        var currentLine = ""

        for (word in words) {
            if (currentLine.isEmpty()) {
                // 단어 하나가 maxWidth 보다 길면 글자 단위로 분리
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
                    // 새 단어가 maxWidth보다 길면 글자 단위 분리
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

    /**
     * 글자 단위 줄바꿈 (한국어 등 공백이 적은 텍스트 대응)
     */
    private fun wrapByCharacter(
        g2d: Graphics2D,
        text: String,
        maxWidth: Int
    ): List<String> {
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

    /**
     * 텍스트가 최대 너비를 초과하면 말줄임 처리
     */
    private fun truncateWithEllipsis(
        g2d: Graphics2D,
        text: String,
        maxWidth: Int
    ): String {
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

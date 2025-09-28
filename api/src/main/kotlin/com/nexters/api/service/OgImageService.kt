package com.nexters.api.service

import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Service
class OgImageService {
    companion object {
        private const val IMAGE_WIDTH = 1200
        private const val IMAGE_HEIGHT = 630
        private const val BORDER_WIDTH = 8
        private const val CORNER_RADIUS = 24
        private const val CONTENT_PADDING = 80
    }

    fun generateOgImage(
        title: String,
        tag: String,
        newsletterName: String,
        textColor: String = "#DCFF64"
    ): ByteArray {
        val image = BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g2d = image.createGraphics()

        // Enable anti-aliasing for smooth rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        // Draw gradient border background (고정된 보라색 그라데이션)
        drawGradientBorder(g2d)

        // Draw dark inner background
        g2d.color = Color(30, 30, 30) // 어두운 배경
        g2d.fillRoundRect(
            BORDER_WIDTH,
            BORDER_WIDTH,
            IMAGE_WIDTH - (BORDER_WIDTH * 2),
            IMAGE_HEIGHT - (BORDER_WIDTH * 2),
            CORNER_RADIUS - 4,
            CORNER_RADIUS - 4
        )

        // Content positioning - 더 촘촘한 레이아웃
        val contentX = CONTENT_PADDING
        val titleStartY = CONTENT_PADDING + 80

        // Draw title (main content)
        val titleEndY = drawMainTitle(g2d, title, contentX, titleStartY, textColor)

        // Draw tag and newsletter name - 타이틀과 적당한 간격으로 배치
        val bottomY = titleEndY + 90
        drawBottomInfo(g2d, tag, newsletterName, contentX, bottomY, textColor)

        g2d.dispose()

        // Convert to byte array
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", outputStream)
        return outputStream.toByteArray()
    }

    private fun drawGradientBorder(g2d: Graphics2D) {
        // 고정된 보라색 그라데이션 테두리
        val gradient =
            GradientPaint(
                0f,
                0f,
                Color.decode("#8B5CF6"),
                IMAGE_WIDTH.toFloat(),
                IMAGE_HEIGHT.toFloat(),
                Color.decode("#A78BFA")
            )

        g2d.paint = gradient
        g2d.fillRoundRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, CORNER_RADIUS, CORNER_RADIUS)
    }

    private fun drawMainTitle(
        g2d: Graphics2D,
        title: String,
        x: Int,
        y: Int,
        textColor: String
    ): Int {
        // Use a bold, large font for the title
        val font = Font("SansSerif", Font.BOLD, 64)
        g2d.font = font

        // 동적 텍스트 색상 적용
        g2d.color = Color.decode(textColor)

        val maxWidth = IMAGE_WIDTH - (CONTENT_PADDING * 2)
        val words = title.split(" ")
        var currentLine = ""
        var currentY = y
        val lineHeight = 75 // 줄 간격을 약간 줄임

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val metrics = g2d.fontMetrics

            if (metrics.stringWidth(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    g2d.drawString(currentLine, x, currentY)
                    currentY += lineHeight
                    currentLine = word
                } else {
                    // Single word is too long, draw it anyway
                    g2d.drawString(word, x, currentY)
                    currentY += lineHeight
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            g2d.drawString(currentLine, x, currentY)
            currentY += lineHeight
        }

        // 마지막 줄의 Y 좌표 반환
        return currentY - lineHeight
    }

    private fun drawBottomInfo(
        g2d: Graphics2D,
        tag: String,
        newsletterName: String,
        x: Int,
        y: Int,
        textColor: String
    ) {
        // Set font for bottom info - 크기를 약간 줄임
        val font = Font("SansSerif", Font.PLAIN, 32)
        g2d.font = font

        // 동적 텍스트 색상 적용 (약간 어둡게)
        val baseColor = Color.decode(textColor)
        g2d.color =
            Color(
                (baseColor.red * 0.75).toInt(),
                (baseColor.green * 0.75).toInt(),
                (baseColor.blue * 0.75).toInt()
            )

        // Draw tag and newsletter name separated by |
        val bottomText = "$tag | $newsletterName"
        g2d.drawString(bottomText, x, y)
    }
}

package com.nexters.newsletterfeeder.dto

import java.nio.charset.Charset

enum class EmailCharset(
    val javaCharsetName: String,
    val aliases: List<String>
) {
    EUC_KR("EUC-KR", listOf("EUC-KR", "KS_C_5601-1987")),
    UTF_8("UTF-8", listOf("UTF-8")),
    ISO_8859_1("ISO-8859-1", listOf("ISO-8859-1")),
    US_ASCII("US-ASCII", listOf("US-ASCII"));

    companion object {
        fun findByName(charsetName: String): EmailCharset? {
            return values().find { charset ->
                charset.aliases.any { alias ->
                    alias.equals(charsetName, ignoreCase = true)
                }
            }
        }

        fun getCharset(charsetName: String): Charset {
            return findByName(charsetName)?.let { emailCharset ->
                try {
                    Charset.forName(emailCharset.javaCharsetName)
                } catch (e: Exception) {
                    Charset.forName("UTF-8")
                }
            } ?: run {
                try {
                    Charset.forName(charsetName)
                } catch (e: Exception) {
                    Charset.forName("UTF-8")
                }
            }
        }
    }
}

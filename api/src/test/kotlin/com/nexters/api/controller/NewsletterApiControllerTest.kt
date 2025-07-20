package com.nexters.api.controller

import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NewsletterApiControllerTest {
    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun setup() {
        RestAssured.port = port
        RestAssured.basePath = "/api/newsletters"
    }

    @Test
    fun `getNewsletterContents returns sample data`() {
        val userId = 1L

        Given {
            contentType(ContentType.JSON)
        } When {
            get("/contents/$userId")
        } Then {
            statusCode(HttpStatus.OK.value())
            body("cards", notNullValue())
            body("cards.size()", equalTo(1))
            body("cards[0].title", equalTo("OOP는 끝났다? Java 교육 방식에 대격변 예고!"))
            body("cards[0].category", equalTo("BE"))
            body("cards[0].topKeyword", equalTo("Java 교육"))
            body("cards[0].newsletterName", equalTo("The Awesome Java Weekly"))
        }
    }

    @Test
    fun `getNewsletterContents with default date returns today's data`() {
        val userId = 1L
        val today = LocalDate.now()
        val formattedDate = today.format(DateTimeFormatter.ISO_DATE)

        Given {
            contentType(ContentType.JSON)
        } When {
            get("/contents/$userId")
        } Then {
            statusCode(HttpStatus.OK.value())
            body("publishedDate", equalTo(formattedDate))
        }
    }
}

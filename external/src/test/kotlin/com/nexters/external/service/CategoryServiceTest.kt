package com.nexters.external.service

import com.nexters.external.repository.CategoryRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

@DataJpaTest
@Transactional
class CategoryServiceTest {
    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    private val sut: CategoryService by lazy { CategoryService(categoryRepository) }

    @Test
    @Sql(scripts = ["/sql/today-keywords-category-id.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    fun `getTodayKeywordsByCategoryId should return top 6 keywords by weight`() {
        // given
        val categoryId = 1L

        // when
        val result = sut.getTodayKeywordsByCategoryId(categoryId)

        // then
        assertEquals(6, result.size)
        assertEquals("트러블슈팅", result[0].name)
        assertEquals("자료구조", result[1].name)
        assertEquals("경험", result[2].name)
        assertEquals("데이터베이스", result[3].name)
        assertEquals("Java", result[4].name)
        assertEquals("Intellij", result[5].name)
    }
}

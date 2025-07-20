package com.nexters.external.repository

import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.jdbc.Sql

@DataJpaTest
@Sql(
    scripts = ["/sql/reserved-keywords-test-data.sql"],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class ReservedKeywordRepositoryTest {
    @Autowired
    lateinit var repository: ReservedKeywordRepository

    @Test
    fun findReservedKeywords() {
        // given
        val categoryId = 1L

        // when
        val reservedKeywords = repository.findReservedKeywordsByCategoryId(categoryId)

        // then
        then(reservedKeywords).hasSize(3)
    }
}

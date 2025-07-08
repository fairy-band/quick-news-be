package com.nexters.newsletterfeeder.apiclient

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.nexters.newsletterfeeder.dto.GeminiModel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@TestPropertySource(
    properties = [
        "ai.gemini.key=\${GEMINI_API_KEY:test-key}"
    ]
)
class GeminiClientTest {
    private lateinit var geminiClient: GeminiClient

    @BeforeEach
    fun setUp() {
        geminiClient = GeminiClient(apiKey = "test-api-key")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    fun `requestKeywords should return pure JSON without markdown formatting`() {
        val inputKeywords = listOf("인공지능", "머신러닝", "딥러닝")
        val content =
            """
            최근 인공지능 기술의 발전이 가속화되고 있습니다.
            특히 머신러닝과 딥러닝 분야에서 혁신적인 모델들이 등장하고 있으며,
            자연어처리와 컴퓨터비전 영역에서 실용적인 응용이 늘어나고 있습니다.
            """.trimIndent()

        val response = geminiClient.requestKeywords(inputKeywords, GeminiModel.TWO_ZERO_FLASH_LITE, content)

        assertNotNull(response, "Response should not be null")
        val responseText = response.text()!!.trim()

        println("=== Raw Response ===")
        println("'$responseText'")
        println("Length: ${responseText.length}")

        assertFalse(responseText.contains("```json"), "Response should not contain markdown json block")
        assertFalse(responseText.contains("```"), "Response should not contain any markdown code blocks")

        assertTrue(responseText.startsWith("{"), "Response should start with '{'")
        assertTrue(responseText.endsWith("}"), "Response should end with '}'")

        try {
            val jsonNode: JsonNode = ObjectMapper().readTree(responseText)

            println("=== Parsed JSON ===")
            println("Matched Keywords: ${jsonNode.get("matchedKeywords")}")
            println("Suggested Keywords: ${jsonNode.get("suggestedKeywords")}")

            assertTrue(jsonNode.has("matchedKeywords"), "JSON should have 'matchedKeywords' field")
            assertTrue(jsonNode.has("suggestedKeywords"), "JSON should have 'suggestedKeywords' field")

            val matchedKeywords = jsonNode.get("matchedKeywords")
            val suggestedKeywords = jsonNode.get("suggestedKeywords")

            assertTrue(matchedKeywords.isArray, "matchedKeywords should be an array")
            assertTrue(suggestedKeywords.isArray, "suggestedKeywords should be an array")

            println("=== ✅ JSON Parsing Success ===")
        } catch (e: Exception) {
            println("=== ❌ JSON Parsing Failed ===")
            println("Error: ${e.message}")
            println("Response analysis:")
            println("- Contains 'matchedKeywords': ${responseText.contains("matchedKeywords")}")
            println("- Contains 'suggestedKeywords': ${responseText.contains("suggestedKeywords")}")
            println("- Contains newlines: ${responseText.contains("\n")}")
            println("- Has extra text: ${!responseText.matches(Regex("^\\{.*\\}$"))}")

            throw AssertionError("Failed to parse response as JSON: ${e.message}")
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    fun `request actual newsletter`() {
        val inputKeywords = listOf("AI", "machine learning")
        val content =
            """
            앞으로 미국 국립공원을 방문하는 외국인 관광객은 미국인보다 더 많은 금액을 내게 됐어요. 도널드 트럼프 미국 대통령이 '국립공원 개선으로 미국을 다시 아름답게 만들기' 행정 명령에 3일(현지시간) 서명했기 때문이에요.

            해당 명령에는 자국민과 외국인에게 입장료를 차등 적용하는 내용이 담겼어요. 트럼프 대통령은 "국립공원 시스템 내 입장료 또는 레크리에이션 이용료가 부과되는 곳에서 비거주자(외국인)를 대상으로 요금을 적절하게 인상해야 한다"고 지시했어요. '아메리카 더 뷰티풀 패스' 등 외국인 전용 입장권의 가격 인상도 주문했다고 해요. 입장료 인상에 따른 수익은 국립공원, 국유림, 야생 동물 보호구역 등 시설 개선에 활용한다는 계획이래요. 자연을 보호한다는 취지는 좋지만, 외국인을 차별한다는 게 썩 유쾌하지만은 않네요.
            """.trimIndent()

        val response = geminiClient.requestKeywords(inputKeywords, GeminiModel.TWO_ZERO_FLASH_LITE, content)

        val responseText = response?.text()
        println(responseText)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """관세 전쟁이 세계 경제에 미치는 충격은 어느 수준일까요. 세계은행은 지난달 '세계경제전망(Global Economic Prospects)' 보고서를 통해 무역 긴장 고조와 정책적 불확실성으로 인해 올해 세계 경제 성장률은 (글로벌 금융 위기가 있던) 2008년 이후 가장 낮은 수준으로 떨어질 것으로 예상한다"며 "(세계은행은) 올해 전 세계 경제 주체의 70%에 대해 성장률 전망치를 하향 조정했다"고 밝혔습니다.""",
            """소프트웨어 제작이 피자 주문만큼이나 쉬워질 겁니다." 2016년 영국 런던에서 앱 개발 스타트업 '빌더 AI'가 첫걸음을 내디뎠을 당시 창업자 사친 데브 두갈이 내비쳤던 포부입니다. 그는 "인공지능(AI)의 힘으로 누구나 맞춤형 앱을 쉽게 개발할 수 있도록 하겠다"며 호언장담했고, 실제로 '나타샤(Natasha)'란 AI를 내세워 고객들이 제작 요청하는 앱을 뚝딱 만들어 냈습니다. 그러나 최근 블룸버그 등이 보도한 바에 따르면 빌더 AI가 핵심 기술이라 주장한 'AI 맞춤형 앱 제작'은 모두 허상이었습니다. 실제로는 인도인 개발자 700여 명이 현지에서 수작업으로 코딩을 하고, 그 결과물을 고객에게 전달하며 'AI가 만든 작품'이라 속였던 겁니다."""
        ]
    )
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    fun `request summary actual newsletter`(content: String) {
        val response = geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, content)

        val responseText = response?.text()
        println(responseText)
    }
}

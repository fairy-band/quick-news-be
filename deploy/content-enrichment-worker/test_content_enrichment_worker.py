import unittest

from content_enrichment_worker import extract_link_candidates, is_non_article_url, source_provider_name


class ContentEnrichmentWorkerTest(unittest.TestCase):
    def test_maeil_mail_extracts_only_question_links(self) -> None:
        source = {
            "sender": "maeil-mail",
            "senderEmail": "noreply@maeil-mail.kr",
            "subject": "[매일메일] 시스템 간 비동기 연동 방식에는 무엇이 있나요?",
            "htmlContent": """
                <html><body>
                  <a href="https://www.maeil-mail.kr/">매일메일</a>
                  <a href="https://www.maeil-mail.kr/question/137">답변 확인</a>
                  <a href="https://www.maeil-mail.kr/question/mine/newsletter.feeding@gmail.com">내 질문</a>
                  <a href="https://wiki.maeil-mail.kr/">NEW</a>
                  <a href="https://www.maeil-mail.kr/setting?email=newsletter.feeding@gmail.com&amp;token=token">설정</a>
                  <a href="https://www.maeil-mail.kr/unsubscribe?email=newsletter.feeding@gmail.com&amp;token=token">수신거부</a>
                  <a href="https://maeil-mail-resource.s3.ap-northeast-2.amazonaws.com/maeilmail-logo.png">로고</a>
                </body></html>
            """,
            "content": "https://www.maeil-mail.kr/unsubscribe?email=newsletter.feeding@gmail.com&token=token",
        }

        candidates = extract_link_candidates(source)

        self.assertEqual("Maeil Mail", source_provider_name(source))
        self.assertEqual(["https://www.maeil-mail.kr/question/137"], [candidate.url for candidate in candidates])
        self.assertEqual("시스템 간 비동기 연동 방식에는 무엇이 있나요?", candidates[0].title)

    def test_common_non_article_paths_are_rejected(self) -> None:
        self.assertTrue(is_non_article_url("https://example.com/unsubscribe?token=1"))
        self.assertTrue(is_non_article_url("https://example.com/setting?token=1"))
        self.assertTrue(is_non_article_url("https://example.com/preferences"))

    def test_default_provider_keeps_anchor_titles(self) -> None:
        source = {
            "sender": "weekly@example.com",
            "subject": "Weekly Digest",
            "htmlContent": """
                <html><body>
                  <a href="https://example.com/articles/runtime-patterns">Runtime patterns</a>
                </body></html>
            """,
            "content": "",
        }

        candidates = extract_link_candidates(source)

        self.assertEqual(["https://example.com/articles/runtime-patterns"], [candidate.url for candidate in candidates])
        self.assertEqual("Runtime patterns", candidates[0].title)


if __name__ == "__main__":
    unittest.main()

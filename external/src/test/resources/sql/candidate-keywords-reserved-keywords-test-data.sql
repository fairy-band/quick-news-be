-- 기존 데이터 정리
DELETE FROM reserved_keywords;
DELETE FROM candidate_keywords;

--- 키워드 후보 생성
INSERT INTO candidate_keywords (id, name, created_at, updated_at)
VALUES (1, 'Swift', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

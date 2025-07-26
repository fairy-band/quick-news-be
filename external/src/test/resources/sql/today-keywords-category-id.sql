INSERT INTO categories(id, name, created_at, updated_at)
VALUES (1, 'BE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO reserved_keywords(id, name, created_at, updated_at)
VALUES (1, 'Kotlin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO reserved_keywords(id, name, created_at, updated_at)
VALUES (2, 'Java', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO reserved_keywords(id, name, created_at, updated_at)
VALUES (3, 'Intellij', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO reserved_keywords(id, name, created_at, updated_at)
VALUES (4, '경험', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO reserved_keywords(id, name, created_at, updated_at)
VALUES (5, '자료구조', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO reserved_keywords(id, name, created_at, updated_at)
VALUES (6, '트러블슈팅', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO reserved_keywords(id, name, created_at, updated_at)
VALUES (7, '데이터베이스', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO category_keyword_mappings(category_id, keyword_id, weight, created_at, updated_at)
VALUES (1, 1, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO category_keyword_mappings(category_id, keyword_id, weight, created_at, updated_at)
VALUES (1, 2, 45, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO category_keyword_mappings(category_id, keyword_id, weight, created_at, updated_at)
VALUES (1, 3, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO category_keyword_mappings(category_id, keyword_id, weight, created_at, updated_at)
VALUES (1, 4, 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO category_keyword_mappings(category_id, keyword_id, weight, created_at, updated_at)
VALUES (1, 5, 70, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO category_keyword_mappings(category_id, keyword_id, weight, created_at, updated_at)
VALUES (1, 6, 80, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO category_keyword_mappings(category_id, keyword_id, weight, created_at, updated_at)
VALUES (1, 7, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

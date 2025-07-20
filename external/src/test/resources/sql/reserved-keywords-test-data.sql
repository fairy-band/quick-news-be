-- 기존 데이터 정리
DELETE FROM category_keyword_mappings;
DELETE FROM reserved_keywords;
DELETE FROM categories;

-- 카테고리 생성
INSERT INTO categories (id, name, created_at, updated_at) 
VALUES (1, 'Technology', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 키워드 생성
INSERT INTO reserved_keywords (id, name, created_at, updated_at) 
VALUES (1, 'AI', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO reserved_keywords (id, name, created_at, updated_at) 
VALUES (2, 'Machine Learning', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO reserved_keywords (id, name, created_at, updated_at) 
VALUES (3, 'Cloud Computing', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 카테고리-키워드 매핑 생성
INSERT INTO category_keyword_mappings (category_id, keyword_id, weight, created_at, updated_at) 
VALUES (1, 1, 0.8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO category_keyword_mappings (category_id, keyword_id, weight, created_at, updated_at) 
VALUES (1, 2, 0.7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO category_keyword_mappings (category_id, keyword_id, weight, created_at, updated_at) 
VALUES (1, 3, 0.6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP); 
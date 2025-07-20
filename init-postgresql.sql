-- PostgreSQL 초기화 스크립트
-- Newsletter 애플리케이션을 위한 데이터베이스 설정

-- 데이터베이스 생성 (이미 docker-compose에서 생성됨)
-- CREATE DATABASE newsletter;

-- 사용자 권한 설정
GRANT ALL PRIVILEGES ON DATABASE newsletter TO newsletter;

-- 기본 테이블 생성 (필요시)
-- 예시: 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- 시퀀스 생성 (필요시)
-- CREATE SEQUENCE IF NOT EXISTS user_id_seq;

-- 뷰 생성 (필요시)
-- CREATE OR REPLACE VIEW active_users AS
-- SELECT * FROM users WHERE created_at > CURRENT_DATE - INTERVAL '30 days';

-- 함수 생성 (필요시)
-- CREATE OR REPLACE FUNCTION update_updated_at_column()
-- RETURNS TRIGGER AS $$
-- BEGIN
--     NEW.updated_at = CURRENT_TIMESTAMP;
--     RETURN NEW;
-- END;
-- $$ language 'plpgsql';

-- 트리거 생성 (필요시)
-- CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
--     FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 권한 설정
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO newsletter;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO newsletter;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO newsletter;

-- 기본 데이터 삽입 (필요시)
-- INSERT INTO users (email, name) VALUES 
--     ('admin@newsletter.com', 'Admin User'),
--     ('test@newsletter.com', 'Test User')
-- ON CONFLICT (email) DO NOTHING; 
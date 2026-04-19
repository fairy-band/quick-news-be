-- Users table
CREATE TABLE IF NOT EXISTS users
(
    id           SERIAL PRIMARY KEY,
    device_token VARCHAR(255) NOT NULL UNIQUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Categories table
CREATE TABLE IF NOT EXISTS categories
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User-Category mappings table
CREATE TABLE IF NOT EXISTS user_category_mappings
(
    id          SERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    category_id BIGINT    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, category_id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- Reserved keywords table
CREATE TABLE IF NOT EXISTS reserved_keywords
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Candidate keywords table
CREATE TABLE IF NOT EXISTS candidate_keywords
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Contents table
CREATE TABLE IF NOT EXISTS contents
(
    id                   SERIAL PRIMARY KEY,
    newsletter_source_id VARCHAR(255),
    title                VARCHAR(255) NOT NULL,
    content              TEXT         NOT NULL,
    newsletter_name      VARCHAR(255) NOT NULL,
    original_url         VARCHAR(255) NOT NULL,
    image_url            VARCHAR(1024),
    published_at         DATE         NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    content_provider_id  BIGINT
);

CREATE TABLE IF NOT EXISTS content_generation_attempts
(
    id                BIGSERIAL PRIMARY KEY,
    content_id        BIGINT       NOT NULL,
    generation_mode   VARCHAR(20)  NOT NULL CHECK (generation_mode IN ('SINGLE', 'BATCH')),
    attempt_number    INTEGER      NOT NULL,
    model             VARCHAR(255) NOT NULL,
    prompt_version    VARCHAR(100) NOT NULL,
    generated_summary TEXT         NOT NULL,
    generated_headlines TEXT       NOT NULL,
    matched_keywords  TEXT         NOT NULL,
    quality_score     INTEGER      NOT NULL,
    quality_reason    TEXT         NOT NULL,
    ai_like_patterns  TEXT         NOT NULL,
    recommended_fix   TEXT         NOT NULL,
    passed            BOOLEAN      NOT NULL DEFAULT FALSE,
    accepted          BOOLEAN      NOT NULL DEFAULT FALSE,
    retry_count       INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_generation_attempt_content FOREIGN KEY (content_id) REFERENCES contents (id)
);

CREATE INDEX IF NOT EXISTS idx_content_generation_attempt_content_id
    ON content_generation_attempts (content_id);

-- Summaries table
CREATE TABLE IF NOT EXISTS summaries
(
    id                 SERIAL PRIMARY KEY,
    content_id         BIGINT       NOT NULL,
    title              VARCHAR(255) NOT NULL,
    summarized_content TEXT         NOT NULL,
    generation_attempt_id BIGINT,
    quality_score      INTEGER,
    quality_reason     TEXT,
    retry_count        INTEGER,
    summarized_at      TIMESTAMP    NOT NULL,
    model              VARCHAR(50)  NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (content_id) REFERENCES contents (id),
    CONSTRAINT fk_summary_generation_attempt FOREIGN KEY (generation_attempt_id) REFERENCES content_generation_attempts (id)
);

ALTER TABLE summaries
    ADD COLUMN IF NOT EXISTS generation_attempt_id BIGINT;

ALTER TABLE summaries
    ADD COLUMN IF NOT EXISTS quality_score INTEGER;

ALTER TABLE summaries
    ADD COLUMN IF NOT EXISTS quality_reason TEXT;

ALTER TABLE summaries
    ADD COLUMN IF NOT EXISTS retry_count INTEGER;

-- Category-Keyword mappings table
CREATE TABLE IF NOT EXISTS category_keyword_mappings
(
    id          SERIAL PRIMARY KEY,
    category_id BIGINT           NOT NULL,
    keyword_id  BIGINT           NOT NULL,
    weight      DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (category_id, keyword_id),
    FOREIGN KEY (category_id) REFERENCES categories (id),
    FOREIGN KEY (keyword_id) REFERENCES reserved_keywords (id)
);

-- Reserved-Candidate keyword mappings table
CREATE TABLE IF NOT EXISTS keyword_mappings
(
    reserved_keyword_id  BIGINT NOT NULL,
    candidate_keyword_id BIGINT NOT NULL,
    PRIMARY KEY (reserved_keyword_id, candidate_keyword_id),
    FOREIGN KEY (reserved_keyword_id) REFERENCES reserved_keywords (id),
    FOREIGN KEY (candidate_keyword_id) REFERENCES candidate_keywords (id)
);

-- Content-Keyword mappings table
CREATE TABLE IF NOT EXISTS content_keyword_mappings
(
    id         SERIAL PRIMARY KEY,
    content_id BIGINT    NOT NULL,
    keyword_id BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (content_id, keyword_id)
);

CREATE INDEX IF NOT EXISTS content_keyword_mapping_keyword_id
    ON content_keyword_mappings (keyword_id);

-- Newsletter sources table
CREATE TABLE IF NOT EXISTS newsletter_sources
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exposure_contents
(
    id                   BIGSERIAL PRIMARY KEY,
    content_id           BIGINT       NOT NULL,
    provocative_keyword  VARCHAR(255) NOT NULL,
    provocative_headline VARCHAR(255) NOT NULL,
    summary_content      TEXT         NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_exposure_content_content FOREIGN KEY (content_id) REFERENCES contents (id)
);

CREATE INDEX IF NOT EXISTS idx_exposure_content_content_id ON exposure_contents (content_id);

CREATE TABLE IF NOT EXISTS user_exposed_contents_mapping
(
    user_id    bigint  not null,
    content_id integer not null,
    created_at timestamp default CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS user_exposed_contents_user_id_content_id_index
    ON user_exposed_contents_mapping (user_id, content_id);

-- FCM tokens table for push notifications
CREATE TABLE IF NOT EXISTS fcm_tokens
(
    id           SERIAL PRIMARY KEY,
    device_token VARCHAR(255) NOT NULL UNIQUE,
    fcm_token    VARCHAR(255) NOT NULL UNIQUE,
    device_type  VARCHAR(20)  NOT NULL CHECK (device_type IN ('ANDROID', 'IOS')),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_token ON fcm_tokens (device_token);
CREATE INDEX IF NOT EXISTS idx_fcm_token ON fcm_tokens (fcm_token);

-- Content providers table
CREATE TABLE IF NOT EXISTS content_provider
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    channel    VARCHAR(255) NOT NULL,
    language   VARCHAR(10)  NOT NULL,
    type       VARCHAR(20)  NOT NULL CHECK (type IN ('BLOG', 'NEWSLETTER')),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Content provider category mappings table
CREATE TABLE IF NOT EXISTS content_provider_category_mappings
(
    id                  SERIAL PRIMARY KEY,
    content_provider_id BIGINT           NOT NULL,
    category_id         BIGINT           NOT NULL,
    weight              DOUBLE PRECISION NOT NULL DEFAULT 100.0,
    created_at          TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (content_provider_id, category_id)
);


-- Gemini rate limit table
CREATE TABLE IF NOT EXISTS gemini_rate_limit
(
    id                    SERIAL PRIMARY KEY,
    model_name            VARCHAR(100) NOT NULL,
    limit_date            DATE         NOT NULL,
    request_count         INTEGER      NOT NULL DEFAULT 0,
    max_requests_per_day  INTEGER      NOT NULL DEFAULT 20,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_model_date ON gemini_rate_limit (model_name, limit_date);

CREATE TABLE IF NOT EXISTS content_provider_requests
(
    id                    SERIAL PRIMARY KEY,
    content_provider_name VARCHAR(255) NOT NULL,
    channel               VARCHAR(255) NOT NULL,
    request_category      VARCHAR(20)  NOT NULL,
    related_to            VARCHAR(255) NOT NULL,
    reason                TEXT,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS popular_newsletter_snapshots
(
    id                 BIGSERIAL PRIMARY KEY,
    segment_type       VARCHAR(20) NOT NULL CHECK (segment_type IN ('GLOBAL', 'CATEGORY', 'JOB_GROUP')),
    segment_key        VARCHAR(255),
    window_start_date  DATE        NOT NULL,
    window_end_date    DATE        NOT NULL,
    generated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_event_name  VARCHAR(255) NOT NULL,
    candidate_limit    INTEGER     NOT NULL,
    resolved_item_count INTEGER    NOT NULL DEFAULT 0,
    status             VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_popular_newsletter_snapshot_segment_generated_at
    ON popular_newsletter_snapshots (segment_type, segment_key, generated_at DESC);

CREATE TABLE IF NOT EXISTS popular_newsletter_snapshot_items
(
    id                          BIGSERIAL PRIMARY KEY,
    snapshot_id                 BIGINT      NOT NULL,
    rank_order                  INTEGER     NOT NULL,
    raw_object_id               VARCHAR(1024) NOT NULL,
    click_count                 BIGINT      NOT NULL,
    resolved_content_id         BIGINT,
    resolved_exposure_content_id BIGINT,
    resolution_status           VARCHAR(20) NOT NULL CHECK (resolution_status IN ('RESOLVED', 'UNRESOLVED')),
    created_at                  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_popular_newsletter_snapshot_items_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES popular_newsletter_snapshots (id),
    CONSTRAINT fk_popular_newsletter_snapshot_items_content
        FOREIGN KEY (resolved_content_id) REFERENCES contents (id),
    CONSTRAINT fk_popular_newsletter_snapshot_items_exposure_content
        FOREIGN KEY (resolved_exposure_content_id) REFERENCES exposure_contents (id),
    CONSTRAINT uk_popular_newsletter_snapshot_item_snapshot_rank UNIQUE (snapshot_id, rank_order)
);

ALTER TABLE popular_newsletter_snapshot_items
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE popular_newsletter_snapshot_items
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_popular_newsletter_snapshot_items_snapshot_id
    ON popular_newsletter_snapshot_items (snapshot_id);

COMMENT ON TABLE popular_newsletter_snapshots IS '인기 뉴스레터 랭킹 스냅샷 메타데이터';

COMMENT ON TABLE popular_newsletter_snapshot_items IS '인기 뉴스레터 스냅샷 개별 랭킹 아이템';

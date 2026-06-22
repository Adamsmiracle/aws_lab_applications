-- Run on startup by Spring (spring.sql.init.mode=always). Idempotent.
CREATE TABLE IF NOT EXISTS photos (
    id          BIGSERIAL PRIMARY KEY,
    s3_key      TEXT        NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;


CREATE TABLE users (
                       id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       registration_number  BIGINT NOT NULL UNIQUE,
                       first_name           VARCHAR(255) NOT NULL,
                       last_name            VARCHAR(255),
                       uni_email            VARCHAR(255) NOT NULL UNIQUE,
                       personal_email       VARCHAR(255),
                       phone_number         VARCHAR(255),
                       hashed_password      VARCHAR(255),
                       department           VARCHAR(255),
                       role                 VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
                       verified             BOOLEAN NOT NULL DEFAULT FALSE
);


CREATE TABLE items (
                       id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       type              VARCHAR(10) NOT NULL,
                       title             VARCHAR(150) NOT NULL,
                       description       VARCHAR(1000) NOT NULL,
                       status            VARCHAR(15) NOT NULL DEFAULT 'OPEN',
                       event_from        TIMESTAMPTZ,
                       event_to          TIMESTAMPTZ,
                       location          VARCHAR(255) NOT NULL,
                       image_url         VARCHAR(255),
                       image_public_id   VARCHAR(255),
                       reported_by       UUID NOT NULL REFERENCES users(id),
                       created_at        TIMESTAMPTZ NOT NULL,
                       updated_at        TIMESTAMPTZ,
                       text_embedding    vector(384),
                       image_embedding   vector(512)
);

CREATE INDEX idx_items_type_status ON items (type, status);
CREATE INDEX idx_items_reported_by ON items (reported_by);

CREATE TABLE refresh_tokens (
                                id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                jti                  VARCHAR(36) NOT NULL UNIQUE,
                                user_id              UUID NOT NULL REFERENCES users(id),
                                device_fingerprint   VARCHAR(128),
                                ip_address           VARCHAR(45),
                                user_agent           VARCHAR(500),
                                created_at           TIMESTAMPTZ NOT NULL,
                                expires_at           TIMESTAMPTZ NOT NULL,
                                last_used_at         TIMESTAMPTZ,
                                revoked              BOOLEAN NOT NULL DEFAULT FALSE,
                                revoked_at           TIMESTAMPTZ,
                                replaced_by_jti      VARCHAR(36),
                                version              BIGINT
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
CREATE INDEX idx_refresh_tokens_user_revoked ON refresh_tokens (user_id, revoked);


CREATE TABLE matches (
                         id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         lost_item_id      UUID NOT NULL REFERENCES items(id),
                         found_item_id     UUID NOT NULL REFERENCES items(id),
                         similarity_score  DOUBLE PRECISION NOT NULL,
                         status            VARCHAR(15) NOT NULL DEFAULT 'PENDING',
                         created_at        TIMESTAMPTZ NOT NULL,
                         CONSTRAINT uq_matches_pair UNIQUE (lost_item_id, found_item_id)
);

CREATE INDEX idx_matches_lost_item ON matches (lost_item_id);
CREATE INDEX idx_matches_found_item ON matches (found_item_id);

ALTER TABLE matches DROP COLUMN IF EXISTS similarity_score;

CREATE INDEX IF NOT EXISTS idx_items_text_embedding_hnsw
    ON items USING hnsw (text_embedding vector_cosine_ops);
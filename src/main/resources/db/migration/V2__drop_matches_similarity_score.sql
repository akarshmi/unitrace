-- similarity_score was superseded by text_score / image_score / time_score / final_score
-- in the Match entity. Drop it so inserts that only set the new columns succeed.
ALTER TABLE matches DROP COLUMN IF EXISTS similarity_score;

CREATE INDEX IF NOT EXISTS idx_items_text_embedding_hnsw
    ON items USING hnsw (text_embedding vector_cosine_ops);
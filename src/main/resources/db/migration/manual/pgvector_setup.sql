-- UniTrace pgvector / embedding-queue setup. Run ONCE on Neon (SQL editor or psql) AFTER the app has started
-- with ddl-auto so Hibernate has created/updated `items` and `matches`.
--
-- Why this is NOT a Flyway migration: the project has the Flyway starter, but Flyway runs BEFORE Hibernate's
-- ddl-auto, so a V2 that indexes columns Hibernate has not created yet would fail the application start.
-- Never edit an already-applied V1__initial_schema.sql.

CREATE EXTENSION IF NOT EXISTS vector;

-- Text ANN index (cosine). The search ORDER BY uses the same operator (<=>), so the index is usable.
CREATE INDEX IF NOT EXISTS idx_items_text_embedding_hnsw
    ON items USING hnsw (text_embedding vector_cosine_ops);

-- Embedding work queue (idx_items_type_status / idx_items_reported_by are declared by the Item entity).
CREATE INDEX IF NOT EXISTS idx_items_embedding_queue
    ON items (embedding_next_attempt_at)
    WHERE embedding_status IS NULL OR embedding_status IN ('PENDING', 'RETRY', 'PROCESSING');

-- One-off backfill for rows created before embedding_status existed.
UPDATE items SET embedding_status = 'READY'   WHERE embedding_status IS NULL AND text_embedding IS NOT NULL;
UPDATE items SET embedding_status = 'PENDING' WHERE embedding_status IS NULL AND text_embedding IS NULL;

-- If an older experimental match table exists (e.g. named "match"), it is unused now:
--   SELECT table_name FROM information_schema.tables WHERE table_name IN ('match', 'matches');
--   DROP TABLE IF EXISTS match;   -- only after checking it holds nothing you need
-- Verify the entity-declared indexes exist:
--   SELECT indexname FROM pg_indexes WHERE tablename IN ('items', 'matches');

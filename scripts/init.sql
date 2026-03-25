-- Full-text search setup for documents table
-- This runs once on first container startup via docker-entrypoint-initdb.d

-- tsvector column (JPA creates the table via ddl-auto=update first,
-- this script adds the column and trigger safely)
ALTER TABLE documents ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Trigger function: title weighted 'A', content weighted 'B'
CREATE OR REPLACE FUNCTION documents_search_vector_update()
RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.content, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger
CREATE OR REPLACE TRIGGER documents_search_vector_trigger
BEFORE INSERT OR UPDATE ON documents
FOR EACH ROW EXECUTE FUNCTION documents_search_vector_update();

-- GIN index for fast tsvector lookups
CREATE INDEX IF NOT EXISTS idx_documents_search_vector
ON documents USING GIN(search_vector);
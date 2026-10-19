-- Preserve existing migration SQL checksums while mapping the historical CLOB type to PostgreSQL text.
DO $$ BEGIN
  CREATE DOMAIN clob AS text;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

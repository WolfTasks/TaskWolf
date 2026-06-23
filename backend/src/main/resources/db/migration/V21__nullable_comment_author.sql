-- Allow system-generated comments (e.g. postmortems) with no author
ALTER TABLE comments ALTER COLUMN author_id DROP NOT NULL;

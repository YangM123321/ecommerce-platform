-- Session 5: each product can have one image. The file lives in Blob Storage;
-- the database only keeps its public URL.
ALTER TABLE catalog.products ADD COLUMN image_url VARCHAR(500);

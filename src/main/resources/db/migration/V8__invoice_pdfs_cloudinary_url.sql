-- Invoice PDFs are now uploaded to Cloudinary and referenced by URL instead
-- of being stored as bytea in Postgres — this also sidesteps the Hibernate 6
-- @Lob-vs-bytea mapping mismatch that made every prior insert into this
-- table fail, so no row here has ever successfully persisted pdf_bytes.
ALTER TABLE invoice_pdfs
    DROP COLUMN pdf_bytes;

ALTER TABLE invoice_pdfs
    ADD COLUMN pdf_url TEXT NOT NULL;

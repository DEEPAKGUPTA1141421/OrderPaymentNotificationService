-- Snapshot product image URL at checkout so sellers can see what to pack
-- without a round-trip to ProductClientService at query time.
ALTER TABLE booking_items
    ADD COLUMN IF NOT EXISTS product_image_url TEXT;

-- Phase 0: indexes to support seller-scoped order queries efficiently
-- idx_bookings_shop_status_created: covers the primary seller order list query
--   SELECT * FROM bookings WHERE shop_id = ? AND status = ? ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_bookings_shop_status_created
    ON bookings (shop_id, status, created_at DESC);

-- idx_bookings_shop_created: covers the unfiltered seller order list (all statuses)
CREATE INDEX IF NOT EXISTS idx_bookings_shop_created
    ON bookings (shop_id, created_at DESC);

-- idx_booking_items_product_id: covers product-level order analytics
CREATE INDEX IF NOT EXISTS idx_booking_items_product_id
    ON booking_items (product_id);

-- idx_booking_items_variant_id: covers variant-level sales aggregation
CREATE INDEX IF NOT EXISTS idx_booking_items_variant_id
    ON booking_items (variant_id);

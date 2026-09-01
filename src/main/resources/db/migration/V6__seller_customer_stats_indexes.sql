-- Phase 1: seller dashboard — customer stats
-- idx_bookings_shop_user_created: covers distinct-customer / new-vs-returning
-- aggregations, e.g.
--   SELECT COUNT(DISTINCT user_id) FROM bookings WHERE shop_id = ? AND created_at >= ?
--   NOT EXISTS (SELECT 1 FROM bookings WHERE shop_id = ? AND user_id = ? AND created_at < ?)
CREATE INDEX IF NOT EXISTS idx_bookings_shop_user_created
    ON bookings (shop_id, user_id, created_at);

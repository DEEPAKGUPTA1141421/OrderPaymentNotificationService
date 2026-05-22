-- Drop the old auto-generated check constraint (created by @Enumerated before the AttributeConverter refactor)
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;

-- Coerce any rows whose status value is no longer valid into CANCELLED so the new constraint can be applied cleanly.
UPDATE bookings
SET status = 'CANCELLED'
WHERE status NOT IN (
    'INITIATED',
    'CONFIRMED',
    'PROCESSING',
    'OUT_FOR_DELIVERY',
    'DELIVERED',
    'CANCELLED',
    'FAILED',
    'REVERSED',
    'REVERSE_FAILED'
);

-- Recreate the constraint covering all current Status enum values.
ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check
    CHECK (status IN (
        'INITIATED',
        'CONFIRMED',
        'PROCESSING',
        'OUT_FOR_DELIVERY',
        'DELIVERED',
        'CANCELLED',
        'FAILED',
        'REVERSED',
        'REVERSE_FAILED'
    ));

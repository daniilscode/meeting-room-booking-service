--liquibase formatted sql

--changeset meet-reserve:002-booking-overlap-constraint
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
    ADD CONSTRAINT chk_booking_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED'));

ALTER TABLE bookings
    ADD CONSTRAINT no_overlapping_active_bookings
        EXCLUDE USING gist (
            room_id WITH =,
            tstzrange(start_time, end_time) WITH &&
        )
        WHERE (status IN ('PENDING', 'CONFIRMED'));

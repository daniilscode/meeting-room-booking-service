-- таблица переговорных
CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    capacity INT NOT NULL CHECK (capacity > 0),
    has_projector BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- таблица бронирований
CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_booking_time_valid CHECK (end_time > start_time)
);

-- индекс для быстрого поиска наложений и очистки шедулером
CREATE INDEX idx_bookings_lookup
ON bookings (room_id, status, start_time, end_time);

CREATE INDEX idx_bookings_expired_cleanup
ON bookings (status, expires_at)
WHERE status = 'PENDING';

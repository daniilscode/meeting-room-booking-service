package org.corp.meet_reserve.service.impl;

import org.corp.meet_reserve.entity.Booking;
import org.corp.meet_reserve.entity.Room;
import org.corp.meet_reserve.exception.RoomAlreadyBookedException;
import org.corp.meet_reserve.exception.RoomNotFoundException;
import org.corp.meet_reserve.repository.BookingRepository;
import org.corp.meet_reserve.repository.RoomRepository;
import org.corp.meet_reserve.service.BookingService;

import lombok.RequiredArgsConstructor;
import org.corp.meet_reserve.api.model.BookingResponse;
import org.corp.meet_reserve.api.model.CreateBookingRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.time.ZoneOffset;
import java.util.EnumSet;

import static java.time.Duration.between;
import static org.corp.meet_reserve.entity.BookingStatus.CONFIRMED;
import static org.corp.meet_reserve.entity.BookingStatus.PENDING;
import static org.corp.meet_reserve.util.MessagesUtil.INVALID_DURATION;
import static org.corp.meet_reserve.util.MessagesUtil.INVALID_END_TIME;
import static org.corp.meet_reserve.util.MessagesUtil.ROOM_INACTIVE;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final StringRedisTemplate redisTemplate;

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "end return 0", Long.class);

    @Transactional
    public BookingResponse createPendingBooking(CreateBookingRequest request) {
        LocalDateTime start = request.getStartTime().toLocalDateTime();
        LocalDateTime end = request.getEndTime().toLocalDateTime();

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(INVALID_END_TIME);
        }

        if (between(start, end).toMinutes() < 15 || between(start, end).toHours() > 4) {
            throw new IllegalArgumentException(INVALID_DURATION);
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException(request.getRoomId()));

        if (!room.isActive()) {
            throw new IllegalStateException(ROOM_INACTIVE);
        }

        String lockKey = "booking:room:" + room.getId();
        String lockToken = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, LOCK_TTL))) {
            throw new RoomAlreadyBookedException();
        }

        try {
            if (!bookingRepository.findByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(room.getId(), EnumSet.of(PENDING, CONFIRMED), end, start).isEmpty()) {
                throw new RoomAlreadyBookedException();
            }

            Booking saved = bookingRepository.save(getBooking(request, room, start, end));
            return new BookingResponse(
                saved.getId(),
                saved.getRoom().getId(),
                saved.getUserId(),
                saved.getTitle(),
                saved.getStartTime().atOffset(ZoneOffset.UTC),
                saved.getEndTime().atOffset(ZoneOffset.UTC),
                BookingResponse.StatusEnum.PENDING,
                saved.getExpiresAt().atOffset(ZoneOffset.UTC),
                    saved.getCreatedAt() == null ? null : saved.getCreatedAt().atOffset(ZoneOffset.UTC));
        } finally {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockToken);
        }
    }

    private static Booking getBooking(CreateBookingRequest request, Room room, LocalDateTime start, LocalDateTime end) {
        Booking booking = new Booking();
        booking.setRoom(room);
        booking.setUserId(request.getUserId());
        booking.setTitle(request.getTitle());
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setStatus(PENDING);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return booking;
    }

}

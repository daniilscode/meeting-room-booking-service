package org.corp.meet_reserve.service.impl;

import lombok.RequiredArgsConstructor;
import org.corp.meet_reserve.api.model.BookingResponse;
import org.corp.meet_reserve.api.model.CreateBookingRequest;
import org.corp.meet_reserve.entity.Booking;
import org.corp.meet_reserve.entity.BookingStatus;
import org.corp.meet_reserve.entity.Room;
import org.corp.meet_reserve.exception.RoomAlreadyBookedException;
import org.corp.meet_reserve.exception.RoomInactiveException;
import org.corp.meet_reserve.exception.RoomNotFoundException;
import org.corp.meet_reserve.repository.BookingRepository;
import org.corp.meet_reserve.repository.RoomRepository;
import org.corp.meet_reserve.service.BookingService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static java.time.Duration.ofMinutes;
import static org.corp.meet_reserve.entity.BookingStatus.CONFIRMED;
import static org.corp.meet_reserve.entity.BookingStatus.EXPIRED;
import static org.corp.meet_reserve.entity.BookingStatus.PENDING;
import static org.corp.meet_reserve.util.MessagesUtil.INVALID_DURATION;
import static org.corp.meet_reserve.util.MessagesUtil.INVALID_END_TIME;
import static org.corp.meet_reserve.util.MessagesUtil.INVALID_START_TIME;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);
    private static final Duration MIN_BOOKING_DURATION = ofMinutes(15);
    private static final Duration MAX_BOOKING_DURATION = Duration.ofHours(4);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "end return 0", Long.class);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Override
    @Transactional
    public BookingResponse createPendingBooking(CreateBookingRequest request) {
        validateRequest(request);

        String lockKey = "booking:room:" + request.getRoomId();
        String lockToken = UUID.randomUUID().toString();
        acquireLock(lockKey, lockToken);

        boolean unlockAfterTransaction = false;
        try {
            Instant start = request.getStartTime().toInstant();
            Instant end = request.getEndTime().toInstant();

            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new RoomNotFoundException(request.getRoomId()));

            if (!room.isActive()) {
                throw new RoomInactiveException();
            }

            Instant now = clock.instant();
            bookingRepository.expireOverdueBookings(now, PENDING, EXPIRED);

            boolean overlapExists = !bookingRepository
                    .findByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                            room.getId(), EnumSet.of(PENDING, CONFIRMED), end, start)
                    .isEmpty();
            if (overlapExists) {
                throw new RoomAlreadyBookedException();
            }

            Booking booking = new Booking();
            booking.setRoom(room);
            booking.setUserId(request.getUserId());
            booking.setTitle(request.getTitle().trim());
            booking.setStartTime(start);
            booking.setEndTime(end);
            booking.setStatus(PENDING);
            booking.setCreatedAt(now);
            booking.setExpiresAt(now.plus(ofMinutes(10)));

            Booking saved = bookingRepository.saveAndFlush(booking);
            BookingResponse response = toResponse(saved);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        unlock(lockKey, lockToken);
                    }
                });
                unlockAfterTransaction = true;
            }

            return response;
        } catch (DataIntegrityViolationException exception) {
            throw new RoomAlreadyBookedException();
        } finally {
            if (!unlockAfterTransaction) {
                unlock(lockKey, lockToken);
            }
        }
    }

    private void validateRequest(CreateBookingRequest request) {
        if (request == null || request.getRoomId() == null || request.getRoomId() <= 0
                || request.getUserId() == null || request.getUserId() <= 0
                || request.getTitle() == null || request.getTitle().isBlank()
                || request.getTitle().length() > 255
                || request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("Некорректные данные запроса");
        }

        Instant start = request.getStartTime().toInstant();
        Instant end = request.getEndTime().toInstant();
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(INVALID_END_TIME);
        }
        if (!start.isAfter(clock.instant())) {
            throw new IllegalArgumentException(INVALID_START_TIME);
        }

        Duration duration = Duration.between(start, end);
        if (duration.compareTo(MIN_BOOKING_DURATION) < 0
                || duration.compareTo(MAX_BOOKING_DURATION) > 0) {
            throw new IllegalArgumentException(INVALID_DURATION);
        }
    }

    private void acquireLock(String lockKey, String lockToken) {
        Instant deadline = clock.instant().plus(LOCK_WAIT);
        while (!Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, LOCK_TTL))) {
            if (!clock.instant().isBefore(deadline)) {
                throw new RoomAlreadyBookedException();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RoomAlreadyBookedException();
            }
        }
    }

    private void unlock(String lockKey, String lockToken) {
        redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockToken);
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getUserId(),
                booking.getTitle(),
                toOffsetDateTime(booking.getStartTime()),
                toOffsetDateTime(booking.getEndTime()),
                BookingResponse.StatusEnum.valueOf(booking.getStatus().name()),
                toOffsetDateTime(booking.getExpiresAt()),
                toOffsetDateTime(booking.getCreatedAt()));
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}

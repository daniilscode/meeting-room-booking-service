package org.corp.meet_reserve.service.impl;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyList;
import org.corp.meet_reserve.entity.Booking;
import org.corp.meet_reserve.entity.Room;
import org.corp.meet_reserve.exception.RoomAlreadyBookedException;
import org.corp.meet_reserve.exception.RoomNotFoundException;
import org.corp.meet_reserve.repository.BookingRepository;
import org.corp.meet_reserve.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.corp.meet_reserve.api.model.CreateBookingRequest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;



@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {
    @Mock BookingRepository bookingRepository;
    @Mock RoomRepository roomRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @InjectMocks BookingServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUpRedis() {
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.lenient().when(valueOperations.setIfAbsent(any(), any(), any(java.time.Duration.class))).thenReturn(true);
    }

    private final OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).withSecond(0).withNano(0);
    private final OffsetDateTime end = start.plusHours(1);

    @Test
    void createsPendingBookingForAvailableRoom() {
        Room room = room(1L, true);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID());
            booking.setCreatedAt(LocalDateTime.now());
            return booking;
        });

        var response = service.createPendingBooking(request());

        assertThat(response.getStatus()).isEqualTo(org.corp.meet_reserve.api.model.BookingResponse.StatusEnum.PENDING);
        assertThat(response.getRoomId()).isEqualTo(1L);
        assertThat(response.getExpiresAt()).isNotNull();
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void allowsOnlyOneBookingWhenTwentyThreadsCompeteForSameSlot() throws Exception {
        AtomicBoolean lockTaken = new AtomicBoolean();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        int threadCount = 20;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, true)));
        when(valueOperations.setIfAbsent(any(), any(), any(java.time.Duration.class)))
                .thenAnswer(invocation -> lockTaken.compareAndSet(false, true));
        doAnswer(invocation -> { lockTaken.set(false); return 1L; })
                .when(redisTemplate).execute(any(), anyList(), any());
        AtomicBoolean slotClaimed = new AtomicBoolean();
        when(bookingRepository.findByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(anyLong(), any(), any(), any()))
                .thenAnswer(invocation -> slotClaimed.compareAndSet(false, true) ? List.of() : List.of(new Booking()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID());
            booking.setCreatedAt(LocalDateTime.now());
            return booking;
        });

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    startSignal.await();
                    service.createPendingBooking(request());
                    successCount.incrementAndGet();
                } catch (RoomAlreadyBookedException exception) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        startSignal.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount).hasValue(1);
        assertThat(conflictCount).hasValue(19);
    }
    @Test
    void rejectsMissingRoom() {
        when(roomRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPendingBooking(request()))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void rejectsInactiveRoom() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, false)));

        assertThatThrownBy(() -> service.createPendingBooking(request()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void rejectsOverlappingBooking() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, true)));
        when(bookingRepository.findByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(anyLong(), any(), any(), any()))
                .thenReturn(List.of(new Booking()));

        assertThatThrownBy(() -> service.createPendingBooking(request()))
                .isInstanceOf(RoomAlreadyBookedException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void allowsAdjacentBooking() {
        Room room = room(1L, true);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.createPendingBooking(request())).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidTimeRange() {
        CreateBookingRequest invalid = new CreateBookingRequest(1L, 42L, "Test", end, start);

        assertThatThrownBy(() -> service.createPendingBooking(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roomRepository, bookingRepository);
    }

    @Test
    void rejectsTooShortBooking() {
        CreateBookingRequest invalid = new CreateBookingRequest(1L, 42L, "Test", start, start.plusMinutes(10));

        assertThatThrownBy(() -> service.createPendingBooking(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roomRepository, bookingRepository);
    }

    private CreateBookingRequest request() {
        return new CreateBookingRequest(1L, 42L, "Test meeting", start, end);
    }

    private Room room(Long id, boolean active) {
        Room room = new Room();
        room.setId(id);
        room.setActive(active);
        return room;
    }
}

package org.corp.meet_reserve.repository;

import org.corp.meet_reserve.entity.Booking;
import org.corp.meet_reserve.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            SELECT b FROM Booking b
            WHERE b.room.id = :roomId
              AND b.status IN :statuses
              AND b.startTime < :endTime
              AND b.endTime > :startTime
            """)
    List<Booking> findByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            @Param("roomId") Long roomId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("endTime") LocalDateTime endTime,
            @Param("startTime") LocalDateTime startTime);

    @Modifying
    @Query("""
            UPDATE Booking b
            SET b.status = 'EXPIRED'
            WHERE b.status = 'PENDING'
              AND b.expiresAt <= :now
            """)
    int expireOverdueBookings(@Param("now") LocalDateTime now);
}

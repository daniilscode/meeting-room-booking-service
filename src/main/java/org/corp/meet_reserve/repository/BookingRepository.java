package org.corp.meet_reserve.repository;

import org.corp.meet_reserve.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Modifying
    @Query("""
            UPDATE Booking b
            SET b.status = 'EXPIRED'
            WHERE b.status = 'PENDING'
              AND b.expiresAt <= :now
            """)
    int expireOverdueBookings(@Param("now") LocalDateTime now);
}

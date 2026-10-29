package org.corp.meet_reserve.controller;
import org.corp.meet_reserve.api.BookingsApi;
import org.corp.meet_reserve.api.model.BookingResponse;
import org.corp.meet_reserve.api.model.CreateBookingRequest;

import lombok.RequiredArgsConstructor;
import org.corp.meet_reserve.api.BookingsApi;
import org.corp.meet_reserve.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookingController implements BookingsApi {

    private final BookingService bookingService;

    @Override
    public ResponseEntity<BookingResponse> createPendingBooking(CreateBookingRequest request) {
        return ResponseEntity.status(201).body(bookingService.createPendingBooking(request));
    }
}

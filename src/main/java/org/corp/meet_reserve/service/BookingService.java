package org.corp.meet_reserve.service;

import org.corp.meet_reserve.api.model.BookingResponse;
import org.corp.meet_reserve.api.model.CreateBookingRequest;

public interface BookingService {
    BookingResponse createPendingBooking(CreateBookingRequest request);
}

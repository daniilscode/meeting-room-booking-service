package org.corp.meet_reserve.exception;

import static org.corp.meet_reserve.util.MessagesUtil.SLOT_ALREADY_BOOKED;

public class RoomAlreadyBookedException extends RuntimeException {
    public RoomAlreadyBookedException(){
        super(SLOT_ALREADY_BOOKED);
    }
}

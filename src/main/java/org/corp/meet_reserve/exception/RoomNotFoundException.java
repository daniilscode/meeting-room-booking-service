package org.corp.meet_reserve.exception;

import static org.corp.meet_reserve.util.MessagesUtil.ROOM_NOT_FOUND;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(Long id){
        super(String.format(ROOM_NOT_FOUND + id));
    }
}

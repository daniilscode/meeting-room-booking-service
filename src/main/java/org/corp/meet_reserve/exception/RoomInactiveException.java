package org.corp.meet_reserve.exception;

public class RoomInactiveException extends RuntimeException {
    public RoomInactiveException() {
        super("Комната неактивна");
    }
}

package org.corp.meet_reserve.util;

public interface MessagesUtil {
    String INVALID_END_TIME = "endTime должен быть позже startTime";
    String INVALID_START_TIME = "startTime должен быть в будущем";
    String INVALID_DURATION = "Длительность брони должна быть от 15 минут до 4 часов";
    String ROOM_INACTIVE = "Комната неактивна";
    String ROOM_NOT_FOUND = "Комната не найдена: ";
    String SLOT_ALREADY_BOOKED = "Выбранный интервал уже занят";
}

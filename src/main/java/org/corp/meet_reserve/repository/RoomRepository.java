package org.corp.meet_reserve.repository;

import org.corp.meet_reserve.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
}

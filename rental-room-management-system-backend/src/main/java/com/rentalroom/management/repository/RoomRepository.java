package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    boolean existsByBranchIdAndRoomCode(Long branchId, String roomCode);

    boolean existsByBranchIdAndRoomCodeAndIdNot(Long branchId, String roomCode, Long id);

    List<Room> findByBranchId(Long branchId);

    boolean existsByRoomTypeId(Long roomTypeId);
}

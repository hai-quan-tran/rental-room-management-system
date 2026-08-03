package com.rentalroom.management.repository;

import com.rentalroom.management.entity.RoomTypeHandoverItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomTypeHandoverItemRepository extends JpaRepository<RoomTypeHandoverItem, Long> {

    List<RoomTypeHandoverItem> findByRoomTypeIdOrderByIdAsc(Long roomTypeId);

    void deleteByRoomTypeId(Long roomTypeId);
}

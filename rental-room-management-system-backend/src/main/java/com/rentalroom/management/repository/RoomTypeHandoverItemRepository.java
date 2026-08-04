package com.rentalroom.management.repository;

import com.rentalroom.management.entity.RoomTypeHandoverItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomTypeHandoverItemRepository extends JpaRepository<RoomTypeHandoverItem, Long> {

    List<RoomTypeHandoverItem> findByRoomTypeIdOrderByIdAsc(Long roomTypeId);

    List<RoomTypeHandoverItem> findByRoomType_BranchId(Long branchId);

    void deleteByRoomTypeId(Long roomTypeId);

    boolean existsByItemId(Long itemId);
}

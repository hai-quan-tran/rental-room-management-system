package com.rentalroom.management.service;

import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Item;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.RoomType;
import com.rentalroom.management.entity.RoomTypeHandoverItem;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ItemRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.RoomTypeHandoverItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Reproduces the worked example from the spec: branch has 5 Tivi + 5 Tủ lạnh, room type A and B
 * each need 1 of each, 3 rooms of type A + 2 of type B already exist (exactly at capacity).
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomTypeHandoverItemRepository handoverItemRepository;

    private ItemService itemService;

    private RoomType typeA;
    private RoomType typeB;

    @BeforeEach
    void setUp() {
        itemService = new ItemService(itemRepository, branchRepository, roomRepository, handoverItemRepository);

        Branch branch = new Branch();
        branch.setId(1L);

        typeA = new RoomType();
        typeA.setId(10L);
        typeA.setBranch(branch);

        typeB = new RoomType();
        typeB.setId(20L);
        typeB.setBranch(branch);

        Item tivi = new Item();
        tivi.setId(100L);
        tivi.setBranch(branch);
        tivi.setName("Tivi");
        tivi.setQuantityAvailable(5);

        Item tuLanh = new Item();
        tuLanh.setId(200L);
        tuLanh.setBranch(branch);
        tuLanh.setName("Tủ lạnh");
        tuLanh.setQuantityAvailable(5);

        List<RoomTypeHandoverItem> handoverItems = List.of(
                handoverItem(typeA, tivi, 1),
                handoverItem(typeA, tuLanh, 1),
                handoverItem(typeB, tivi, 1),
                handoverItem(typeB, tuLanh, 1)
        );
        when(handoverItemRepository.findByRoomType_BranchId(1L)).thenReturn(handoverItems);

        List<Room> existingRooms = List.of(
                roomOfType(typeA), roomOfType(typeA), roomOfType(typeA),
                roomOfType(typeB), roomOfType(typeB)
        );
        when(roomRepository.findByBranchId(1L)).thenReturn(existingRooms);
    }

    @Test
    void currentState_exactlyAtCapacity_doesNotThrow() {
        assertDoesNotThrow(() -> itemService.assertSufficientStock(1L, Map.of()));
    }

    @Test
    void addingOneMoreRoomOfTypeB_exceedsStock_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.assertSufficientStock(1L, Map.of(typeB.getId(), 1)));
        assertTrue(ex.getMessage().contains("Tivi"));
    }

    @Test
    void changingOneRoomFromTypeAToTypeB_staysAtCapacity_doesNotThrow() {
        assertDoesNotThrow(() -> itemService.assertSufficientStock(1L,
                Map.of(typeA.getId(), -1, typeB.getId(), 1)));
    }

    private static RoomTypeHandoverItem handoverItem(RoomType roomType, Item item, int quantity) {
        RoomTypeHandoverItem handoverItem = new RoomTypeHandoverItem();
        handoverItem.setRoomType(roomType);
        handoverItem.setItem(item);
        handoverItem.setQuantity(quantity);
        return handoverItem;
    }

    private static Room roomOfType(RoomType roomType) {
        Room room = new Room();
        room.setRoomType(roomType);
        return room;
    }
}

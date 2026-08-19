package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.HandoverItemRequest;
import com.rentalroom.management.dto.request.RoomTypeRequest;
import com.rentalroom.management.dto.response.HandoverItemResponse;
import com.rentalroom.management.dto.response.RoomTypeResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Item;
import com.rentalroom.management.entity.RoomType;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ItemRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.RoomTypeHandoverItemRepository;
import com.rentalroom.management.repository.RoomTypeRepository;
import com.rentalroom.management.security.SecurityUtils;
import com.rentalroom.management.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomTypeServiceTest {

    private RoomTypeRepository roomTypeRepository;
    private RoomTypeHandoverItemRepository handoverItemRepository;
    private RoomRepository roomRepository;
    private BranchRepository branchRepository;
    private ItemRepository itemRepository;
    private RoomTypeService roomTypeService;

    private MockedStatic<SecurityUtils> securityUtils;

    private Branch branch;
    private RoomType roomType;
    private Item tivi;

    @BeforeEach
    void setUp() {
        roomTypeRepository = mock(RoomTypeRepository.class);
        handoverItemRepository = mock(RoomTypeHandoverItemRepository.class);
        roomRepository = mock(RoomRepository.class);
        branchRepository = mock(BranchRepository.class);
        itemRepository = mock(ItemRepository.class);

        roomTypeService = new RoomTypeService(roomTypeRepository, handoverItemRepository,
                roomRepository, branchRepository, itemRepository);

        branch = new Branch();
        branch.setId(1L);
        branch.setName("Chi nhánh 1");

        roomType = new RoomType();
        roomType.setId(10L);
        roomType.setName("Loại A");
        roomType.setBranch(branch);
        roomType.setArea("20m2");

        tivi = new Item();
        tivi.setId(100L);
        tivi.setBranch(branch);
        tivi.setName("Tivi");
        tivi.setPrice(new BigDecimal("1000000"));
        tivi.setQuantityAvailable(5);

        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(roomType));
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(inv -> inv.getArgument(0));
        when(handoverItemRepository.findByRoomTypeIdOrderByIdAsc(10L)).thenReturn(List.of());

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private RoomTypeRequest request(String name) {
        return new RoomTypeRequest(name, "20m2", "Mô tả");
    }

    // ---- get ----

    @Test
    void get_returnsRoomTypeWithHandoverItems() {
        RoomTypeResponse response = roomTypeService.get(10L);
        assertEquals("Loại A", response.name());
        assertEquals(1L, response.branchId());
    }

    @Test
    void get_notFound_throwsNotFound() {
        when(roomTypeRepository.findById(999L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> roomTypeService.get(999L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // ---- create ----

    @Test
    void create_valid_savesRoomType() {
        when(roomTypeRepository.existsByBranchIdAndName(1L, "Loại B")).thenReturn(false);

        RoomTypeResponse response = assertDoesNotThrow(() -> roomTypeService.create(1L, request("Loại B")));

        assertEquals("Loại B", response.name());
        assertEquals(1L, response.branchId());
    }

    @Test
    void create_branchNotFound_throwsNotFound() {
        when(branchRepository.findById(1L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomTypeService.create(1L, request("Loại B")));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void create_duplicateNameInBranch_throwsConflict() {
        when(roomTypeRepository.existsByBranchIdAndName(1L, "Loại A")).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomTypeService.create(1L, request("Loại A")));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    // ---- update ----

    @Test
    void update_sameName_skipsDuplicateCheck() {
        RoomTypeResponse response = assertDoesNotThrow(() -> roomTypeService.update(10L, request("Loại A")));
        assertEquals("Loại A", response.name());
        verify(roomTypeRepository, never()).existsByBranchIdAndNameAndIdNot(any(), any(), any());
    }

    @Test
    void update_renamedToDuplicateName_throwsConflict() {
        when(roomTypeRepository.existsByBranchIdAndNameAndIdNot(1L, "Loại B", 10L)).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomTypeService.update(10L, request("Loại B")));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void update_renamedNoConflict_saves() {
        when(roomTypeRepository.existsByBranchIdAndNameAndIdNot(1L, "Loại C", 10L)).thenReturn(false);
        RoomTypeResponse response = assertDoesNotThrow(() -> roomTypeService.update(10L, request("Loại C")));
        assertEquals("Loại C", response.name());
    }

    // ---- delete ----

    @Test
    void delete_notUsedByAnyRoom_deletes() {
        when(roomRepository.existsByRoomTypeId(10L)).thenReturn(false);
        assertDoesNotThrow(() -> roomTypeService.delete(10L));
        verify(roomTypeRepository).deleteById(10L);
    }

    @Test
    void delete_usedByRoom_throwsConflict() {
        when(roomRepository.existsByRoomTypeId(10L)).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> roomTypeService.delete(10L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(roomTypeRepository, never()).deleteById(any());
    }

    // ---- replaceHandoverItems ----

    @Test
    void replaceHandoverItems_valid_deletesOldAndSavesNew() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(tivi));
        when(handoverItemRepository.saveAll(any(List.class))).thenAnswer(inv -> inv.getArgument(0));

        List<HandoverItemRequest> items = List.of(new HandoverItemRequest(100L, 2, "ghi chú"));
        List<HandoverItemResponse> response = roomTypeService.replaceHandoverItems(10L, items);

        assertEquals(1, response.size());
        assertEquals(100L, response.get(0).itemId());
        verify(handoverItemRepository).deleteByRoomTypeId(10L);
        verify(handoverItemRepository).flush();
    }

    @Test
    void replaceHandoverItems_duplicateItemInList_throwsValidationError() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(tivi));

        List<HandoverItemRequest> items = List.of(
                new HandoverItemRequest(100L, 1, null),
                new HandoverItemRequest(100L, 1, null)
        );
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomTypeService.replaceHandoverItems(10L, items));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(handoverItemRepository, never()).deleteByRoomTypeId(any());
    }

    @Test
    void replaceHandoverItems_itemNotFound_throwsNotFound() {
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());
        List<HandoverItemRequest> items = List.of(new HandoverItemRequest(999L, 1, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomTypeService.replaceHandoverItems(10L, items));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void replaceHandoverItems_itemFromDifferentBranch_throwsValidationError() {
        Branch otherBranch = new Branch();
        otherBranch.setId(2L);
        Item foreignItem = new Item();
        foreignItem.setId(200L);
        foreignItem.setBranch(otherBranch);
        foreignItem.setName("Điều hòa");
        foreignItem.setQuantityAvailable(3);
        when(itemRepository.findById(200L)).thenReturn(Optional.of(foreignItem));

        List<HandoverItemRequest> items = List.of(new HandoverItemRequest(200L, 1, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomTypeService.replaceHandoverItems(10L, items));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void replaceHandoverItems_quantityExceedsAvailable_throwsValidationError() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(tivi));
        List<HandoverItemRequest> items = List.of(new HandoverItemRequest(100L, 6, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomTypeService.replaceHandoverItems(10L, items));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    // ---- listAll (branch-scoping smoke test) ----

    @Test
    void listAll_returnsRoomTypesForBranch() {
        when(roomTypeRepository.findByBranchIdOrderByNameAsc(1L)).thenReturn(List.of(roomType));
        List<RoomTypeResponse> response = roomTypeService.listAll(1L);
        assertEquals(1, response.size());
    }
}

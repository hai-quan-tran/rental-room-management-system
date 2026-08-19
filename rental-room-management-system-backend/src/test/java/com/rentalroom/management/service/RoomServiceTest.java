package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.RoomRequest;
import com.rentalroom.management.dto.response.BranchOptionResponse;
import com.rentalroom.management.dto.response.RoomDetailResponse;
import com.rentalroom.management.dto.response.RoomResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.Item;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.RoomType;
import com.rentalroom.management.entity.RoomTypeHandoverItem;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.enums.RoomStatus;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ContractRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomServiceTest {

    private RoomRepository roomRepository;
    private BranchRepository branchRepository;
    private RoomTypeRepository roomTypeRepository;
    private RoomTypeHandoverItemRepository handoverItemRepository;
    private ContractRepository contractRepository;
    private BranchService branchService;
    private ItemService itemService;
    private RoomService roomService;

    private MockedStatic<SecurityUtils> securityUtils;

    private Branch branch;
    private RoomType roomTypeA;
    private RoomType roomTypeB;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        branchRepository = mock(BranchRepository.class);
        roomTypeRepository = mock(RoomTypeRepository.class);
        handoverItemRepository = mock(RoomTypeHandoverItemRepository.class);
        contractRepository = mock(ContractRepository.class);
        branchService = mock(BranchService.class);
        itemService = mock(ItemService.class);

        roomService = new RoomService(roomRepository, branchRepository, roomTypeRepository,
                handoverItemRepository, contractRepository, branchService, itemService);

        branch = new Branch();
        branch.setId(1L);
        branch.setName("Chi nhánh 1");

        roomTypeA = new RoomType();
        roomTypeA.setId(10L);
        roomTypeA.setName("Loại A");
        roomTypeA.setBranch(branch);

        roomTypeB = new RoomType();
        roomTypeB.setId(20L);
        roomTypeB.setName("Loại B");
        roomTypeB.setBranch(branch);

        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(roomTypeA));
        when(roomTypeRepository.findById(20L)).thenReturn(Optional.of(roomTypeB));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private Room room(Long id, RoomType roomType, String roomCode) {
        Room room = new Room();
        room.setId(id);
        room.setBranch(branch);
        room.setRoomType(roomType);
        room.setRoomCode(roomCode);
        room.setMonthlyRent(new BigDecimal("2000000"));
        room.setWifiFee(new BigDecimal("50000"));
        room.setParkingFee(new BigDecimal("30000"));
        room.setStatus(RoomStatus.TRONG);
        return room;
    }

    private RoomRequest request(String roomCode, Long roomTypeId) {
        return new RoomRequest(roomCode, roomTypeId, new BigDecimal("2000000"),
                new BigDecimal("50000"), new BigDecimal("30000"));
    }

    // ---- get ----

    @Test
    void get_returnsRoomWithHandoverItems() {
        Room room = room(100L, roomTypeA, "P101");
        when(roomRepository.findById(100L)).thenReturn(Optional.of(room));
        RoomTypeHandoverItem handoverItem = new RoomTypeHandoverItem();
        handoverItem.setId(900L);
        Item item = new Item();
        item.setId(1L);
        item.setName("Tivi");
        item.setPrice(new BigDecimal("1000000"));
        handoverItem.setItem(item);
        handoverItem.setQuantity(1);
        when(handoverItemRepository.findByRoomTypeIdOrderByIdAsc(10L)).thenReturn(List.of(handoverItem));

        RoomDetailResponse detail = roomService.get(100L);

        assertEquals("P101", detail.room().roomCode());
        assertEquals(1, detail.handoverItems().size());
    }

    @Test
    void get_notFound_throwsNotFound() {
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> roomService.get(999L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // ---- branchOptions ----

    @Test
    void branchOptions_adminTong_returnsAllBranches() {
        when(branchRepository.findAll()).thenReturn(List.of(branch));

        List<BranchOptionResponse> options = roomService.branchOptions();

        assertEquals(1, options.size());
        assertEquals(1L, options.get(0).id());
    }

    @Test
    void branchOptions_adminCap1_returnsOnlyManagedBranches() {
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(2L, "manager", Role.ADMIN_CAP_1, List.of(1L)));
        when(branchRepository.findAllById(List.of(1L))).thenReturn(List.of(branch));

        List<BranchOptionResponse> options = roomService.branchOptions();

        assertEquals(1, options.size());
        verify(branchRepository, never()).findAll();
    }

    // ---- create ----

    @Test
    void create_valid_savesRoomAndEvictsSummary() {
        when(roomRepository.existsByBranchIdAndRoomCode(1L, "P101")).thenReturn(false);

        RoomResponse response = assertDoesNotThrow(() -> roomService.create(1L, request("P101", 10L)));

        assertEquals("P101", response.roomCode());
        assertEquals(RoomStatus.TRONG, response.status());
        verify(itemService).assertSufficientStock(1L, Map.of(10L, 1));
        verify(branchService).evictRoomTypeSummary(1L);
    }

    @Test
    void create_branchNotFound_throwsNotFound() {
        when(branchRepository.findById(1L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomService.create(1L, request("P101", 10L)));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void create_duplicateRoomCode_throwsConflict() {
        when(roomRepository.existsByBranchIdAndRoomCode(1L, "P101")).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomService.create(1L, request("P101", 10L)));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void create_roomTypeNotFound_throwsNotFound() {
        when(roomTypeRepository.findById(99L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomService.create(1L, request("P101", 99L)));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void create_roomTypeBelongsToDifferentBranch_throwsValidationError() {
        Branch otherBranch = new Branch();
        otherBranch.setId(2L);
        RoomType foreignType = new RoomType();
        foreignType.setId(30L);
        foreignType.setBranch(otherBranch);
        when(roomTypeRepository.findById(30L)).thenReturn(Optional.of(foreignType));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomService.create(1L, request("P101", 30L)));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void create_insufficientStock_propagatesException() {
        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "Không đủ vật dụng"))
                .when(itemService).assertSufficientStock(eq(1L), anyMap());

        assertThrows(BusinessException.class, () -> roomService.create(1L, request("P101", 10L)));
        verify(roomRepository, never()).save(any());
    }

    // ---- update ----

    @Test
    void update_sameRoomTypeAndCode_doesNotCheckStockOrPermission() {
        Room existing = room(100L, roomTypeA, "P101");
        when(roomRepository.findById(100L)).thenReturn(Optional.of(existing));

        RoomResponse response = assertDoesNotThrow(() -> roomService.update(100L, request("P101", 10L)));

        assertEquals("P101", response.roomCode());
        verify(itemService, never()).assertSufficientStock(any(), any());
        verify(branchService).evictRoomTypeSummary(1L);
    }

    @Test
    void update_duplicateRoomCode_throwsConflict() {
        Room existing = room(100L, roomTypeA, "P101");
        when(roomRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(roomRepository.existsByBranchIdAndRoomCodeAndIdNot(1L, "P102", 100L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomService.update(100L, request("P102", 10L)));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void update_roomTypeChangeByAdminCap1_throwsAccessDenied() {
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(2L, "manager", Role.ADMIN_CAP_1, List.of(1L)));
        Room existing = room(100L, roomTypeA, "P101");
        when(roomRepository.findById(100L)).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomService.update(100L, request("P101", 20L)));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        verify(itemService, never()).assertSufficientStock(any(), any());
    }

    @Test
    void update_roomTypeChangeByAdminTong_checksStockAndSaves() {
        Room existing = room(100L, roomTypeA, "P101");
        when(roomRepository.findById(100L)).thenReturn(Optional.of(existing));

        RoomResponse response = assertDoesNotThrow(() -> roomService.update(100L, request("P101", 20L)));

        assertEquals(20L, response.roomTypeId());
        verify(itemService).assertSufficientStock(1L, Map.of(10L, -1, 20L, 1));
    }

    @Test
    void update_newRoomTypeBelongsToDifferentBranch_throwsValidationError() {
        Room existing = room(100L, roomTypeA, "P101");
        when(roomRepository.findById(100L)).thenReturn(Optional.of(existing));
        Branch otherBranch = new Branch();
        otherBranch.setId(2L);
        RoomType foreignType = new RoomType();
        foreignType.setId(30L);
        foreignType.setBranch(otherBranch);
        when(roomTypeRepository.findById(30L)).thenReturn(Optional.of(foreignType));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roomService.update(100L, request("P101", 30L)));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    // ---- delete ----

    @Test
    void delete_emptyRoomNoHistory_deletesAndEvictsSummary() {
        Room existing = room(100L, roomTypeA, "P101");
        when(roomRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(contractRepository.findByRoomIdOrderByStartDateDesc(100L)).thenReturn(List.of());

        assertDoesNotThrow(() -> roomService.delete(100L));

        verify(roomRepository).deleteById(100L);
        verify(branchService).evictRoomTypeSummary(1L);
    }

    @Test
    void delete_roomNotEmpty_throwsConflict() {
        Room existing = room(100L, roomTypeA, "P101");
        existing.setStatus(RoomStatus.DANG_THUE);
        when(roomRepository.findById(100L)).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class, () -> roomService.delete(100L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(roomRepository, never()).deleteById(any());
    }

    @Test
    void delete_hasContractHistory_throwsConflict() {
        Room existing = room(100L, roomTypeA, "P101");
        when(roomRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(contractRepository.findByRoomIdOrderByStartDateDesc(100L))
                .thenReturn(List.of(new Contract()));

        BusinessException ex = assertThrows(BusinessException.class, () -> roomService.delete(100L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(roomRepository, never()).deleteById(any());
    }
}

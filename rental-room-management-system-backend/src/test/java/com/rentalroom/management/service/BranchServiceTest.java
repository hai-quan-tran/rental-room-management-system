package com.rentalroom.management.service;

import com.rentalroom.management.common.PageResponse;
import com.rentalroom.management.dto.request.BranchRequest;
import com.rentalroom.management.dto.response.BranchDetailResponse;
import com.rentalroom.management.dto.response.BranchResponse;
import com.rentalroom.management.dto.response.RoomTypeSummaryResponse;
import com.rentalroom.management.entity.Account;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.BranchRoomSummary;
import com.rentalroom.management.entity.BranchRoomSummaryId;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.AccountRepository;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.BranchRoomSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BranchServiceTest {

    private BranchRepository branchRepository;
    private BranchRoomSummaryRepository branchRoomSummaryRepository;
    private AccountRepository accountRepository;
    private BranchService branchService;

    private Branch branch;
    private Account manager;

    @BeforeEach
    void setUp() {
        branchRepository = mock(BranchRepository.class);
        branchRoomSummaryRepository = mock(BranchRoomSummaryRepository.class);
        accountRepository = mock(AccountRepository.class);
        branchService = new BranchService(branchRepository, branchRoomSummaryRepository, accountRepository);

        branch = new Branch();
        branch.setId(1L);
        branch.setName("Chi nhánh Q1");
        branch.setAddress("123 Nguyễn Huệ");

        manager = new Account();
        manager.setId(10L);
        manager.setRole(Role.ADMIN_CAP_1);
        manager.setFullName("Nguyễn Văn A");

        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchRoomSummaryRepository.findById_BranchId(1L)).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    @Test
    void list_delegatesToRepositoryAndWrapsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(branchRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(branch), pageable, 1));

        PageResponse<BranchResponse> response = branchService.list("Q1", pageable);

        assertEquals(1, response.totalElements());
        assertEquals("Chi nhánh Q1", response.content().get(0).name());
    }

    @Test
    void get_found_returnsDetailWithSummaries() {
        BranchRoomSummary summary = newSummary(1L, 100L, "Standard", 5L, 2L, 3L);
        when(branchRoomSummaryRepository.findById_BranchId(1L)).thenReturn(List.of(summary));

        BranchDetailResponse response = branchService.get(1L);

        assertEquals("Chi nhánh Q1", response.branch().name());
        assertEquals(5L, response.totalRooms());
        assertEquals(1, response.roomTypeSummaries().size());
    }

    @Test
    void get_notFound_throwsNotFound() {
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> branchService.get(99L));
    }

    @Test
    void getRoomTypeSummaries_mapsEachSummary() {
        BranchRoomSummary summary = newSummary(1L, 100L, "Standard", 5L, 2L, 3L);
        when(branchRoomSummaryRepository.findById_BranchId(1L)).thenReturn(List.of(summary));

        List<RoomTypeSummaryResponse> result = branchService.getRoomTypeSummaries(1L);

        assertEquals(1, result.size());
        assertEquals("Standard", result.get(0).roomTypeName());
        assertEquals(5L, result.get(0).roomCount());
        assertEquals(2L, result.get(0).emptyRoomCount());
        assertEquals(3L, result.get(0).occupiedRoomCount());
    }

    @Test
    void create_noManager_savesWithNullManager() {
        BranchRequest request = new BranchRequest("Chi nhánh mới", "456 Lê Lợi", null);

        BranchResponse response = assertDoesNotThrow(() -> branchService.create(request));

        assertNull(response.managerAccountId());
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void create_managerNotFound_throwsNotFound() {
        when(accountRepository.findById(10L)).thenReturn(Optional.empty());
        BranchRequest request = new BranchRequest("Chi nhánh mới", "456 Lê Lợi", 10L);

        assertThrows(BusinessException.class, () -> branchService.create(request));
    }

    @Test
    void create_managerWrongRole_throwsValidationError() {
        manager.setRole(Role.USER);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(manager));
        BranchRequest request = new BranchRequest("Chi nhánh mới", "456 Lê Lợi", 10L);

        assertThrows(BusinessException.class, () -> branchService.create(request));
    }

    @Test
    void create_validManager_savesWithManager() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(manager));
        BranchRequest request = new BranchRequest("Chi nhánh mới", "456 Lê Lợi", 10L);

        BranchResponse response = assertDoesNotThrow(() -> branchService.create(request));

        assertEquals(10L, response.managerAccountId());

        ArgumentCaptor<Branch> captor = ArgumentCaptor.forClass(Branch.class);
        verify(branchRepository).save(captor.capture());
        assertEquals(manager, captor.getValue().getManagerAccount());
    }

    @Test
    void update_notFound_throwsNotFound() {
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());
        BranchRequest request = new BranchRequest("Tên mới", "Địa chỉ mới", null);

        assertThrows(BusinessException.class, () -> branchService.update(99L, request));
    }

    @Test
    void update_valid_updatesNameAddressAndManager() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(manager));
        BranchRequest request = new BranchRequest("Tên mới", "Địa chỉ mới", 10L);

        BranchResponse response = assertDoesNotThrow(() -> branchService.update(1L, request));

        assertEquals("Tên mới", response.name());
        assertEquals("Địa chỉ mới", response.address());
        assertEquals(10L, response.managerAccountId());
    }

    private BranchRoomSummary newSummary(long branchId, long roomTypeId, String roomTypeName,
                                          long roomCount, long emptyCount, long occupiedCount) {
        BranchRoomSummary summary = new BranchRoomSummary();
        ReflectionTestUtils.setField(summary, "id", new BranchRoomSummaryId(branchId, roomTypeId));
        ReflectionTestUtils.setField(summary, "roomTypeName", roomTypeName);
        ReflectionTestUtils.setField(summary, "roomCount", roomCount);
        ReflectionTestUtils.setField(summary, "emptyRoomCount", emptyCount);
        ReflectionTestUtils.setField(summary, "occupiedRoomCount", occupiedCount);
        return summary;
    }
}

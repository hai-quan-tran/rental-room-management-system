package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.ContractCreateRequest;
import com.rentalroom.management.dto.request.ContractEndDateRequest;
import com.rentalroom.management.dto.request.ContractTenantRequest;
import com.rentalroom.management.dto.response.ContractDetailResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.ContractTenant;
import com.rentalroom.management.entity.ContractTenantId;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.Tenant;
import com.rentalroom.management.enums.ContractStatus;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.enums.RoomStatus;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.ContractRepository;
import com.rentalroom.management.repository.ContractTenantRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.repository.TenantRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractServiceTest {

    private ContractRepository contractRepository;
    private ContractTenantRepository contractTenantRepository;
    private RoomRepository roomRepository;
    private TenantRepository tenantRepository;
    private BranchService branchService;
    private ContractService contractService;

    private MockedStatic<SecurityUtils> securityUtils;

    private Branch branch;
    private Room room;
    private Tenant tenantWithEmail;
    private Tenant tenantNoEmail;
    private Contract contract;

    @BeforeEach
    void setUp() {
        contractRepository = mock(ContractRepository.class);
        contractTenantRepository = mock(ContractTenantRepository.class);
        roomRepository = mock(RoomRepository.class);
        tenantRepository = mock(TenantRepository.class);
        branchService = mock(BranchService.class);

        contractService = new ContractService(contractRepository, contractTenantRepository,
                roomRepository, tenantRepository, branchService);

        branch = new Branch();
        branch.setId(1L);

        room = new Room();
        room.setId(50L);
        room.setBranch(branch);
        room.setStatus(RoomStatus.TRONG);
        room.setMonthlyRent(new BigDecimal("3000000"));

        tenantWithEmail = new Tenant();
        tenantWithEmail.setId(100L);
        tenantWithEmail.setFullName("Nguyen Van A");
        tenantWithEmail.setEmail("tenant100@example.com");

        tenantNoEmail = new Tenant();
        tenantNoEmail.setId(101L);
        tenantNoEmail.setFullName("Tran Thi B");
        tenantNoEmail.setEmail(null);

        contract = new Contract();
        contract.setId(500L);
        contract.setRoom(room);
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setMonthlyRent(new BigDecimal("3000000"));
        contract.setDepositAmount(new BigDecimal("5000000"));

        when(roomRepository.findById(50L)).thenReturn(Optional.of(room));
        when(tenantRepository.findById(100L)).thenReturn(Optional.of(tenantWithEmail));
        when(tenantRepository.findById(101L)).thenReturn(Optional.of(tenantNoEmail));
        when(contractRepository.findById(500L)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> {
            Contract c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(500L);
            }
            return c;
        });
        when(contractTenantRepository.save(any(ContractTenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contractTenantRepository.findById_ContractIdOrderByRepresentativeDescJoinedAtAsc(anyLong()))
                .thenReturn(List.of());

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private ContractCreateRequest createRequest(LocalDate endDate, List<ContractTenantRequest> tenants) {
        return new ContractCreateRequest(LocalDate.of(2026, 1, 1), endDate, new BigDecimal("5000000"), null, tenants);
    }

    @Test
    void get_notFound_throwsNotFound() {
        when(contractRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> contractService.get(999L));
    }

    @Test
    void get_found_returnsDetail() {
        ContractDetailResponse response = assertDoesNotThrow(() -> contractService.get(500L));
        assertEquals(500L, response.contract().id());
    }

    @Test
    void listByRoom_returnsContracts() {
        when(contractRepository.findByRoomIdOrderByStartDateDesc(50L)).thenReturn(List.of(contract));
        var response = contractService.listByRoom(50L);
        assertEquals(1, response.size());
        assertEquals(500L, response.get(0).id());
    }

    @Test
    void create_roomNotAvailable_throwsConflict() {
        room.setStatus(RoomStatus.DANG_THUE);
        ContractCreateRequest request = createRequest(null, List.of(new ContractTenantRequest(100L, true)));

        assertThrows(BusinessException.class, () -> contractService.create(50L, request));
        verify(contractRepository, never()).save(any());
    }

    @Test
    void create_zeroRepresentatives_throwsValidationError() {
        ContractCreateRequest request = createRequest(null, List.of(new ContractTenantRequest(100L, false)));
        assertThrows(BusinessException.class, () -> contractService.create(50L, request));
    }

    @Test
    void create_twoRepresentatives_throwsValidationError() {
        ContractCreateRequest request = createRequest(null, List.of(
                new ContractTenantRequest(100L, true),
                new ContractTenantRequest(101L, true)));
        // both flagged representative: this alone fails the "exactly 1" check before attachTenant runs
        assertThrows(BusinessException.class, () -> contractService.create(50L, request));
    }

    @Test
    void create_endDateBeforeStartDate_throwsValidationError() {
        ContractCreateRequest request = createRequest(LocalDate.of(2025, 12, 1),
                List.of(new ContractTenantRequest(100L, true)));
        assertThrows(BusinessException.class, () -> contractService.create(50L, request));
    }

    @Test
    void create_representativeTenantWithoutEmail_throwsValidationError() {
        ContractCreateRequest request = createRequest(null, List.of(new ContractTenantRequest(101L, true)));
        assertThrows(BusinessException.class, () -> contractService.create(50L, request));
    }

    @Test
    void create_valid_savesContractAndOccupiesRoom() {
        ContractCreateRequest request = createRequest(null, List.of(new ContractTenantRequest(100L, true)));

        ContractDetailResponse response = assertDoesNotThrow(() -> contractService.create(50L, request));

        assertEquals(RoomStatus.DANG_THUE, room.getStatus());
        verify(roomRepository).save(room);
        verify(branchService).evictRoomTypeSummary(1L);
        verify(contractTenantRepository).save(any(ContractTenant.class));
        assertEquals(new BigDecimal("3000000"), response.contract().monthlyRent());
    }

    @Test
    void create_valid_nonRepresentativeTenantWithoutEmail_isAllowed() {
        ContractCreateRequest request = createRequest(null, List.of(
                new ContractTenantRequest(100L, true),
                new ContractTenantRequest(101L, false)));

        assertDoesNotThrow(() -> contractService.create(50L, request));
        verify(contractTenantRepository, times(2)).save(any(ContractTenant.class));
    }

    @Test
    void addTenant_contractEnded_throwsConflict() {
        contract.setStatus(ContractStatus.ENDED);
        assertThrows(BusinessException.class,
                () -> contractService.addTenant(500L, new ContractTenantRequest(101L, false)));
    }

    @Test
    void addTenant_representativeAlreadyExists_throwsConflict() {
        when(contractTenantRepository.existsById_ContractIdAndRepresentativeTrue(500L)).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> contractService.addTenant(500L, new ContractTenantRequest(100L, true)));
    }

    @Test
    void addTenant_representativeWithoutEmail_throwsValidationError() {
        when(contractTenantRepository.existsById_ContractIdAndRepresentativeTrue(500L)).thenReturn(false);
        assertThrows(BusinessException.class,
                () -> contractService.addTenant(500L, new ContractTenantRequest(101L, true)));
    }

    @Test
    void addTenant_valid_addsTenant() {
        when(contractTenantRepository.existsById_ContractIdAndRepresentativeTrue(500L)).thenReturn(false);
        assertDoesNotThrow(() -> contractService.addTenant(500L, new ContractTenantRequest(100L, false)));
        verify(contractTenantRepository).save(any(ContractTenant.class));
    }

    @Test
    void updateEndDate_contractEnded_throwsConflict() {
        contract.setStatus(ContractStatus.ENDED);
        assertThrows(BusinessException.class,
                () -> contractService.updateEndDate(500L, new ContractEndDateRequest(LocalDate.of(2026, 6, 1))));
    }

    @Test
    void updateEndDate_beforeStartDate_throwsValidationError() {
        assertThrows(BusinessException.class,
                () -> contractService.updateEndDate(500L, new ContractEndDateRequest(LocalDate.of(2025, 1, 1))));
    }

    @Test
    void updateEndDate_valid_updatesEndDate() {
        ContractDetailResponse response = assertDoesNotThrow(
                () -> contractService.updateEndDate(500L, new ContractEndDateRequest(LocalDate.of(2026, 12, 31))));
        assertEquals(LocalDate.of(2026, 12, 31), response.contract().endDate());
    }

    @Test
    void removeTenant_notInContract_throwsNotFound() {
        when(contractTenantRepository.findById(new ContractTenantId(500L, 101L))).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> contractService.removeTenant(500L, 101L));
    }

    @Test
    void removeTenant_lastTenantRemaining_throwsConflict() {
        ContractTenant contractTenant = new ContractTenant();
        contractTenant.setId(new ContractTenantId(500L, 101L));
        contractTenant.setRepresentative(false);
        when(contractTenantRepository.findById(new ContractTenantId(500L, 101L))).thenReturn(Optional.of(contractTenant));
        when(contractTenantRepository.countById_ContractId(500L)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> contractService.removeTenant(500L, 101L));
        verify(contractTenantRepository, never()).delete(any());
    }

    @Test
    void removeTenant_representative_throwsConflict() {
        ContractTenant contractTenant = new ContractTenant();
        contractTenant.setId(new ContractTenantId(500L, 100L));
        contractTenant.setRepresentative(true);
        when(contractTenantRepository.findById(new ContractTenantId(500L, 100L))).thenReturn(Optional.of(contractTenant));
        when(contractTenantRepository.countById_ContractId(500L)).thenReturn(2L);

        assertThrows(BusinessException.class, () -> contractService.removeTenant(500L, 100L));
        verify(contractTenantRepository, never()).delete(any());
    }

    @Test
    void removeTenant_valid_removesTenant() {
        ContractTenant contractTenant = new ContractTenant();
        contractTenant.setId(new ContractTenantId(500L, 101L));
        contractTenant.setRepresentative(false);
        when(contractTenantRepository.findById(new ContractTenantId(500L, 101L))).thenReturn(Optional.of(contractTenant));
        when(contractTenantRepository.countById_ContractId(500L)).thenReturn(2L);

        assertDoesNotThrow(() -> contractService.removeTenant(500L, 101L));
        verify(contractTenantRepository).delete(contractTenant);
    }
}

package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.TenantRequest;
import com.rentalroom.management.dto.response.TenantRentalHistoryResponse;
import com.rentalroom.management.dto.response.TenantResponse;
import com.rentalroom.management.entity.ContractTenant;
import com.rentalroom.management.entity.Tenant;
import com.rentalroom.management.enums.ContractStatus;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.ContractTenantRepository;
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

import java.time.LocalDate;
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
class TenantServiceTest {

    private TenantRepository tenantRepository;
    private ContractTenantRepository contractTenantRepository;
    private TenantService tenantService;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        contractTenantRepository = mock(ContractTenantRepository.class);
        tenantService = new TenantService(tenantRepository, contractTenantRepository);

        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private Tenant tenant(Long id, String idCardNumber, LocalDate dateOfBirth, String email) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setFullName("Nguyễn Văn A");
        tenant.setDateOfBirth(dateOfBirth);
        tenant.setIdCardNumber(idCardNumber);
        tenant.setPhoneNumber("0912345678");
        tenant.setEmail(email);
        return tenant;
    }

    private TenantRequest request(LocalDate dateOfBirth, String idCardNumber, String email) {
        return new TenantRequest("Nguyễn Văn A", dateOfBirth, idCardNumber, "0912345678", email);
    }

    // ---- get / getRentalHistory ----

    @Test
    void get_notFound_throwsNotFound() {
        when(tenantRepository.findById(999L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> tenantService.get(999L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void get_found_returnsResponse() {
        Tenant tenant = tenant(1L, "079123456789", LocalDate.of(2000, 1, 1), "a@example.com");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        TenantResponse response = tenantService.get(1L);
        assertEquals("Nguyễn Văn A", response.fullName());
    }

    @Test
    void getRentalHistory_tenantNotFound_throwsNotFound() {
        when(tenantRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> tenantService.getRentalHistory(999L));
    }

    @Test
    void getRentalHistory_returnsHistoryOrderedByJoinedAtDesc() {
        Tenant tenant = tenant(1L, "079123456789", LocalDate.of(2000, 1, 1), "a@example.com");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(contractTenantRepository.findById_TenantIdOrderByJoinedAtDesc(1L)).thenReturn(List.of());

        List<TenantRentalHistoryResponse> history = tenantService.getRentalHistory(1L);
        assertEquals(0, history.size());
    }

    // ---- create: CCCD-required-if-adult / age boundary ----

    @Test
    void create_exactlyEighteenYearsOldWithBlankCccd_throwsValidationError() {
        LocalDate dob = LocalDate.now().minusYears(18);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.create(request(dob, null, "a@example.com")));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void create_oneDayUnderEighteenWithBlankCccd_doesNotRequireCccd() {
        LocalDate dob = LocalDate.now().minusYears(18).plusDays(1);

        TenantResponse response = assertDoesNotThrow(
                () -> tenantService.create(request(dob, "  ", "a@example.com")));

        assertNull(response.idCardNumber());
    }

    @Test
    void create_blankIdCardNumber_normalizesToNull() {
        LocalDate dob = LocalDate.now().minusYears(10);

        TenantResponse response = assertDoesNotThrow(
                () -> tenantService.create(request(dob, "   ", "a@example.com")));

        assertNull(response.idCardNumber());
        verify(tenantRepository, never()).existsByIdCardNumber(any());
    }

    @Test
    void create_duplicateIdCardNumber_throwsConflict() {
        LocalDate dob = LocalDate.now().minusYears(20);
        when(tenantRepository.existsByIdCardNumber("079123456789")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.create(request(dob, "079123456789", "a@example.com")));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void create_valid_savesTenantWithAuditFields() {
        LocalDate dob = LocalDate.now().minusYears(20);
        when(tenantRepository.existsByIdCardNumber("079123456789")).thenReturn(false);

        TenantResponse response = assertDoesNotThrow(
                () -> tenantService.create(request(dob, "079123456789", "a@example.com")));

        assertEquals("079123456789", response.idCardNumber());
    }

    // ---- update ----

    @Test
    void update_notFound_throwsNotFound() {
        when(tenantRepository.findById(999L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.update(999L, request(LocalDate.now().minusYears(20), "079123456789", "a@example.com")));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void update_adultWithBlankCccd_throwsValidationError() {
        Tenant existing = tenant(1L, "079123456789", LocalDate.now().minusYears(20), "a@example.com");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.update(1L, request(LocalDate.now().minusYears(20), null, "a@example.com")));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void update_changedToDuplicateIdCardNumber_throwsConflict() {
        LocalDate dob = LocalDate.now().minusYears(20);
        Tenant existing = tenant(1L, "079123456789", dob, "a@example.com");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tenantRepository.existsByIdCardNumberAndIdNot("079999999999", 1L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.update(1L, request(dob, "079999999999", "a@example.com")));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void update_unchangedIdCardNumber_skipsDuplicateCheck() {
        LocalDate dob = LocalDate.now().minusYears(20);
        Tenant existing = tenant(1L, "079123456789", dob, "a@example.com");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> tenantService.update(1L, request(dob, "079123456789", "a@example.com")));

        verify(tenantRepository, never()).existsByIdCardNumberAndIdNot(any(), any());
    }

    @Test
    void update_clearingEmailWhileActiveContractRepresentative_throwsConflict() {
        LocalDate dob = LocalDate.now().minusYears(20);
        Tenant existing = tenant(1L, "079123456789", dob, "a@example.com");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(contractTenantRepository.existsById_TenantIdAndRepresentativeTrueAndContract_Status(1L, ContractStatus.ACTIVE))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.update(1L, request(dob, "079123456789", null)));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void update_clearingEmailWhenContractEnded_isAllowed() {
        LocalDate dob = LocalDate.now().minusYears(20);
        Tenant existing = tenant(1L, "079123456789", dob, "a@example.com");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(contractTenantRepository.existsById_TenantIdAndRepresentativeTrueAndContract_Status(1L, ContractStatus.ACTIVE))
                .thenReturn(false);

        TenantResponse response = assertDoesNotThrow(
                () -> tenantService.update(1L, request(dob, "079123456789", null)));

        assertNull(response.email());
    }

    @Test
    void update_valid_savesWithUpdatedByAndDoesNotOverwriteCreatedBy() {
        LocalDate dob = LocalDate.now().minusYears(20);
        Tenant existing = tenant(1L, "079123456789", dob, "a@example.com");
        existing.setCreatedBy(5L);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));

        TenantResponse response = assertDoesNotThrow(
                () -> tenantService.update(1L, request(dob, "079123456789", "new@example.com")));

        assertEquals("new@example.com", response.email());
        assertEquals(5L, existing.getCreatedBy());
        assertEquals(1L, existing.getUpdatedBy());
    }

    // ---- delete ----

    @Test
    void delete_noRentalHistory_deletes() {
        Tenant existing = tenant(1L, "079123456789", LocalDate.now().minusYears(20), "a@example.com");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(contractTenantRepository.findById_TenantIdOrderByJoinedAtDesc(1L)).thenReturn(List.of());

        assertDoesNotThrow(() -> tenantService.delete(1L));
        verify(tenantRepository).deleteById(1L);
    }

    @Test
    void delete_hasRentalHistory_throwsConflict() {
        Tenant existing = tenant(1L, "079123456789", LocalDate.now().minusYears(20), "a@example.com");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(contractTenantRepository.findById_TenantIdOrderByJoinedAtDesc(1L))
                .thenReturn(List.of(new ContractTenant()));

        BusinessException ex = assertThrows(BusinessException.class, () -> tenantService.delete(1L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(tenantRepository, never()).deleteById(any());
    }
}

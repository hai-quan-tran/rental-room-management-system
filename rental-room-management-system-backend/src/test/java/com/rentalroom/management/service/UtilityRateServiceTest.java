package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.UtilityRateRequest;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.ExtraFeeCategory;
import com.rentalroom.management.entity.UtilityRate;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ExtraFeeCategoryRepository;
import com.rentalroom.management.repository.UtilityRateRepository;
import com.rentalroom.management.security.SecurityUtils;
import com.rentalroom.management.security.UserPrincipal;
import com.rentalroom.management.enums.Role;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UtilityRateServiceTest {

    private UtilityRateRepository utilityRateRepository;
    private BranchRepository branchRepository;
    private ExtraFeeCategoryRepository extraFeeCategoryRepository;
    private UtilityRateService utilityRateService;

    private MockedStatic<SecurityUtils> securityUtils;

    private Branch branch;
    private ExtraFeeCategory electricity;
    private ExtraFeeCategory wifi;

    @BeforeEach
    void setUp() {
        utilityRateRepository = mock(UtilityRateRepository.class);
        branchRepository = mock(BranchRepository.class);
        extraFeeCategoryRepository = mock(ExtraFeeCategoryRepository.class);
        utilityRateService = new UtilityRateService(utilityRateRepository, branchRepository, extraFeeCategoryRepository);

        branch = new Branch();
        branch.setId(1L);

        electricity = new ExtraFeeCategory();
        electricity.setId(10L);
        electricity.setName("Điện");
        electricity.setUnit("kWh");
        electricity.setMetered(true);

        wifi = new ExtraFeeCategory();
        wifi.setId(20L);
        wifi.setName("Wifi");
        wifi.setMetered(false);

        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(utilityRateRepository.save(any(UtilityRate.class))).thenAnswer(inv -> inv.getArgument(0));

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void create_nonMeteredCategory_throwsValidationError() {
        when(extraFeeCategoryRepository.findById(20L)).thenReturn(Optional.of(wifi));

        UtilityRateRequest request = new UtilityRateRequest(20L, new BigDecimal("3500"), LocalDate.of(2026, 1, 1));
        assertThrows(BusinessException.class, () -> utilityRateService.create(1L, request));
    }

    @Test
    void create_duplicateEffectiveDate_throwsConflict() {
        when(extraFeeCategoryRepository.findById(10L)).thenReturn(Optional.of(electricity));
        when(utilityRateRepository.existsByBranchIdAndExtraFeeCategoryIdAndEffectiveFrom(1L, 10L, LocalDate.of(2026, 1, 1)))
                .thenReturn(true);

        UtilityRateRequest request = new UtilityRateRequest(10L, new BigDecimal("3500"), LocalDate.of(2026, 1, 1));
        assertThrows(BusinessException.class, () -> utilityRateService.create(1L, request));
    }

    @Test
    void create_valid_savesRate() {
        when(extraFeeCategoryRepository.findById(10L)).thenReturn(Optional.of(electricity));

        UtilityRateRequest request = new UtilityRateRequest(10L, new BigDecimal("3500"), LocalDate.of(2026, 1, 1));
        var response = assertDoesNotThrow(() -> utilityRateService.create(1L, request));

        assertEquals(new BigDecimal("3500"), response.unitPrice());
        assertEquals(10L, response.extraFeeCategoryId());
    }

    @Test
    void findCurrentRate_picksLatestRateAtOrBeforeReferenceDate() {
        UtilityRate rate = new UtilityRate();
        rate.setUnitPrice(new BigDecimal("4000"));
        rate.setEffectiveFrom(LocalDate.of(2026, 2, 1));
        when(utilityRateRepository.findTopByBranchIdAndExtraFeeCategoryIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                1L, 10L, LocalDate.of(2026, 3, 1))).thenReturn(Optional.of(rate));

        Optional<UtilityRate> resolved = utilityRateService.findCurrentRate(1L, 10L, LocalDate.of(2026, 3, 1));
        assertEquals(new BigDecimal("4000"), resolved.orElseThrow().getUnitPrice());
    }
}

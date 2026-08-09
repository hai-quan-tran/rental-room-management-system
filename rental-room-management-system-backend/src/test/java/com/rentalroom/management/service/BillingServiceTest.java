package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.MonthlyBillCreateRequest;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.ExtraFeeCategory;
import com.rentalroom.management.entity.ExtraFeeItem;
import com.rentalroom.management.entity.MeterReading;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.UtilityRate;
import com.rentalroom.management.enums.ContractStatus;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.ContractRepository;
import com.rentalroom.management.repository.ExtraFeeCategoryRepository;
import com.rentalroom.management.repository.ExtraFeeItemRepository;
import com.rentalroom.management.repository.MeterReadingRepository;
import com.rentalroom.management.repository.MonthlyBillRepository;
import com.rentalroom.management.repository.PaymentRepository;
import com.rentalroom.management.security.SecurityUtils;
import com.rentalroom.management.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    private MonthlyBillRepository monthlyBillRepository;
    private ExtraFeeItemRepository extraFeeItemRepository;
    private ExtraFeeCategoryRepository extraFeeCategoryRepository;
    private PaymentRepository paymentRepository;
    private ContractRepository contractRepository;
    private BranchRepository branchRepository;
    private MeterReadingRepository meterReadingRepository;
    private UtilityRateService utilityRateService;
    private EntityManager entityManager;
    private BillingService billingService;

    private MockedStatic<SecurityUtils> securityUtils;

    private Contract contract;
    private ExtraFeeCategory electricity;
    private ExtraFeeCategory water;

    @BeforeEach
    void setUp() {
        monthlyBillRepository = mock(MonthlyBillRepository.class);
        extraFeeItemRepository = mock(ExtraFeeItemRepository.class);
        extraFeeCategoryRepository = mock(ExtraFeeCategoryRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        contractRepository = mock(ContractRepository.class);
        branchRepository = mock(BranchRepository.class);
        meterReadingRepository = mock(MeterReadingRepository.class);
        utilityRateService = mock(UtilityRateService.class);
        entityManager = mock(EntityManager.class);

        billingService = new BillingService(monthlyBillRepository, extraFeeItemRepository, extraFeeCategoryRepository,
                paymentRepository, contractRepository, branchRepository, meterReadingRepository, utilityRateService, entityManager);

        Branch branch = new Branch();
        branch.setId(1L);

        Room room = new Room();
        room.setId(50L);
        room.setBranch(branch);
        room.setWifiFee(BigDecimal.ZERO);
        room.setParkingFee(BigDecimal.ZERO);

        contract = new Contract();
        contract.setId(500L);
        contract.setRoom(room);
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDate.of(2025, 1, 1));
        contract.setMonthlyRent(new BigDecimal("3000000"));

        electricity = new ExtraFeeCategory();
        electricity.setId(10L);
        electricity.setName("Điện");
        electricity.setUnit("kWh");
        electricity.setMetered(true);

        water = new ExtraFeeCategory();
        water.setId(11L);
        water.setName("Nước");
        water.setUnit("m3");
        water.setMetered(true);

        when(contractRepository.findById(500L)).thenReturn(Optional.of(contract));
        when(monthlyBillRepository.existsByContractIdAndBillYearAndBillMonth(500L, 2026, 3)).thenReturn(false);
        when(monthlyBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(extraFeeItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(extraFeeCategoryRepository.findByMeteredTrue()).thenReturn(List.of(electricity, water));

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void createBill_noReadingYet_seedsBothCategoriesAtZero() {
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 10L, 2026, 3))
                .thenReturn(Optional.empty());
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 11L, 2026, 3))
                .thenReturn(Optional.empty());

        billingService.createBill(500L, new MonthlyBillCreateRequest(3, 2026));

        ArgumentCaptor<ExtraFeeItem> captor = ArgumentCaptor.forClass(ExtraFeeItem.class);
        org.mockito.Mockito.verify(extraFeeItemRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        List<ExtraFeeItem> saved = captor.getAllValues();
        assertEquals(2, saved.size());
        assertEquals(BigDecimal.ZERO, saved.get(0).getAmount());
        assertEquals(BigDecimal.ZERO, saved.get(1).getAmount());
    }

    @Test
    void createBill_readingAndRatePresent_computesAmount() {
        MeterReading reading = new MeterReading();
        reading.setOldReading(new BigDecimal("100"));
        reading.setNewReading(new BigDecimal("125"));
        reading.setConsumption(new BigDecimal("25"));
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 10L, 2026, 3))
                .thenReturn(Optional.of(reading));
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 11L, 2026, 3))
                .thenReturn(Optional.empty());

        UtilityRate rate = new UtilityRate();
        rate.setUnitPrice(new BigDecimal("3500"));
        when(utilityRateService.findCurrentRate(1L, 10L, LocalDate.of(2026, 3, 1))).thenReturn(Optional.of(rate));
        when(utilityRateService.findCurrentRate(1L, 11L, LocalDate.of(2026, 3, 1))).thenReturn(Optional.empty());

        billingService.createBill(500L, new MonthlyBillCreateRequest(3, 2026));

        ArgumentCaptor<ExtraFeeItem> captor = ArgumentCaptor.forClass(ExtraFeeItem.class);
        org.mockito.Mockito.verify(extraFeeItemRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        ExtraFeeItem electricityItem = captor.getAllValues().stream()
                .filter(item -> item.getExtraFeeCategory().getId().equals(10L))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("87500"), electricityItem.getAmount());

        ExtraFeeItem waterItem = captor.getAllValues().stream()
                .filter(item -> item.getExtraFeeCategory().getId().equals(11L))
                .findFirst().orElseThrow();
        assertEquals(BigDecimal.ZERO, waterItem.getAmount());
    }
}

package com.rentalroom.management.service;

import com.rentalroom.management.dto.request.MeterReadingRequest;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.ExtraFeeCategory;
import com.rentalroom.management.entity.MeterReading;
import com.rentalroom.management.entity.MonthlyBill;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.UtilityRate;
import com.rentalroom.management.enums.BillSyncStatus;
import com.rentalroom.management.enums.PaymentStatus;
import com.rentalroom.management.enums.Role;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.ContractRepository;
import com.rentalroom.management.repository.ExtraFeeCategoryRepository;
import com.rentalroom.management.repository.MeterReadingRepository;
import com.rentalroom.management.repository.MonthlyBillRepository;
import com.rentalroom.management.repository.RoomRepository;
import com.rentalroom.management.security.SecurityUtils;
import com.rentalroom.management.security.UserPrincipal;
import jakarta.persistence.EntityManager;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeterReadingServiceTest {

    private MeterReadingRepository meterReadingRepository;
    private RoomRepository roomRepository;
    private ExtraFeeCategoryRepository extraFeeCategoryRepository;
    private ContractRepository contractRepository;
    private MonthlyBillRepository monthlyBillRepository;
    private UtilityRateService utilityRateService;
    private BillingService billingService;
    private EntityManager entityManager;
    private MeterReadingService meterReadingService;

    private MockedStatic<SecurityUtils> securityUtils;

    private Room room;
    private ExtraFeeCategory electricity;

    @BeforeEach
    void setUp() {
        meterReadingRepository = mock(MeterReadingRepository.class);
        roomRepository = mock(RoomRepository.class);
        extraFeeCategoryRepository = mock(ExtraFeeCategoryRepository.class);
        contractRepository = mock(ContractRepository.class);
        monthlyBillRepository = mock(MonthlyBillRepository.class);
        utilityRateService = mock(UtilityRateService.class);
        billingService = mock(BillingService.class);
        entityManager = mock(EntityManager.class);

        meterReadingService = new MeterReadingService(meterReadingRepository, roomRepository, extraFeeCategoryRepository,
                contractRepository, monthlyBillRepository, utilityRateService, billingService, entityManager);

        Branch branch = new Branch();
        branch.setId(1L);

        room = new Room();
        room.setId(50L);
        room.setBranch(branch);

        electricity = new ExtraFeeCategory();
        electricity.setId(10L);
        electricity.setName("Điện");
        electricity.setUnit("kWh");
        electricity.setMetered(true);

        when(roomRepository.findById(50L)).thenReturn(Optional.of(room));
        when(extraFeeCategoryRepository.findById(10L)).thenReturn(Optional.of(electricity));
        when(meterReadingRepository.save(any(MeterReading.class))).thenAnswer(inv -> inv.getArgument(0));
        when(billingService.syncMeteredExtraFeeItem(anyLong(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(BillSyncStatus.UPDATED);

        // Simulate the DB generated column: consumption = new - old, only visible after refresh in real Hibernate.
        doAnswer(inv -> {
            MeterReading reading = inv.getArgument(0);
            reading.setConsumption(reading.getNewReading().subtract(reading.getOldReading()));
            return null;
        }).when(entityManager).refresh(any());

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::currentUser)
                .thenReturn(new UserPrincipal(1L, "admin", Role.ADMIN_TONG, List.of()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void firstEverReading_withoutOldReading_throwsValidationError() {
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 10L, 2026, 3))
                .thenReturn(Optional.empty());
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdOrderByBillYearDescBillMonthDesc(50L, 10L))
                .thenReturn(List.of());

        MeterReadingRequest request = new MeterReadingRequest(10L, 3, 2026, null, new BigDecimal("120"), null);
        assertThrows(BusinessException.class, () -> meterReadingService.upsertReading(50L, request));
    }

    @Test
    void newReadingLessThanOldReading_throwsValidationError() {
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 10L, 2026, 3))
                .thenReturn(Optional.empty());
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdOrderByBillYearDescBillMonthDesc(50L, 10L))
                .thenReturn(List.of());

        MeterReadingRequest request = new MeterReadingRequest(10L, 3, 2026, new BigDecimal("100"), new BigDecimal("50"), null);
        assertThrows(BusinessException.class, () -> meterReadingService.upsertReading(50L, request));
    }

    @Test
    void oldReadingAutoChains_fromMostRecentPriorReading_skippingAMonth() {
        // January reading exists, February was skipped (nobody read the meter) — March must chain from January.
        MeterReading january = new MeterReading();
        january.setBillYear(2026);
        january.setBillMonth(1);
        january.setNewReading(new BigDecimal("100"));

        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 10L, 2026, 3))
                .thenReturn(Optional.empty());
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdOrderByBillYearDescBillMonthDesc(50L, 10L))
                .thenReturn(List.of(january));
        when(utilityRateService.findCurrentRate(eq(1L), eq(10L), any())).thenReturn(Optional.empty());

        MeterReadingRequest request = new MeterReadingRequest(10L, 3, 2026, null, new BigDecimal("150"), null);
        var response = assertDoesNotThrow(() -> meterReadingService.upsertReading(50L, request));

        assertEquals(new BigDecimal("100"), response.oldReading());
        assertEquals(new BigDecimal("50"), response.consumption());
    }

    @Test
    void rateConfigured_computesAmountAndSyncsBill() {
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 10L, 2026, 3))
                .thenReturn(Optional.empty());
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdOrderByBillYearDescBillMonthDesc(50L, 10L))
                .thenReturn(List.of());

        UtilityRate rate = new UtilityRate();
        rate.setUnitPrice(new BigDecimal("3500"));
        when(utilityRateService.findCurrentRate(eq(1L), eq(10L), any())).thenReturn(Optional.of(rate));

        MeterReadingRequest request = new MeterReadingRequest(10L, 3, 2026, new BigDecimal("100"), new BigDecimal("125"), null);
        var response = assertDoesNotThrow(() -> meterReadingService.upsertReading(50L, request));

        // consumption = 25 x 3500 = 87500
        assertEquals(new BigDecimal("87500"), response.amount());
        assertEquals(BillSyncStatus.UPDATED, response.billSyncStatus());
        verify(billingService).syncMeteredExtraFeeItem(eq(50L), eq(2026), eq(3), eq(electricity), eq(new BigDecimal("87500")), any());
    }

    @Test
    void noRateConfigured_amountZero_stillSavesReadingAndSyncsZero() {
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 10L, 2026, 3))
                .thenReturn(Optional.empty());
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdOrderByBillYearDescBillMonthDesc(50L, 10L))
                .thenReturn(List.of());
        when(utilityRateService.findCurrentRate(eq(1L), eq(10L), any())).thenReturn(Optional.empty());

        MeterReadingRequest request = new MeterReadingRequest(10L, 3, 2026, new BigDecimal("100"), new BigDecimal("125"), null);
        var response = assertDoesNotThrow(() -> meterReadingService.upsertReading(50L, request));

        assertEquals(BigDecimal.ZERO, response.amount());
        verify(billingService).syncMeteredExtraFeeItem(eq(50L), eq(2026), eq(3), eq(electricity), eq(BigDecimal.ZERO), any());
    }

    @Test
    void billAlreadyFullyPaid_rejectsReadingChange() {
        MonthlyBill paidBill = new MonthlyBill();
        paidBill.setPaymentStatus(PaymentStatus.DA_THANH_TOAN);
        when(monthlyBillRepository.findByContract_Room_IdAndBillYearAndBillMonth(50L, 2026, 3))
                .thenReturn(List.of(paidBill));

        MeterReadingRequest request = new MeterReadingRequest(10L, 3, 2026, new BigDecimal("100"), new BigDecimal("125"), null);
        assertThrows(BusinessException.class, () -> meterReadingService.upsertReading(50L, request));

        verify(meterReadingRepository, Mockito.never()).save(any());
    }

    @Test
    void billConfirmedButNotYetPaid_rejectsReadingChange() {
        // Confirming a bill locks it even before any payment is recorded — not just once fully paid.
        MonthlyBill confirmedBill = new MonthlyBill();
        confirmedBill.setPaymentStatus(PaymentStatus.CHUA_THANH_TOAN);
        when(monthlyBillRepository.findByContract_Room_IdAndBillYearAndBillMonth(50L, 2026, 3))
                .thenReturn(List.of(confirmedBill));

        MeterReadingRequest request = new MeterReadingRequest(10L, 3, 2026, new BigDecimal("100"), new BigDecimal("125"), null);
        assertThrows(BusinessException.class, () -> meterReadingService.upsertReading(50L, request));

        verify(meterReadingRepository, Mockito.never()).save(any());
    }

    @Test
    void billStillUnconfirmed_allowsReadingChange() {
        MonthlyBill unconfirmedBill = new MonthlyBill();
        unconfirmedBill.setPaymentStatus(PaymentStatus.CHUA_XAC_NHAN);
        when(monthlyBillRepository.findByContract_Room_IdAndBillYearAndBillMonth(50L, 2026, 3))
                .thenReturn(List.of(unconfirmedBill));
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(50L, 10L, 2026, 3))
                .thenReturn(Optional.empty());
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdOrderByBillYearDescBillMonthDesc(50L, 10L))
                .thenReturn(List.of());
        when(utilityRateService.findCurrentRate(eq(1L), eq(10L), any())).thenReturn(Optional.empty());

        MeterReadingRequest request = new MeterReadingRequest(10L, 3, 2026, new BigDecimal("100"), new BigDecimal("125"), null);
        assertDoesNotThrow(() -> meterReadingService.upsertReading(50L, request));

        verify(meterReadingRepository).save(any());
    }
}

package com.rentalroom.management.service;

import com.rentalroom.management.dto.response.TenantBillReminderRunResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.ContractTenant;
import com.rentalroom.management.entity.ExtraFeeCategory;
import com.rentalroom.management.entity.ExtraFeeItem;
import com.rentalroom.management.entity.MeterReading;
import com.rentalroom.management.entity.MonthlyBill;
import com.rentalroom.management.entity.Room;
import com.rentalroom.management.entity.Tenant;
import com.rentalroom.management.enums.PaymentStatus;
import com.rentalroom.management.repository.ContractTenantRepository;
import com.rentalroom.management.repository.MeterReadingRepository;
import com.rentalroom.management.repository.MonthlyBillRepository;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantBillReminderServiceTest {

    private MonthlyBillRepository monthlyBillRepository;
    private ContractTenantRepository contractTenantRepository;
    private MeterReadingRepository meterReadingRepository;
    private EmailService emailService;
    private TenantBillReminderService tenantBillReminderService;

    @BeforeEach
    void setUp() {
        monthlyBillRepository = mock(MonthlyBillRepository.class);
        contractTenantRepository = mock(ContractTenantRepository.class);
        meterReadingRepository = mock(MeterReadingRepository.class);
        emailService = mock(EmailService.class);
        tenantBillReminderService = new TenantBillReminderService(
                monthlyBillRepository, contractTenantRepository, meterReadingRepository, emailService);
    }

    private Branch branch(boolean withBankInfo) {
        Branch b = new Branch();
        b.setId(1L);
        b.setName("Chi nhánh 1");
        if (withBankInfo) {
            b.setBankBin("970436");
            b.setBankAccountNumber("0123456789");
            b.setBankAccountName("NGUYEN VAN A");
        }
        return b;
    }

    private Room room(Branch b) {
        Room r = new Room();
        r.setId(10L);
        r.setRoomCode("P101");
        r.setBranch(b);
        return r;
    }

    private Contract contract(Room r) {
        Contract c = new Contract();
        c.setId(100L);
        c.setRoom(r);
        return c;
    }

    private MonthlyBill bill(Contract c, PaymentStatus status, BigDecimal remaining) {
        MonthlyBill bill = new MonthlyBill();
        bill.setId(1000L);
        bill.setContract(c);
        bill.setBillYear(YearMonth.now().minusMonths(1).getYear());
        bill.setBillMonth(YearMonth.now().minusMonths(1).getMonthValue());
        bill.setRentAmount(new BigDecimal("3000000"));
        bill.setWifiFee(BigDecimal.ZERO);
        bill.setParkingFee(BigDecimal.ZERO);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setTotalAmount(new BigDecimal("3000000"));
        bill.setRemainingAmount(remaining);
        bill.setPaymentStatus(status);
        return bill;
    }

    private Tenant tenant(String email) {
        Tenant t = new Tenant();
        t.setId(500L);
        t.setFullName("Trần Văn B");
        t.setEmail(email);
        return t;
    }

    private ContractTenant representative(Contract c, Tenant t) {
        ContractTenant ct = new ContractTenant();
        ct.setContract(c);
        ct.setTenant(t);
        ct.setRepresentative(true);
        return ct;
    }

    @Test
    void run_noBillsFound_sendsNothing() throws MessagingException {
        when(monthlyBillRepository.findByBillYearAndBillMonth(anyInt(), anyInt())).thenReturn(List.of());

        TenantBillReminderRunResponse response = tenantBillReminderService.run();

        assertEquals(0, response.billsFound());
        assertEquals(0, response.emailsSent());
        assertEquals(0, response.skipped());
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void run_unconfirmedBill_isExcluded() throws MessagingException {
        Branch b = branch(false);
        Room r = room(b);
        Contract c = contract(r);
        MonthlyBill unconfirmed = bill(c, PaymentStatus.CHUA_XAC_NHAN, new BigDecimal("3000000"));
        when(monthlyBillRepository.findByBillYearAndBillMonth(anyInt(), anyInt())).thenReturn(List.of(unconfirmed));

        TenantBillReminderRunResponse response = tenantBillReminderService.run();

        assertEquals(0, response.billsFound());
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void run_fullyPaidBill_isExcluded() throws MessagingException {
        Branch b = branch(false);
        Room r = room(b);
        Contract c = contract(r);
        MonthlyBill paid = bill(c, PaymentStatus.DA_THANH_TOAN, BigDecimal.ZERO);
        when(monthlyBillRepository.findByBillYearAndBillMonth(anyInt(), anyInt())).thenReturn(List.of(paid));

        TenantBillReminderRunResponse response = tenantBillReminderService.run();

        assertEquals(0, response.billsFound());
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void run_noRepresentativeFound_skipsWithoutSending() throws MessagingException {
        Branch b = branch(false);
        Room r = room(b);
        Contract c = contract(r);
        MonthlyBill unpaid = bill(c, PaymentStatus.CHUA_THANH_TOAN, new BigDecimal("3000000"));
        when(monthlyBillRepository.findByBillYearAndBillMonth(anyInt(), anyInt())).thenReturn(List.of(unpaid));
        when(contractTenantRepository.findById_ContractIdAndRepresentativeTrue(100L)).thenReturn(Optional.empty());

        TenantBillReminderRunResponse response = tenantBillReminderService.run();

        assertEquals(1, response.billsFound());
        assertEquals(0, response.emailsSent());
        assertEquals(1, response.skipped());
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void run_representativeHasBlankEmail_skipsWithoutSending() throws MessagingException {
        Branch b = branch(false);
        Room r = room(b);
        Contract c = contract(r);
        MonthlyBill unpaid = bill(c, PaymentStatus.CHUA_THANH_TOAN, new BigDecimal("3000000"));
        Tenant t = tenant("  ");
        when(monthlyBillRepository.findByBillYearAndBillMonth(anyInt(), anyInt())).thenReturn(List.of(unpaid));
        when(contractTenantRepository.findById_ContractIdAndRepresentativeTrue(100L))
                .thenReturn(Optional.of(representative(c, t)));

        TenantBillReminderRunResponse response = tenantBillReminderService.run();

        assertEquals(0, response.emailsSent());
        assertEquals(1, response.skipped());
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void run_happyPath_withMeteredCategory_sendsEmailWithOldAndNewReadings() throws MessagingException {
        Branch b = branch(true);
        Room r = room(b);
        Contract c = contract(r);
        MonthlyBill unpaid = bill(c, PaymentStatus.THANH_TOAN_MOT_PHAN, new BigDecimal("1500000"));

        ExtraFeeCategory electricity = new ExtraFeeCategory();
        electricity.setId(1L);
        electricity.setName("Điện");
        electricity.setMetered(true);
        ExtraFeeItem electricityItem = new ExtraFeeItem();
        electricityItem.setMonthlyBill(unpaid);
        electricityItem.setExtraFeeCategory(electricity);
        electricityItem.setAmount(new BigDecimal("350000"));
        unpaid.getExtraFeeItems().add(electricityItem);

        MeterReading reading = new MeterReading();
        reading.setOldReading(new BigDecimal("100.00"));
        reading.setNewReading(new BigDecimal("200.00"));
        reading.setConsumption(new BigDecimal("100.00"));

        Tenant t = tenant("tenant@example.com");
        when(monthlyBillRepository.findByBillYearAndBillMonth(anyInt(), anyInt())).thenReturn(List.of(unpaid));
        when(contractTenantRepository.findById_ContractIdAndRepresentativeTrue(100L))
                .thenReturn(Optional.of(representative(c, t)));
        when(meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(
                eq(10L), eq(1L), anyInt(), anyInt())).thenReturn(Optional.of(reading));

        TenantBillReminderRunResponse response = tenantBillReminderService.run();

        assertEquals(1, response.billsFound());
        assertEquals(1, response.emailsSent());
        assertEquals(0, response.skipped());
        verify(emailService, times(1)).sendHtml(eq("tenant@example.com"), anyString(),
                contains("100.00"));
    }

    @Test
    void run_branchWithoutBankInfo_stillSendsWithoutQr() throws MessagingException {
        Branch b = branch(false);
        Room r = room(b);
        Contract c = contract(r);
        MonthlyBill unpaid = bill(c, PaymentStatus.CHUA_THANH_TOAN, new BigDecimal("3000000"));
        Tenant t = tenant("tenant@example.com");
        when(monthlyBillRepository.findByBillYearAndBillMonth(anyInt(), anyInt())).thenReturn(List.of(unpaid));
        when(contractTenantRepository.findById_ContractIdAndRepresentativeTrue(100L))
                .thenReturn(Optional.of(representative(c, t)));

        TenantBillReminderRunResponse response = tenantBillReminderService.run();

        assertEquals(1, response.emailsSent());
        verify(emailService, times(1)).sendHtml(eq("tenant@example.com"), anyString(), anyString());
    }

    @Test
    void run_emailSendFails_countsAsSkippedNotThrown() throws MessagingException {
        Branch b = branch(false);
        Room r = room(b);
        Contract c = contract(r);
        MonthlyBill unpaid = bill(c, PaymentStatus.CHUA_THANH_TOAN, new BigDecimal("3000000"));
        Tenant t = tenant("tenant@example.com");
        when(monthlyBillRepository.findByBillYearAndBillMonth(anyInt(), anyInt())).thenReturn(List.of(unpaid));
        when(contractTenantRepository.findById_ContractIdAndRepresentativeTrue(100L))
                .thenReturn(Optional.of(representative(c, t)));
        doThrow(new MessagingException("smtp down")).when(emailService).sendHtml(anyString(), anyString(), anyString());

        TenantBillReminderRunResponse response = tenantBillReminderService.run();

        assertEquals(0, response.emailsSent());
        assertEquals(1, response.skipped());
    }
}

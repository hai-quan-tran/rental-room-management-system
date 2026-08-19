package com.rentalroom.management.service;

import com.rentalroom.management.dto.response.InvoiceReminderRunResponse;
import com.rentalroom.management.entity.Account;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Employee;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.DashboardRepository;
import com.rentalroom.management.repository.EmployeeRepository;
import com.rentalroom.management.repository.projection.RoomInvoiceActionRow;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceReminderServiceTest {

    private DashboardRepository dashboardRepository;
    private BranchRepository branchRepository;
    private EmployeeRepository employeeRepository;
    private EmailService emailService;
    private InvoiceReminderService invoiceReminderService;

    @BeforeEach
    void setUp() {
        dashboardRepository = mock(DashboardRepository.class);
        branchRepository = mock(BranchRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        emailService = mock(EmailService.class);
        invoiceReminderService = new InvoiceReminderService(
                dashboardRepository, branchRepository, employeeRepository, emailService);
    }

    private RoomInvoiceActionRow row(long branchId, String reason) {
        RoomInvoiceActionRow r = mock(RoomInvoiceActionRow.class);
        when(r.getRoomId()).thenReturn(100L);
        when(r.getRoomCode()).thenReturn("P101");
        when(r.getBranchId()).thenReturn(branchId);
        when(r.getBranchName()).thenReturn("Chi nhánh 1");
        when(r.getReason()).thenReturn(reason);
        return r;
    }

    @Test
    void run_noRoomsFlagged_sendsNothing() throws MessagingException {
        when(dashboardRepository.roomsNeedingInvoiceAction(anyInt(), anyInt())).thenReturn(List.of());

        InvoiceReminderRunResponse response = invoiceReminderService.run();

        assertEquals(0, response.roomsFlagged());
        assertEquals(0, response.branchesNotified());
        assertEquals(0, response.branchesSkipped());
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void run_branchHasNoManagerAccount_skipsWithoutSending() throws MessagingException {
        RoomInvoiceActionRow flaggedRow = row(1L, "MISSING");
        when(dashboardRepository.roomsNeedingInvoiceAction(anyInt(), anyInt())).thenReturn(List.of(flaggedRow));
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setManagerAccount(null);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));

        InvoiceReminderRunResponse response = invoiceReminderService.run();

        assertEquals(0, response.branchesNotified());
        assertEquals(1, response.branchesSkipped());
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void run_managerAccountHasNoLinkedEmployee_skipsWithoutSending() throws MessagingException {
        RoomInvoiceActionRow flaggedRow = row(1L, "MISSING");
        when(dashboardRepository.roomsNeedingInvoiceAction(anyInt(), anyInt())).thenReturn(List.of(flaggedRow));
        Account managerAccount = new Account();
        managerAccount.setId(50L);
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setManagerAccount(managerAccount);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(employeeRepository.findByAccountId(50L)).thenReturn(Optional.empty());

        InvoiceReminderRunResponse response = invoiceReminderService.run();

        assertEquals(0, response.branchesNotified());
        assertEquals(1, response.branchesSkipped());
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void run_managerEmployeeHasBlankEmail_skipsWithoutSending() throws MessagingException {
        RoomInvoiceActionRow flaggedRow = row(1L, "MISSING");
        when(dashboardRepository.roomsNeedingInvoiceAction(anyInt(), anyInt())).thenReturn(List.of(flaggedRow));
        Account managerAccount = new Account();
        managerAccount.setId(50L);
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setManagerAccount(managerAccount);
        Employee manager = new Employee();
        manager.setEmail("  ");
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(employeeRepository.findByAccountId(50L)).thenReturn(Optional.of(manager));

        InvoiceReminderRunResponse response = invoiceReminderService.run();

        assertEquals(0, response.branchesNotified());
        assertEquals(1, response.branchesSkipped());
        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void run_happyPath_sendsOneEmailPerBranch() throws MessagingException {
        RoomInvoiceActionRow missingRow = row(1L, "MISSING");
        RoomInvoiceActionRow unconfirmedRow = row(1L, "UNCONFIRMED");
        when(dashboardRepository.roomsNeedingInvoiceAction(anyInt(), anyInt()))
                .thenReturn(List.of(missingRow, unconfirmedRow));
        Account managerAccount = new Account();
        managerAccount.setId(50L);
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setName("Chi nhánh 1");
        branch.setManagerAccount(managerAccount);
        Employee manager = new Employee();
        manager.setEmail("manager@example.com");
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(employeeRepository.findByAccountId(50L)).thenReturn(Optional.of(manager));

        YearMonth expected = YearMonth.now().minusMonths(1);
        InvoiceReminderRunResponse response = invoiceReminderService.run();

        assertEquals(expected.getYear(), response.targetYear());
        assertEquals(expected.getMonthValue(), response.targetMonth());
        assertEquals(2, response.roomsFlagged());
        assertEquals(1, response.branchesNotified());
        assertEquals(0, response.branchesSkipped());
        verify(emailService, times(1)).sendHtml(eq("manager@example.com"), anyString(), anyString());
    }

    @Test
    void run_emailSendFails_countsAsSkippedNotThrown() throws MessagingException {
        RoomInvoiceActionRow flaggedRow = row(1L, "MISSING");
        when(dashboardRepository.roomsNeedingInvoiceAction(anyInt(), anyInt())).thenReturn(List.of(flaggedRow));
        Account managerAccount = new Account();
        managerAccount.setId(50L);
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setManagerAccount(managerAccount);
        Employee manager = new Employee();
        manager.setEmail("manager@example.com");
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(employeeRepository.findByAccountId(50L)).thenReturn(Optional.of(manager));
        doThrow(new MessagingException("smtp down")).when(emailService).sendHtml(anyString(), anyString(), anyString());

        InvoiceReminderRunResponse response = invoiceReminderService.run();

        assertEquals(0, response.branchesNotified());
        assertEquals(1, response.branchesSkipped());
    }
}

package com.rentalroom.management.service;

import com.rentalroom.management.dto.response.InvoiceReminderRunResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Employee;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.DashboardRepository;
import com.rentalroom.management.repository.EmployeeRepository;
import com.rentalroom.management.repository.projection.RoomInvoiceActionRow;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Emails each branch's manager (via {@code Branch.managerAccount -> Employee.email}) a reminder
 * listing that branch's currently-occupied rooms whose bill for last month is either missing or
 * still unconfirmed. Stateless by design (no "already sent" tracking) — every run (scheduled or
 * manual) re-queries current state, so a branch with nothing left to report simply gets no email.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class InvoiceReminderService {

    private final DashboardRepository dashboardRepository;
    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailService emailService;

    public InvoiceReminderService(DashboardRepository dashboardRepository, BranchRepository branchRepository,
                                   EmployeeRepository employeeRepository, EmailService emailService) {
        this.dashboardRepository = dashboardRepository;
        this.branchRepository = branchRepository;
        this.employeeRepository = employeeRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.invoice-reminder.cron:0 0 8 7-9 * *}")
    public void runScheduled() {
        run();
    }

    public InvoiceReminderRunResponse run() {
        YearMonth target = YearMonth.now().minusMonths(1);
        List<RoomInvoiceActionRow> rows = dashboardRepository.roomsNeedingInvoiceAction(
                target.getYear(), target.getMonthValue());

        if (rows.isEmpty()) {
            log.info("Invoice reminder {}/{}: no rooms flagged, nothing to send", target.getMonthValue(), target.getYear());
            return new InvoiceReminderRunResponse(target.getYear(), target.getMonthValue(), 0, 0, 0);
        }

        Map<Long, List<RoomInvoiceActionRow>> byBranch = rows.stream()
                .collect(Collectors.groupingBy(RoomInvoiceActionRow::getBranchId));

        int branchesNotified = 0;
        int branchesSkipped = 0;
        for (Map.Entry<Long, List<RoomInvoiceActionRow>> entry : byBranch.entrySet()) {
            if (notifyBranch(entry.getKey(), entry.getValue(), target)) {
                branchesNotified++;
            } else {
                branchesSkipped++;
            }
        }

        return new InvoiceReminderRunResponse(
                target.getYear(), target.getMonthValue(), rows.size(), branchesNotified, branchesSkipped);
    }

    private boolean notifyBranch(Long branchId, List<RoomInvoiceActionRow> rooms, YearMonth target) {
        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null || branch.getManagerAccount() == null) {
            log.warn("Invoice reminder: branch {} has no manager account, skipping", branchId);
            return false;
        }

        Employee manager = employeeRepository.findByAccountId(branch.getManagerAccount().getId()).orElse(null);
        if (manager == null || manager.getEmail() == null || manager.getEmail().isBlank()) {
            log.warn("Invoice reminder: branch {} manager account {} has no linked employee email, skipping",
                    branchId, branch.getManagerAccount().getId());
            return false;
        }

        try {
            String subject = "Nhắc xác nhận hóa đơn tháng %d/%d - %s"
                    .formatted(target.getMonthValue(), target.getYear(), branch.getName());
            emailService.sendHtml(manager.getEmail(), subject, buildBody(branch.getName(), rooms, target));
            log.info("Invoice reminder sent to {} for branch {} ({} rooms)", manager.getEmail(), branchId, rooms.size());
            return true;
        } catch (MessagingException e) {
            log.error("Invoice reminder: failed to send email for branch {}", branchId, e);
            return false;
        }
    }

    private String buildBody(String branchName, List<RoomInvoiceActionRow> rooms, YearMonth target) {
        StringBuilder rowsHtml = new StringBuilder();
        for (RoomInvoiceActionRow row : rooms) {
            String reasonText = "MISSING".equals(row.getReason()) ? "Thiếu hóa đơn" : "Chưa xác nhận hóa đơn";
            rowsHtml.append("<li>").append(row.getRoomCode()).append(" — ").append(reasonText).append("</li>");
        }
        return "<p>Chi nhánh <b>" + branchName + "</b> có các phòng sau cần tạo/xác nhận hóa đơn tháng "
                + target.getMonthValue() + "/" + target.getYear() + ":</p>"
                + "<ul>" + rowsHtml + "</ul>";
    }
}

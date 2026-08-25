package com.rentalroom.management.service;

import com.rentalroom.management.common.util.VietQrUtil;
import com.rentalroom.management.dto.response.TenantBillReminderRunResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.entity.ContractTenant;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Emails each overdue bill's contract representative (via {@code ContractTenant.representative}) a
 * detailed breakdown of last month's already-confirmed, still-unpaid bill — one line per cost item,
 * with old/new meter readings spelled out for metered categories (Điện, Nước), plus a VietQR payment
 * image when the room's branch has bank info configured. Stateless by design (no "already sent"
 * tracking, same convention as {@link InvoiceReminderService}) — every run re-queries current state.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class TenantBillReminderService {

    private final MonthlyBillRepository monthlyBillRepository;
    private final ContractTenantRepository contractTenantRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final EmailService emailService;

    public TenantBillReminderService(MonthlyBillRepository monthlyBillRepository,
                                      ContractTenantRepository contractTenantRepository,
                                      MeterReadingRepository meterReadingRepository,
                                      EmailService emailService) {
        this.monthlyBillRepository = monthlyBillRepository;
        this.contractTenantRepository = contractTenantRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.tenant-bill-reminder.cron:0 0 8 11 * *}")
    public void runScheduled() {
        run();
    }

    public TenantBillReminderRunResponse run() {
        YearMonth target = YearMonth.now().minusMonths(1);
        List<MonthlyBill> bills = monthlyBillRepository
                .findByBillYearAndBillMonth(target.getYear(), target.getMonthValue())
                .stream()
                .filter(bill -> bill.getPaymentStatus() != PaymentStatus.CHUA_XAC_NHAN
                        && bill.getRemainingAmount() != null
                        && bill.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (bills.isEmpty()) {
            log.info("Tenant bill reminder {}/{}: no overdue confirmed bills, nothing to send",
                    target.getMonthValue(), target.getYear());
            return new TenantBillReminderRunResponse(target.getYear(), target.getMonthValue(), 0, 0, 0);
        }

        int emailsSent = 0;
        int skipped = 0;
        for (MonthlyBill bill : bills) {
            if (notifyRepresentative(bill, target)) {
                emailsSent++;
            } else {
                skipped++;
            }
        }

        return new TenantBillReminderRunResponse(target.getYear(), target.getMonthValue(), bills.size(), emailsSent, skipped);
    }

    private boolean notifyRepresentative(MonthlyBill bill, YearMonth target) {
        Contract contract = bill.getContract();
        Room room = contract.getRoom();
        Branch branch = room.getBranch();

        Optional<ContractTenant> representative = contractTenantRepository
                .findById_ContractIdAndRepresentativeTrue(contract.getId());
        if (representative.isEmpty()) {
            log.warn("Tenant bill reminder: contract {} has no representative, skipping bill {}", contract.getId(), bill.getId());
            return false;
        }
        Tenant tenant = representative.get().getTenant();
        if (tenant.getEmail() == null || tenant.getEmail().isBlank()) {
            log.warn("Tenant bill reminder: contract {} representative (tenant {}) has no email, skipping bill {}",
                    contract.getId(), tenant.getId(), bill.getId());
            return false;
        }

        try {
            String subject = "Nhắc thanh toán hóa đơn tháng %d/%d - Phòng %s"
                    .formatted(target.getMonthValue(), target.getYear(), room.getRoomCode());
            emailService.sendHtml(tenant.getEmail(), subject, buildBody(tenant, room, branch, bill, target));
            log.info("Tenant bill reminder sent to {} for bill {} (room {})", tenant.getEmail(), bill.getId(), room.getRoomCode());
            return true;
        } catch (MessagingException e) {
            log.error("Tenant bill reminder: failed to send email for bill {}", bill.getId(), e);
            return false;
        }
    }

    private String buildBody(Tenant tenant, Room room, Branch branch, MonthlyBill bill, YearMonth target) {
        StringBuilder rowsHtml = new StringBuilder();
        appendRow(rowsHtml, "Tiền phòng", null, bill.getRentAmount());
        if (bill.getWifiFee().compareTo(BigDecimal.ZERO) > 0) {
            appendRow(rowsHtml, "Wifi", null, bill.getWifiFee());
        }
        if (bill.getParkingFee().compareTo(BigDecimal.ZERO) > 0) {
            appendRow(rowsHtml, "Giữ xe", null, bill.getParkingFee());
        }
        for (ExtraFeeItem item : bill.getExtraFeeItems()) {
            MeterReading reading = item.getExtraFeeCategory().isMetered()
                    ? meterReadingRepository.findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(
                            room.getId(), item.getExtraFeeCategory().getId(), bill.getBillYear(), bill.getBillMonth())
                            .orElse(null)
                    : null;
            appendRow(rowsHtml, item.getExtraFeeCategory().getName(), reading, item.getAmount());
        }

        String addInfo = "RRMS %s T%d.%d".formatted(room.getRoomCode(), bill.getBillMonth(), bill.getBillYear());
        String qrUrl = VietQrUtil.buildImageUrl(branch.getBankBin(), branch.getBankAccountNumber(),
                bill.getRemainingAmount(), addInfo, branch.getBankAccountName());

        StringBuilder html = new StringBuilder();
        html.append("<p>Kính gửi <b>").append(tenant.getFullName()).append("</b>,</p>")
                .append("<p>Phòng <b>").append(room.getRoomCode()).append("</b> - ").append(branch.getName())
                .append(" còn hóa đơn tháng ").append(target.getMonthValue()).append('/').append(target.getYear())
                .append(" chưa thanh toán đủ, chi tiết như sau:</p>")
                .append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse\">")
                .append("<tr><th>Khoản mục</th><th>Số cũ</th><th>Số mới</th><th>Tiêu thụ</th><th>Thành tiền</th></tr>")
                .append(rowsHtml)
                .append("</table>")
                .append("<p>Tổng cộng: <b>").append(format(bill.getTotalAmount())).append("</b> đ<br/>")
                .append("Đã thanh toán: ").append(format(bill.getPaidAmount())).append(" đ<br/>")
                .append("Còn lại: <b style=\"color:#c0392b\">").append(format(bill.getRemainingAmount())).append(" đ</b></p>");

        if (qrUrl != null) {
            html.append("<p>Quét mã sau để chuyển khoản nhanh (nội dung: ").append(addInfo).append("):</p>")
                    .append("<img src=\"").append(qrUrl).append("\" alt=\"VietQR\" width=\"200\" />");
        }

        html.append("<p><i>Nếu quý khách đã thanh toán, vui lòng bỏ qua email này.</i></p>");
        return html.toString();
    }

    private void appendRow(StringBuilder rowsHtml, String label, MeterReading reading, BigDecimal amount) {
        rowsHtml.append("<tr><td>").append(label).append("</td>");
        if (reading != null) {
            rowsHtml.append("<td>").append(format(reading.getOldReading())).append("</td>")
                    .append("<td>").append(format(reading.getNewReading())).append("</td>")
                    .append("<td>").append(format(reading.getConsumption())).append("</td>");
        } else {
            rowsHtml.append("<td>-</td><td>-</td><td>-</td>");
        }
        rowsHtml.append("<td>").append(format(amount)).append(" đ</td></tr>");
    }

    private String format(BigDecimal value) {
        return value == null ? "-" : value.toPlainString();
    }
}

package com.rentalroom.management.service;

import com.rentalroom.management.config.RedisConfig;
import com.rentalroom.management.dto.response.DashboardResponse;
import com.rentalroom.management.dto.response.MissingInvoiceRoomResponse;
import com.rentalroom.management.dto.response.MoveInOutPointResponse;
import com.rentalroom.management.dto.response.OccupantsByBranchResponse;
import com.rentalroom.management.dto.response.RevenuePointResponse;
import com.rentalroom.management.dto.response.RoomStatusChartResponse;
import com.rentalroom.management.dto.response.UnpaidInvoiceRoomResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.enums.RoomStatus;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.exception.ErrorCode;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.DashboardRepository;
import com.rentalroom.management.repository.projection.ActiveContractRoomRow;
import com.rentalroom.management.repository.projection.ContractBillPeriod;
import com.rentalroom.management.repository.projection.MonthCount;
import com.rentalroom.management.repository.projection.MonthRevenue;
import com.rentalroom.management.security.SecurityUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** Default chart range when {@code from}/{@code to} aren't supplied: the current month and 6 back. */
    private static final int DEFAULT_CHART_MONTHS_BACK = 6;

    /** How many recent months to check for missing bills — not a full history audit, just a catch-up window. */
    private static final int MISSING_INVOICE_LOOKBACK_MONTHS = 6;

    private final DashboardRepository dashboardRepository;
    private final BranchRepository branchRepository;

    public DashboardService(DashboardRepository dashboardRepository, BranchRepository branchRepository) {
        this.dashboardRepository = dashboardRepository;
        this.branchRepository = branchRepository;
    }

    /**
     * {@code branchId} is only honored for ADMIN_TONG ("Tất cả chi nhánh" when null). ADMIN_CAP_1
     * always gets its own managed branches from the JWT — the client-supplied value is ignored,
     * per spec, to prevent viewing another branch's data.
     *
     * {@code fromYear}/{@code fromMonth}/{@code toYear}/{@code toMonth} bound the move-in/move-out
     * and revenue charts; any of them missing falls back to the default range (current month and
     * {@value #DEFAULT_CHART_MONTHS_BACK} months back).
     */
    @Cacheable(cacheNames = RedisConfig.CACHE_DASHBOARD,
            key = "(#branchId != null ? #branchId : 'ALL') + ':' + #fromYear + '-' + #fromMonth"
                    + " + ':' + #toYear + '-' + #toMonth"
                    + " + ':' + T(com.rentalroom.management.security.SecurityUtils).currentUser().userId()")
    public DashboardResponse getDashboard(Long branchId, Integer fromYear, Integer fromMonth,
                                           Integer toYear, Integer toMonth) {
        List<Long> branchIds = SecurityUtils.resolveBranchScope(
                branchId, () -> branchRepository.findAll().stream().map(Branch::getId).toList());

        if (branchIds.isEmpty()) {
            return new DashboardResponse(new RoomStatusChartResponse(0, 0), List.of(), List.of(),
                    List.of(), List.of(), List.of());
        }

        YearMonth to = (toYear != null && toMonth != null) ? YearMonth.of(toYear, toMonth) : YearMonth.now();
        YearMonth from = (fromYear != null && fromMonth != null)
                ? YearMonth.of(fromYear, fromMonth)
                : to.minusMonths(DEFAULT_CHART_MONTHS_BACK);
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Từ tháng phải trước hoặc bằng Đến tháng");
        }
        List<YearMonth> months = monthsBetween(from, to);

        return new DashboardResponse(
                roomStatusChart(branchIds),
                moveInOutChart(branchIds, months),
                revenueChart(branchIds, months),
                occupantsByBranch(branchIds),
                missingInvoiceRooms(branchIds),
                unpaidInvoiceRooms(branchIds));
    }

    private RoomStatusChartResponse roomStatusChart(List<Long> branchIds) {
        Map<String, Long> counts = dashboardRepository.countRoomsByStatus(branchIds).stream()
                .collect(Collectors.toMap(row -> row.getStatus(), row -> row.getCnt()));
        return new RoomStatusChartResponse(
                counts.getOrDefault(RoomStatus.TRONG.name(), 0L),
                counts.getOrDefault(RoomStatus.DANG_THUE.name(), 0L)
        );
    }

    private List<MoveInOutPointResponse> moveInOutChart(List<Long> branchIds, List<YearMonth> months) {
        var fromDate = months.get(0).atDay(1);
        var toDateExclusive = months.get(months.size() - 1).plusMonths(1).atDay(1);

        Map<YearMonth, Long> moveIns = toMonthMap(
                dashboardRepository.countMoveInsByMonth(branchIds, fromDate, toDateExclusive), MonthCount::getCnt);
        Map<YearMonth, Long> moveOuts = toMonthMap(
                dashboardRepository.countMoveOutsByMonth(branchIds, fromDate, toDateExclusive), MonthCount::getCnt);

        return months.stream()
                .map(ym -> new MoveInOutPointResponse(
                        ym.getYear(), ym.getMonthValue(),
                        moveIns.getOrDefault(ym, 0L),
                        moveOuts.getOrDefault(ym, 0L)))
                .toList();
    }

    private List<RevenuePointResponse> revenueChart(List<Long> branchIds, List<YearMonth> months) {
        var fromDate = months.get(0).atDay(1);
        var toDateExclusive = months.get(months.size() - 1).plusMonths(1).atDay(1);

        Map<YearMonth, BigDecimal> revenue = dashboardRepository.revenueByMonth(branchIds, fromDate, toDateExclusive)
                .stream()
                .collect(Collectors.toMap(row -> YearMonth.of(row.getYr(), row.getMo()), MonthRevenue::getTotal));

        return months.stream()
                .map(ym -> new RevenuePointResponse(ym.getYear(), ym.getMonthValue(), revenue.getOrDefault(ym, BigDecimal.ZERO)))
                .toList();
    }

    private List<OccupantsByBranchResponse> occupantsByBranch(List<Long> branchIds) {
        return dashboardRepository.occupantsByBranch(branchIds).stream()
                .map(row -> new OccupantsByBranchResponse(row.getBranchId(), row.getBranchName(), row.getOccupantCount()))
                .toList();
    }

    /**
     * For every ACTIVE contract's room, flags each month in the lookback window (clipped to the
     * contract's start month) that has no matching {@code monthly_bill} row — not just the most
     * recently expected one, so a room missing 2+ consecutive months shows each of them.
     */
    private List<MissingInvoiceRoomResponse> missingInvoiceRooms(List<Long> branchIds) {
        List<ActiveContractRoomRow> contracts = dashboardRepository.findActiveContractRooms(branchIds);
        if (contracts.isEmpty()) {
            return List.of();
        }

        YearMonth expected = YearMonth.now().minusMonths(1);
        YearMonth windowStart = expected.minusMonths(MISSING_INVOICE_LOOKBACK_MONTHS - 1L);

        List<Long> contractIds = contracts.stream().map(ActiveContractRoomRow::getContractId).toList();
        Map<Long, Set<YearMonth>> billsByContract = dashboardRepository.findBillPeriods(contractIds).stream()
                .collect(Collectors.groupingBy(ContractBillPeriod::getContractId,
                        Collectors.mapping(row -> YearMonth.of(row.getBillYear(), row.getBillMonth()), Collectors.toSet())));

        List<MissingInvoiceRoomResponse> result = new ArrayList<>();
        for (ActiveContractRoomRow row : contracts) {
            YearMonth contractStart = YearMonth.of(row.getStartYear(), row.getStartMonth());
            YearMonth from = windowStart.isAfter(contractStart) ? windowStart : contractStart;
            if (from.isAfter(expected)) {
                continue;
            }
            Set<YearMonth> existing = billsByContract.getOrDefault(row.getContractId(), Set.of());
            for (YearMonth ym = from; !ym.isAfter(expected); ym = ym.plusMonths(1)) {
                if (!existing.contains(ym)) {
                    result.add(new MissingInvoiceRoomResponse(
                            row.getRoomId(), row.getRoomCode(), row.getBranchId(), row.getBranchName(),
                            ym.getYear(), ym.getMonthValue()));
                }
            }
        }
        return result;
    }

    private List<UnpaidInvoiceRoomResponse> unpaidInvoiceRooms(List<Long> branchIds) {
        return dashboardRepository.unpaidInvoiceRooms(branchIds).stream()
                .map(row -> new UnpaidInvoiceRoomResponse(
                        row.getRoomId(), row.getRoomCode(), row.getBranchId(), row.getBranchName(),
                        row.getBillYear(), row.getBillMonth(),
                        row.getTotalAmount(), row.getPaidAmount(), row.getRemainingAmount()))
                .toList();
    }

    private List<YearMonth> monthsBetween(YearMonth from, YearMonth to) {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            months.add(ym);
        }
        return months;
    }

    private <T> Map<YearMonth, T> toMonthMap(List<? extends MonthCount> rows, Function<MonthCount, T> valueFn) {
        return rows.stream().collect(Collectors.toMap(row -> YearMonth.of(row.getYr(), row.getMo()), valueFn));
    }
}

package com.rentalroom.management.service;

import com.rentalroom.management.config.RedisConfig;
import com.rentalroom.management.dto.response.DashboardResponse;
import com.rentalroom.management.dto.response.MoveInOutPointResponse;
import com.rentalroom.management.dto.response.RevenuePointResponse;
import com.rentalroom.management.dto.response.RoomStatusChartResponse;
import com.rentalroom.management.entity.Branch;
import com.rentalroom.management.enums.RoomStatus;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.DashboardRepository;
import com.rentalroom.management.repository.projection.MonthCount;
import com.rentalroom.management.repository.projection.MonthRevenue;
import com.rentalroom.management.security.SecurityUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int MONTHS_BACK = 11;

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
     */
    @Cacheable(cacheNames = RedisConfig.CACHE_DASHBOARD,
            key = "(#branchId != null ? #branchId : 'ALL') + ':' + T(com.rentalroom.management.security.SecurityUtils).currentUser().userId()")
    public DashboardResponse getDashboard(Long branchId) {
        List<Long> branchIds = SecurityUtils.resolveBranchScope(
                branchId, () -> branchRepository.findAll().stream().map(Branch::getId).toList());

        if (branchIds.isEmpty()) {
            return new DashboardResponse(new RoomStatusChartResponse(0, 0), List.of(), List.of());
        }

        return new DashboardResponse(roomStatusChart(branchIds), moveInOutChart(branchIds), revenueChart(branchIds));
    }

    private RoomStatusChartResponse roomStatusChart(List<Long> branchIds) {
        Map<String, Long> counts = dashboardRepository.countRoomsByStatus(branchIds).stream()
                .collect(Collectors.toMap(row -> row.getStatus(), row -> row.getCnt()));
        return new RoomStatusChartResponse(
                counts.getOrDefault(RoomStatus.TRONG.name(), 0L),
                counts.getOrDefault(RoomStatus.DANG_THUE.name(), 0L)
        );
    }

    private List<MoveInOutPointResponse> moveInOutChart(List<Long> branchIds) {
        List<YearMonth> months = lastMonths();
        var fromDate = months.get(0).atDay(1);

        Map<YearMonth, Long> moveIns = toMonthMap(dashboardRepository.countMoveInsByMonth(branchIds, fromDate), MonthCount::getCnt);
        Map<YearMonth, Long> moveOuts = toMonthMap(dashboardRepository.countMoveOutsByMonth(branchIds, fromDate), MonthCount::getCnt);

        return months.stream()
                .map(ym -> new MoveInOutPointResponse(
                        ym.getYear(), ym.getMonthValue(),
                        moveIns.getOrDefault(ym, 0L),
                        moveOuts.getOrDefault(ym, 0L)))
                .toList();
    }

    private List<RevenuePointResponse> revenueChart(List<Long> branchIds) {
        List<YearMonth> months = lastMonths();
        var fromDate = months.get(0).atDay(1);

        Map<YearMonth, BigDecimal> revenue = dashboardRepository.revenueByMonth(branchIds, fromDate).stream()
                .collect(Collectors.toMap(row -> YearMonth.of(row.getYr(), row.getMo()), MonthRevenue::getTotal));

        return months.stream()
                .map(ym -> new RevenuePointResponse(ym.getYear(), ym.getMonthValue(), revenue.getOrDefault(ym, BigDecimal.ZERO)))
                .toList();
    }

    private List<YearMonth> lastMonths() {
        YearMonth current = YearMonth.now();
        return IntStream.rangeClosed(0, MONTHS_BACK)
                .mapToObj(i -> current.minusMonths(MONTHS_BACK - i))
                .toList();
    }

    private <T> Map<YearMonth, T> toMonthMap(List<? extends MonthCount> rows, Function<MonthCount, T> valueFn) {
        return rows.stream().collect(Collectors.toMap(row -> YearMonth.of(row.getYr(), row.getMo()), valueFn));
    }
}

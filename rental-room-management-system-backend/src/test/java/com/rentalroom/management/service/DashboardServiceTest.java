package com.rentalroom.management.service;

import com.rentalroom.management.dto.response.DashboardResponse;
import com.rentalroom.management.exception.BusinessException;
import com.rentalroom.management.repository.BranchRepository;
import com.rentalroom.management.repository.DashboardRepository;
import com.rentalroom.management.repository.projection.ActiveContractRoomRow;
import com.rentalroom.management.repository.projection.ContractBillPeriod;
import com.rentalroom.management.repository.projection.MonthCount;
import com.rentalroom.management.repository.projection.MonthRevenue;
import com.rentalroom.management.repository.projection.OccupantsByBranchRow;
import com.rentalroom.management.repository.projection.RoomStatusCount;
import com.rentalroom.management.repository.projection.UnpaidInvoiceRoomRow;
import com.rentalroom.management.security.SecurityUtils;
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
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private DashboardRepository dashboardRepository;
    private BranchRepository branchRepository;
    private DashboardService dashboardService;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        dashboardRepository = mock(DashboardRepository.class);
        branchRepository = mock(BranchRepository.class);
        dashboardService = new DashboardService(dashboardRepository, branchRepository);

        securityUtils = Mockito.mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void getDashboard_emptyBranchScope_returnsEmptyDashboardWithoutQuerying() {
        securityUtils.when(() -> SecurityUtils.resolveBranchScope(any(), any())).thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboard(null, null, null, null, null);

        assertEquals(0, response.roomStatus().emptyCount());
        assertEquals(0, response.roomStatus().occupiedCount());
        assertTrue(response.moveInOut().isEmpty());
        assertTrue(response.revenue().isEmpty());
        assertTrue(response.occupantsByBranch().isEmpty());
        assertTrue(response.missingInvoiceRooms().isEmpty());
        assertTrue(response.unpaidInvoiceRooms().isEmpty());
    }

    @Test
    void getDashboard_fromAfterTo_throwsValidationError() {
        securityUtils.when(() -> SecurityUtils.resolveBranchScope(any(), any())).thenReturn(List.of(1L));

        assertThrows(BusinessException.class,
                () -> dashboardService.getDashboard(1L, 2026, 6, 2026, 3));
    }

    @Test
    void getDashboard_populatedRange_aggregatesAllCharts() {
        securityUtils.when(() -> SecurityUtils.resolveBranchScope(any(), any())).thenReturn(List.of(1L));

        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDateExclusive = LocalDate.of(2026, 4, 1);

        RoomStatusCount trongRow = mock(RoomStatusCount.class);
        when(trongRow.getStatus()).thenReturn("TRONG");
        when(trongRow.getCnt()).thenReturn(2L);
        RoomStatusCount occupiedRow = mock(RoomStatusCount.class);
        when(occupiedRow.getStatus()).thenReturn("DANG_THUE");
        when(occupiedRow.getCnt()).thenReturn(5L);
        when(dashboardRepository.countRoomsByStatus(List.of(1L))).thenReturn(List.of(trongRow, occupiedRow));

        MonthCount moveInRow = mock(MonthCount.class);
        when(moveInRow.getYr()).thenReturn(2026);
        when(moveInRow.getMo()).thenReturn(1);
        when(moveInRow.getCnt()).thenReturn(3L);
        when(dashboardRepository.countMoveInsByMonth(eq(List.of(1L)), eq(fromDate), eq(toDateExclusive)))
                .thenReturn(List.of(moveInRow));

        MonthCount moveOutRow = mock(MonthCount.class);
        when(moveOutRow.getYr()).thenReturn(2026);
        when(moveOutRow.getMo()).thenReturn(2);
        when(moveOutRow.getCnt()).thenReturn(1L);
        when(dashboardRepository.countMoveOutsByMonth(eq(List.of(1L)), eq(fromDate), eq(toDateExclusive)))
                .thenReturn(List.of(moveOutRow));

        MonthRevenue revenueRow = mock(MonthRevenue.class);
        when(revenueRow.getYr()).thenReturn(2026);
        when(revenueRow.getMo()).thenReturn(2);
        when(revenueRow.getTotal()).thenReturn(new BigDecimal("5000000"));
        when(dashboardRepository.revenueByMonth(eq(List.of(1L)), eq(fromDate), eq(toDateExclusive)))
                .thenReturn(List.of(revenueRow));

        OccupantsByBranchRow occRow = mock(OccupantsByBranchRow.class);
        when(occRow.getBranchId()).thenReturn(1L);
        when(occRow.getBranchName()).thenReturn("Chi nhánh 1");
        when(occRow.getOccupantCount()).thenReturn(10L);
        when(dashboardRepository.occupantsByBranch(List.of(1L))).thenReturn(List.of(occRow));

        YearMonth expected = YearMonth.now().minusMonths(1);
        YearMonth windowStart = expected.minusMonths(5);

        ActiveContractRoomRow contractRow = mock(ActiveContractRoomRow.class);
        when(contractRow.getContractId()).thenReturn(900L);
        when(contractRow.getRoomId()).thenReturn(50L);
        when(contractRow.getRoomCode()).thenReturn("P101");
        when(contractRow.getBranchId()).thenReturn(1L);
        when(contractRow.getBranchName()).thenReturn("Chi nhánh 1");
        when(contractRow.getStartYear()).thenReturn(2000);
        when(contractRow.getStartMonth()).thenReturn(1);
        when(dashboardRepository.findActiveContractRooms(List.of(1L))).thenReturn(List.of(contractRow));

        ContractBillPeriod billPeriod = mock(ContractBillPeriod.class);
        when(billPeriod.getContractId()).thenReturn(900L);
        when(billPeriod.getBillYear()).thenReturn(expected.getYear());
        when(billPeriod.getBillMonth()).thenReturn(expected.getMonthValue());
        when(dashboardRepository.findBillPeriods(List.of(900L))).thenReturn(List.of(billPeriod));

        UnpaidInvoiceRoomRow unpaidRow = mock(UnpaidInvoiceRoomRow.class);
        when(unpaidRow.getRoomId()).thenReturn(50L);
        when(unpaidRow.getRoomCode()).thenReturn("P101");
        when(unpaidRow.getBranchId()).thenReturn(1L);
        when(unpaidRow.getBranchName()).thenReturn("Chi nhánh 1");
        when(unpaidRow.getBillYear()).thenReturn(2026);
        when(unpaidRow.getBillMonth()).thenReturn(3);
        when(unpaidRow.getTotalAmount()).thenReturn(new BigDecimal("3000000"));
        when(unpaidRow.getPaidAmount()).thenReturn(new BigDecimal("1000000"));
        when(unpaidRow.getRemainingAmount()).thenReturn(new BigDecimal("2000000"));
        when(dashboardRepository.unpaidInvoiceRooms(List.of(1L))).thenReturn(List.of(unpaidRow));

        DashboardResponse response = assertDoesNotThrow(
                () -> dashboardService.getDashboard(1L, 2026, 1, 2026, 3));

        assertEquals(2, response.roomStatus().emptyCount());
        assertEquals(5, response.roomStatus().occupiedCount());

        assertEquals(3, response.moveInOut().size());
        assertEquals(3, response.moveInOut().get(0).moveInCount());
        assertEquals(0, response.moveInOut().get(0).moveOutCount());
        assertEquals(1, response.moveInOut().get(1).moveOutCount());

        assertEquals(3, response.revenue().size());
        assertEquals(new BigDecimal("5000000"), response.revenue().get(1).totalAmount());
        assertEquals(BigDecimal.ZERO, response.revenue().get(0).totalAmount());

        assertEquals(1, response.occupantsByBranch().size());
        assertEquals(10, response.occupantsByBranch().get(0).occupantCount());

        // 6-month lookback window with only the most recent month billed -> 5 months missing.
        assertEquals(5, response.missingInvoiceRooms().size());
        assertFalse(response.missingInvoiceRooms().stream()
                .anyMatch(row -> row.missingYear() == expected.getYear() && row.missingMonth() == expected.getMonthValue()));
        assertTrue(response.missingInvoiceRooms().stream()
                .anyMatch(row -> row.missingYear() == windowStart.getYear() && row.missingMonth() == windowStart.getMonthValue()));

        assertEquals(1, response.unpaidInvoiceRooms().size());
        assertEquals(new BigDecimal("2000000"), response.unpaidInvoiceRooms().get(0).remainingAmount());
    }
}

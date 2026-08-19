package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Room;
import com.rentalroom.management.repository.projection.ActiveContractRoomRow;
import com.rentalroom.management.repository.projection.ContractBillPeriod;
import com.rentalroom.management.repository.projection.MonthCount;
import com.rentalroom.management.repository.projection.MonthRevenue;
import com.rentalroom.management.repository.projection.OccupantsByBranchRow;
import com.rentalroom.management.repository.projection.RoomInvoiceActionRow;
import com.rentalroom.management.repository.projection.RoomStatusCount;
import com.rentalroom.management.repository.projection.UnpaidInvoiceRoomRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Aggregation-only queries backing the dashboard (spec 4.5) — not a CRUD repository for any single entity. */
public interface DashboardRepository extends Repository<Room, Long> {

    @Query(value = """
            SELECT r.status AS status, COUNT(*) AS cnt
            FROM room r
            WHERE r.branch_id IN (:branchIds)
            GROUP BY r.status
            """, nativeQuery = true)
    List<RoomStatusCount> countRoomsByStatus(@Param("branchIds") List<Long> branchIds);

    @Query(value = """
            SELECT YEAR(c.start_date) AS yr, MONTH(c.start_date) AS mo, COUNT(*) AS cnt
            FROM contract c JOIN room r ON r.id = c.room_id
            WHERE r.branch_id IN (:branchIds) AND c.start_date >= :fromDate AND c.start_date < :toDateExclusive
            GROUP BY YEAR(c.start_date), MONTH(c.start_date)
            """, nativeQuery = true)
    List<MonthCount> countMoveInsByMonth(@Param("branchIds") List<Long> branchIds,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDateExclusive") LocalDate toDateExclusive);

    @Query(value = """
            SELECT YEAR(c.end_date) AS yr, MONTH(c.end_date) AS mo, COUNT(*) AS cnt
            FROM contract c JOIN room r ON r.id = c.room_id
            WHERE r.branch_id IN (:branchIds) AND c.status = 'ENDED'
              AND c.end_date >= :fromDate AND c.end_date < :toDateExclusive
            GROUP BY YEAR(c.end_date), MONTH(c.end_date)
            """, nativeQuery = true)
    List<MonthCount> countMoveOutsByMonth(@Param("branchIds") List<Long> branchIds,
                                           @Param("fromDate") LocalDate fromDate,
                                           @Param("toDateExclusive") LocalDate toDateExclusive);

    @Query(value = """
            SELECT YEAR(p.payment_date) AS yr, MONTH(p.payment_date) AS mo, SUM(p.amount) AS total
            FROM payment p
            JOIN monthly_bill mb ON mb.id = p.monthly_bill_id
            JOIN contract c ON c.id = mb.contract_id
            JOIN room r ON r.id = c.room_id
            WHERE r.branch_id IN (:branchIds) AND p.payment_date >= :fromDate AND p.payment_date < :toDateExclusive
            GROUP BY YEAR(p.payment_date), MONTH(p.payment_date)
            """, nativeQuery = true)
    List<MonthRevenue> revenueByMonth(@Param("branchIds") List<Long> branchIds,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDateExclusive") LocalDate toDateExclusive);

    /**
     * Distinct occupant count per branch: every tenant on an ACTIVE contract counts (a contract can
     * have several co-tenants living in the same room, not just the one who signed as
     * representative) — {@code COUNT(DISTINCT ...)} only collapses the one case the business rule
     * actually calls out, a person who is themselves a tenant on 2+ concurrent contracts at once.
     */
    @Query(value = """
            SELECT b.id AS branchId, b.name AS branchName, COUNT(DISTINCT ct.tenant_id) AS occupantCount
            FROM branch b
            LEFT JOIN room r ON r.branch_id = b.id
            LEFT JOIN contract c ON c.room_id = r.id AND c.status = 'ACTIVE'
            LEFT JOIN contract_tenant ct ON ct.contract_id = c.id
            WHERE b.id IN (:branchIds)
            GROUP BY b.id, b.name
            """, nativeQuery = true)
    List<OccupantsByBranchRow> occupantsByBranch(@Param("branchIds") List<Long> branchIds);

    /** Every currently-ACTIVE contract's room, used to compute missing monthly-bill months in Java. */
    @Query(value = """
            SELECT c.id AS contractId, r.id AS roomId, r.room_code AS roomCode,
                   r.branch_id AS branchId, b.name AS branchName,
                   YEAR(c.start_date) AS startYear, MONTH(c.start_date) AS startMonth
            FROM contract c
            JOIN room r ON r.id = c.room_id
            JOIN branch b ON b.id = r.branch_id
            WHERE c.status = 'ACTIVE' AND r.branch_id IN (:branchIds)
            """, nativeQuery = true)
    List<ActiveContractRoomRow> findActiveContractRooms(@Param("branchIds") List<Long> branchIds);

    @Query(value = """
            SELECT mb.contract_id AS contractId, mb.bill_year AS billYear, mb.bill_month AS billMonth
            FROM monthly_bill mb
            WHERE mb.contract_id IN (:contractIds)
            """, nativeQuery = true)
    List<ContractBillPeriod> findBillPeriods(@Param("contractIds") List<Long> contractIds);

    /** One row per unpaid/partially-paid bill (not aggregated per room) — the period matters to the caller. */
    @Query(value = """
            SELECT r.id AS roomId, r.room_code AS roomCode, r.branch_id AS branchId, b.name AS branchName,
                   mb.bill_year AS billYear, mb.bill_month AS billMonth,
                   mb.total_amount AS totalAmount, mb.paid_amount AS paidAmount,
                   mb.remaining_amount AS remainingAmount
            FROM monthly_bill mb
            JOIN contract c ON c.id = mb.contract_id
            JOIN room r ON r.id = c.room_id
            JOIN branch b ON b.id = r.branch_id
            WHERE r.branch_id IN (:branchIds) AND mb.remaining_amount > 0 AND mb.payment_status != 'CHUA_XAC_NHAN'
            ORDER BY mb.bill_year DESC, mb.bill_month DESC, mb.remaining_amount DESC
            """, nativeQuery = true)
    List<UnpaidInvoiceRoomRow> unpaidInvoiceRooms(@Param("branchIds") List<Long> branchIds);

    /**
     * Every currently-occupied room whose bill for {@code year}/{@code month} either doesn't exist
     * yet or exists but is still {@code CHUA_XAC_NHAN} — backs the invoice-reminder email job
     * (InvoiceReminderService). Not branch-scoped by caller since the job itself iterates every branch.
     */
    @Query(value = """
            SELECT r.id AS roomId, r.room_code AS roomCode, r.branch_id AS branchId, b.name AS branchName,
                   CASE WHEN mb.id IS NULL THEN 'MISSING' ELSE 'UNCONFIRMED' END AS reason
            FROM contract c
            JOIN room r ON r.id = c.room_id
            JOIN branch b ON b.id = r.branch_id
            LEFT JOIN monthly_bill mb ON mb.contract_id = c.id AND mb.bill_year = :year AND mb.bill_month = :month
            WHERE c.status = 'ACTIVE' AND (mb.id IS NULL OR mb.payment_status = 'CHUA_XAC_NHAN')
            ORDER BY b.name, r.room_code
            """, nativeQuery = true)
    List<RoomInvoiceActionRow> roomsNeedingInvoiceAction(@Param("year") int year, @Param("month") int month);
}

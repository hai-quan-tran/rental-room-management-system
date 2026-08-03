package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Room;
import com.rentalroom.management.repository.projection.MonthCount;
import com.rentalroom.management.repository.projection.MonthRevenue;
import com.rentalroom.management.repository.projection.RoomStatusCount;
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
            WHERE r.branch_id IN (:branchIds) AND c.start_date >= :fromDate
            GROUP BY YEAR(c.start_date), MONTH(c.start_date)
            """, nativeQuery = true)
    List<MonthCount> countMoveInsByMonth(@Param("branchIds") List<Long> branchIds, @Param("fromDate") LocalDate fromDate);

    @Query(value = """
            SELECT YEAR(c.end_date) AS yr, MONTH(c.end_date) AS mo, COUNT(*) AS cnt
            FROM contract c JOIN room r ON r.id = c.room_id
            WHERE r.branch_id IN (:branchIds) AND c.status = 'ENDED' AND c.end_date >= :fromDate
            GROUP BY YEAR(c.end_date), MONTH(c.end_date)
            """, nativeQuery = true)
    List<MonthCount> countMoveOutsByMonth(@Param("branchIds") List<Long> branchIds, @Param("fromDate") LocalDate fromDate);

    @Query(value = """
            SELECT YEAR(p.payment_date) AS yr, MONTH(p.payment_date) AS mo, SUM(p.amount) AS total
            FROM payment p
            JOIN monthly_bill mb ON mb.id = p.monthly_bill_id
            JOIN contract c ON c.id = mb.contract_id
            JOIN room r ON r.id = c.room_id
            WHERE r.branch_id IN (:branchIds) AND p.payment_date >= :fromDate
            GROUP BY YEAR(p.payment_date), MONTH(p.payment_date)
            """, nativeQuery = true)
    List<MonthRevenue> revenueByMonth(@Param("branchIds") List<Long> branchIds, @Param("fromDate") LocalDate fromDate);
}

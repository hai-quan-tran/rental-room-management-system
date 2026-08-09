package com.rentalroom.management.repository;

import com.rentalroom.management.entity.MeterReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

    Optional<MeterReading> findByRoomIdAndExtraFeeCategoryIdAndBillYearAndBillMonth(
            Long roomId, Long extraFeeCategoryId, Integer billYear, Integer billMonth);

    /**
     * Full history for 1 room+category, newest first — callers filter in Java for "strictly before
     * a given period" (auto-chaining {@code oldReading}, grid preview) since a room's reading history
     * is small and a derived query can't express the compound year/month "before" comparison cleanly.
     */
    List<MeterReading> findByRoomIdAndExtraFeeCategoryIdOrderByBillYearDescBillMonthDesc(Long roomId, Long extraFeeCategoryId);

    boolean existsByExtraFeeCategoryId(Long extraFeeCategoryId);
}

package com.rentalroom.management.repository;

import com.rentalroom.management.entity.UtilityRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UtilityRateRepository extends JpaRepository<UtilityRate, Long> {

    List<UtilityRate> findByBranchIdOrderByExtraFeeCategoryIdAscEffectiveFromDesc(Long branchId);

    boolean existsByExtraFeeCategoryId(Long extraFeeCategoryId);

    boolean existsByBranchIdAndExtraFeeCategoryIdAndEffectiveFrom(Long branchId, Long extraFeeCategoryId, LocalDate effectiveFrom);

    /** The rate in effect for a given billing reference date — latest {@code effectiveFrom <= referenceDate}. */
    Optional<UtilityRate> findTopByBranchIdAndExtraFeeCategoryIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long branchId, Long extraFeeCategoryId, LocalDate referenceDate);
}

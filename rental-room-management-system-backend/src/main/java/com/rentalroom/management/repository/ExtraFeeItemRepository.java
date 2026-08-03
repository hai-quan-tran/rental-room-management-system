package com.rentalroom.management.repository;

import com.rentalroom.management.entity.ExtraFeeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExtraFeeItemRepository extends JpaRepository<ExtraFeeItem, Long> {

    List<ExtraFeeItem> findByMonthlyBillIdOrderByIdAsc(Long monthlyBillId);

    Optional<ExtraFeeItem> findByIdAndMonthlyBillId(Long id, Long monthlyBillId);

    boolean existsByExtraFeeCategoryId(Long extraFeeCategoryId);
}

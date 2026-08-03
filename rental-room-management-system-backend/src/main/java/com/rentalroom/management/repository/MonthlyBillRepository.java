package com.rentalroom.management.repository;

import com.rentalroom.management.entity.MonthlyBill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyBillRepository extends JpaRepository<MonthlyBill, Long> {

    List<MonthlyBill> findByContractIdOrderByBillYearDescBillMonthDesc(Long contractId);

    Optional<MonthlyBill> findByContractIdAndBillYearAndBillMonth(Long contractId, Integer billYear, Integer billMonth);

    boolean existsByContractIdAndBillYearAndBillMonth(Long contractId, Integer billYear, Integer billMonth);

    Page<MonthlyBill> findByBillYearAndBillMonthAndContract_Room_BranchIdIn(
            Integer billYear, Integer billMonth, List<Long> branchIds, Pageable pageable);
}

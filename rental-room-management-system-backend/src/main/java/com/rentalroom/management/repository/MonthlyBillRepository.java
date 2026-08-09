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

    /**
     * Every bill for a given room+month, regardless of which contract it belongs to — normally 0
     * or 1, but can be N>1 if a contract ended and a new one started in the same room within the
     * same billing month, which callers must treat as ambiguous rather than picking one.
     */
    List<MonthlyBill> findByContract_Room_IdAndBillYearAndBillMonth(Long roomId, Integer billYear, Integer billMonth);
}

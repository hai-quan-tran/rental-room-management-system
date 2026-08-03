package com.rentalroom.management.repository;

import com.rentalroom.management.entity.DebtRecord;
import com.rentalroom.management.enums.DebtStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DebtRecordRepository extends JpaRepository<DebtRecord, Long> {

    Page<DebtRecord> findByStatus(DebtStatus status, Pageable pageable);

    Page<DebtRecord> findByStatusAndContract_Room_BranchIdIn(DebtStatus status, List<Long> branchIds, Pageable pageable);

    Optional<DebtRecord> findByChecklistId(Long checklistId);
}

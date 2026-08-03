package com.rentalroom.management.repository;

import com.rentalroom.management.entity.BranchRoomSummary;
import com.rentalroom.management.entity.BranchRoomSummaryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRoomSummaryRepository extends JpaRepository<BranchRoomSummary, BranchRoomSummaryId> {

    List<BranchRoomSummary> findById_BranchId(Long branchId);
}

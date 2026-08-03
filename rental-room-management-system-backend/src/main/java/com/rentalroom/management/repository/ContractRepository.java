package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Contract;
import com.rentalroom.management.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByRoomIdOrderByStartDateDesc(Long roomId);

    Optional<Contract> findByRoomIdAndStatus(Long roomId, ContractStatus status);

    boolean existsByRoomIdAndStatus(Long roomId, ContractStatus status);

    List<Contract> findByStatusAndRoom_BranchIdIn(ContractStatus status, List<Long> branchIds);
}

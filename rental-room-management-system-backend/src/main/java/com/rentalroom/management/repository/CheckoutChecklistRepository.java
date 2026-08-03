package com.rentalroom.management.repository;

import com.rentalroom.management.entity.CheckoutChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutChecklistRepository extends JpaRepository<CheckoutChecklist, Long> {

    Optional<CheckoutChecklist> findByContractId(Long contractId);
}

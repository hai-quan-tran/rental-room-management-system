package com.rentalroom.management.repository;

import com.rentalroom.management.entity.ContractTenant;
import com.rentalroom.management.entity.ContractTenantId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractTenantRepository extends JpaRepository<ContractTenant, ContractTenantId> {

    List<ContractTenant> findById_ContractIdOrderByRepresentativeDescJoinedAtAsc(Long contractId);

    List<ContractTenant> findById_TenantIdOrderByJoinedAtDesc(Long tenantId);

    boolean existsById_ContractIdAndRepresentativeTrue(Long contractId);

    long countById_ContractId(Long contractId);
}

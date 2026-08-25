package com.rentalroom.management.repository;

import com.rentalroom.management.entity.ContractTenant;
import com.rentalroom.management.entity.ContractTenantId;
import com.rentalroom.management.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractTenantRepository extends JpaRepository<ContractTenant, ContractTenantId> {

    List<ContractTenant> findById_ContractIdOrderByRepresentativeDescJoinedAtAsc(Long contractId);

    List<ContractTenant> findById_TenantIdOrderByJoinedAtDesc(Long tenantId);

    boolean existsById_ContractIdAndRepresentativeTrue(Long contractId);

    Optional<ContractTenant> findById_ContractIdAndRepresentativeTrue(Long contractId);

    boolean existsById_TenantIdAndRepresentativeTrueAndContract_Status(Long tenantId, ContractStatus status);

    long countById_ContractId(Long contractId);
}

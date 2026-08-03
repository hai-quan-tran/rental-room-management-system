package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TenantRepository extends JpaRepository<Tenant, Long>, JpaSpecificationExecutor<Tenant> {

    boolean existsByIdCardNumber(String idCardNumber);

    boolean existsByIdCardNumberAndIdNot(String idCardNumber, Long id);
}

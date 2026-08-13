package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    boolean existsByIdCardNumber(String idCardNumber);

    boolean existsByIdCardNumberAndIdNot(String idCardNumber, Long id);

    boolean existsByAccountId(Long accountId);
}

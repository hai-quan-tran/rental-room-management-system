package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    boolean existsByIdCardNumber(String idCardNumber);

    boolean existsByIdCardNumberAndIdNot(String idCardNumber, Long id);

    boolean existsByAccountId(Long accountId);

    Optional<Employee> findByAccountId(Long accountId);
}

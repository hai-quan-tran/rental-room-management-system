package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("SELECT a FROM Account a WHERE a.id NOT IN (SELECT e.account.id FROM Employee e)")
    List<Account> findUnassigned();
}

package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Page<Item> findByBranchIdAndNameContainingIgnoreCase(Long branchId, String name, Pageable pageable);

    Page<Item> findByBranchId(Long branchId, Pageable pageable);

    List<Item> findByBranchIdOrderByNameAsc(Long branchId);

    boolean existsByBranchIdAndName(Long branchId, String name);

    boolean existsByBranchIdAndNameAndIdNot(Long branchId, String name, Long id);
}

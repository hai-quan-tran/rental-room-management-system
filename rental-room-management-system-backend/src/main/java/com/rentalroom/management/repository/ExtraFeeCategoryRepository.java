package com.rentalroom.management.repository;

import com.rentalroom.management.entity.ExtraFeeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtraFeeCategoryRepository extends JpaRepository<ExtraFeeCategory, Long> {

    List<ExtraFeeCategory> findAllByOrderByNameAsc();

    List<ExtraFeeCategory> findByMeteredTrue();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}

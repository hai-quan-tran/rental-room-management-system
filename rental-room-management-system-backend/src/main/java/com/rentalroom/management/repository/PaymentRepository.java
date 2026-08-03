package com.rentalroom.management.repository;

import com.rentalroom.management.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByMonthlyBillIdOrderByPaymentDateDescIdDesc(Long monthlyBillId);
}

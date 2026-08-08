package com.rentalroom.management.repository.projection;

import java.math.BigDecimal;

public interface UnpaidInvoiceRoomRow {
    Long getRoomId();

    String getRoomCode();

    Long getBranchId();

    String getBranchName();

    Integer getBillYear();

    Integer getBillMonth();

    BigDecimal getTotalAmount();

    BigDecimal getPaidAmount();

    BigDecimal getRemainingAmount();
}

package com.rentalroom.management.repository.projection;

import java.math.BigDecimal;

public interface MonthRevenue {
    Integer getYr();

    Integer getMo();

    BigDecimal getTotal();
}

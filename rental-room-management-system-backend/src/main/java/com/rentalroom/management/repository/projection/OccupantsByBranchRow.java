package com.rentalroom.management.repository.projection;

public interface OccupantsByBranchRow {
    Long getBranchId();

    String getBranchName();

    Long getOccupantCount();
}

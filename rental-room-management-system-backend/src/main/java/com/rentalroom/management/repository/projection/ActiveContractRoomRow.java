package com.rentalroom.management.repository.projection;

/** One row per currently-ACTIVE contract, used to compute missing monthly-bill months in Java. */
public interface ActiveContractRoomRow {
    Long getContractId();

    Long getRoomId();

    String getRoomCode();

    Long getBranchId();

    String getBranchName();

    Integer getStartYear();

    Integer getStartMonth();
}

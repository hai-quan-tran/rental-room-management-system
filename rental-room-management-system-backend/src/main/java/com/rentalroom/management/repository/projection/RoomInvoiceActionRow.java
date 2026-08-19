package com.rentalroom.management.repository.projection;

public interface RoomInvoiceActionRow {
    Long getRoomId();

    String getRoomCode();

    Long getBranchId();

    String getBranchName();

    /** {@code MISSING} (no bill row for the target month) or {@code UNCONFIRMED} ({@code CHUA_XAC_NHAN}). */
    String getReason();
}

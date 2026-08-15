package com.rentalroom.management.enums;

/**
 * Must stay in sync with the {@code chk_monthly_bill_status} CHECK constraint in V1__init_schema.sql.
 */
public enum PaymentStatus {
    CHUA_XAC_NHAN,
    CHUA_THANH_TOAN,
    THANH_TOAN_MOT_PHAN,
    DA_THANH_TOAN
}

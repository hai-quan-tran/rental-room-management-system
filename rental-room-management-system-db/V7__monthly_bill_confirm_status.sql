-- =====================================================================================
-- V7__monthly_bill_confirm_status.sql
-- Rental Room Management System
-- =====================================================================================
-- Adds a new payment_status value CHUA_XAC_NHAN (unconfirmed), which is now the default
-- status for every newly created monthly_bill. While CHUA_XAC_NHAN, extra fee items and
-- linked meter readings may still be freely edited; once an admin explicitly confirms the
-- bill (status moves to CHUA_THANH_TOAN), it is locked from further edits — only payments
-- may still be recorded. Existing rows already hold one of the 3 old status values, which
-- under the new model all mean "already confirmed" — no backfill needed.
-- =====================================================================================

SET NAMES utf8mb4;
SET time_zone = '+07:00';

ALTER TABLE monthly_bill
    MODIFY COLUMN payment_status VARCHAR(30) NOT NULL DEFAULT 'CHUA_XAC_NHAN';

ALTER TABLE monthly_bill
    DROP CHECK chk_monthly_bill_status;

ALTER TABLE monthly_bill
    ADD CONSTRAINT chk_monthly_bill_status CHECK (
        payment_status IN ('CHUA_XAC_NHAN', 'CHUA_THANH_TOAN', 'THANH_TOAN_MOT_PHAN', 'DA_THANH_TOAN')
    );

-- =====================================================================================
-- END V7__monthly_bill_confirm_status.sql
-- =====================================================================================

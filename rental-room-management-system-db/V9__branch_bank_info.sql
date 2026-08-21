-- =====================================================================================
-- V9__branch_bank_info.sql
-- Rental Room Management System
-- =====================================================================================
-- Adds optional bank-receiving info to `branch` (bank_bin, bank_account_number,
-- bank_account_name) so the frontend can build a VietQR payment QR image per branch,
-- letting tenants scan-to-pay with the bank/account/amount/note already filled in.
-- All 3 columns are nullable — a branch with no bank info configured simply never shows
-- the QR block, nothing else is affected. bank_bin stores the numeric Napas BIN (e.g.
-- 970436 = Vietcombank), not the bank's display name, to avoid keeping a duplicate name
-- in sync — the display name is looked up client-side from a static bank-list constant.
-- =====================================================================================

SET NAMES utf8mb4;
SET time_zone = '+07:00';

ALTER TABLE branch
    ADD COLUMN bank_bin             VARCHAR(20)  NULL AFTER manager_account_id,
    ADD COLUMN bank_account_number  VARCHAR(50)  NULL AFTER bank_bin,
    ADD COLUMN bank_account_name    VARCHAR(150) NULL AFTER bank_account_number;

-- =====================================================================================
-- END V9__branch_bank_info.sql
-- =====================================================================================

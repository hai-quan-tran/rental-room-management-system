-- =====================================================================================
-- V6__account_fullname_nullable.sql
-- Rental Room Management System
-- =====================================================================================
-- account.full_name is no longer collected on the Account screen — it is now sourced
-- from the linked EMPLOYEE record (employee.full_name), synced onto account.full_name
-- whenever an employee is created/linked/edited (see EmployeeService). Accounts with no
-- linked employee (e.g. the bootstrap admin, or accounts created directly on the Account
-- screen before being linked) legitimately have no name to show, so the column must
-- become nullable.
-- =====================================================================================

SET NAMES utf8mb4;
SET time_zone = '+07:00';

ALTER TABLE account
    MODIFY COLUMN full_name VARCHAR(150) NULL;

-- =====================================================================================
-- END V6__account_fullname_nullable.sql
-- =====================================================================================

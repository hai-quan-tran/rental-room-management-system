-- =====================================================================================
-- V5__employee.sql
-- Rental Room Management System
-- =====================================================================================
-- Bảng mới EMPLOYEE — hồ sơ nhân viên (họ tên, ngày sinh, CCCD, SĐT, email — tất cả bắt
-- buộc, khác TENANT nơi email là optional). Chỉ ADMIN_TONG được truy cập màn hình này.
-- Mỗi nhân viên có đúng 1 tài khoản đăng nhập (ACCOUNT) — EMPLOYEE giữ chiều sở hữu quan
-- hệ 1-1 (account_id UNIQUE), vì EMPLOYEE là bản ghi phụ thuộc vào ACCOUNT (không thể có
-- nhân viên mà không có tài khoản), account_id không đổi được sau khi tạo.
-- =====================================================================================

SET NAMES utf8mb4;
SET time_zone = '+07:00';

-- =====================================================================================
-- 1. EMPLOYEE
-- =====================================================================================
CREATE TABLE employee (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(150)    NOT NULL,
    date_of_birth   DATE            NOT NULL,
    id_card_number  VARCHAR(20)     NOT NULL,
    phone_number    VARCHAR(20)     NOT NULL,
    email           VARCHAR(150)    NOT NULL,
    account_id      BIGINT UNSIGNED NOT NULL,
    created_by      BIGINT UNSIGNED NULL,
    updated_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_employee_id_card_number UNIQUE (id_card_number),
    CONSTRAINT uq_employee_account_id UNIQUE (account_id),
    CONSTRAINT fk_employee_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =====================================================================================
-- END V5__employee.sql
-- =====================================================================================

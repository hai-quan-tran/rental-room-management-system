-- =====================================================================================
-- V4__utility_rates_meter_readings.sql
-- Rental Room Management System
-- =====================================================================================
-- Giai đoạn 1 của việc tự động hóa tính phí điện/nước (trước đây chỉ là 1 dòng
-- extra_fee_item với amount gõ tay tự do, y hệt "Wifi"/"Khác", không có đơn giá lưu trữ,
-- không có chỉ số công tơ, không tính toán gì cả):
--   1) extra_fee_category có thêm cờ is_metered — đánh dấu category nào (Điện, Nước) cần
--      tự động sinh dòng chi phí + có màn cấu hình đơn giá/chỉ số riêng. Dùng cờ dữ liệu
--      thay vì so khớp tên "Điện"/"Nước" trong code Java, vì category đã có CRUD đổi tên
--      qua API sẵn có (ExtraFeeCategoryController) dù chưa có UI dùng tới.
--   2) Bảng mới UTILITY_RATE: đơn giá điện/nước theo từng chi nhánh, có ngày hiệu lực.
--      Đổi giá = thêm 1 dòng mới với effective_from mới, không sửa/xóa dòng cũ — đúng
--      pattern "snapshot tại thời điểm tạo hóa đơn" đã dùng cho rent_amount/wifi_fee.
--   3) Bảng mới METER_READING: chỉ số công tơ cũ/mới theo từng phòng theo từng tháng.
--      Gắn theo room_id (không phải contract_id) vì đồng hồ là hạ tầng vật lý của phòng,
--      giống room.wifi_fee/parking_fee không phụ thuộc hợp đồng nào đang thuê.
-- =====================================================================================

SET NAMES utf8mb4;
SET time_zone = '+07:00';

-- =====================================================================================
-- 1. EXTRA_FEE_CATEGORY — thêm cờ is_metered, bật cho Điện/Nước
-- =====================================================================================
ALTER TABLE extra_fee_category
    ADD COLUMN is_metered BOOLEAN NOT NULL DEFAULT FALSE AFTER unit;

UPDATE extra_fee_category SET is_metered = TRUE WHERE name IN ('Điện', 'Nước');


-- =====================================================================================
-- 2. UTILITY_RATE — đơn giá điện/nước theo chi nhánh, có ngày hiệu lực (chỉ thêm, không sửa)
-- =====================================================================================
CREATE TABLE utility_rate (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    branch_id             BIGINT UNSIGNED NOT NULL,
    extra_fee_category_id BIGINT UNSIGNED NOT NULL,
    unit_price            DECIMAL(15,0)   NOT NULL DEFAULT 0,
    effective_from        DATE            NOT NULL,
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_utility_rate_branch_category_from UNIQUE (branch_id, extra_fee_category_id, effective_from),
    CONSTRAINT fk_utility_rate_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT fk_utility_rate_category FOREIGN KEY (extra_fee_category_id) REFERENCES extra_fee_category (id),
    CONSTRAINT chk_utility_rate_price CHECK (unit_price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_utility_rate_branch_category ON utility_rate (branch_id, extra_fee_category_id);


-- =====================================================================================
-- 3. METER_READING — chỉ số công tơ cũ/mới theo phòng theo tháng
-- =====================================================================================
CREATE TABLE meter_reading (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    room_id               BIGINT UNSIGNED NOT NULL,
    extra_fee_category_id BIGINT UNSIGNED NOT NULL,
    bill_month            TINYINT UNSIGNED  NOT NULL,
    bill_year             SMALLINT UNSIGNED NOT NULL,
    old_reading           DECIMAL(10,2)   NOT NULL,
    new_reading           DECIMAL(10,2)   NOT NULL,
    consumption           DECIMAL(10,2)   GENERATED ALWAYS AS (new_reading - old_reading) STORED,
    recorded_by           BIGINT UNSIGNED NULL,
    note                  VARCHAR(300)    NULL,
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_meter_reading_room_category_period UNIQUE (room_id, extra_fee_category_id, bill_year, bill_month),
    CONSTRAINT fk_meter_reading_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_meter_reading_category FOREIGN KEY (extra_fee_category_id) REFERENCES extra_fee_category (id),
    CONSTRAINT chk_meter_reading_old_nonneg CHECK (old_reading >= 0),
    CONSTRAINT chk_meter_reading_new_ge_old CHECK (new_reading >= old_reading)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_meter_reading_room_category ON meter_reading (room_id, extra_fee_category_id);


-- =====================================================================================
-- END V4__utility_rates_meter_readings.sql
-- =====================================================================================

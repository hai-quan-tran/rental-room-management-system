-- =====================================================================================
-- V3__room_fees_items_branch_room_types_checklist_quantities.sql
-- Rental Room Management System
-- =====================================================================================
-- Ba thay đổi lớn trong file này:
--   1) Room có thêm phí wifi/gửi xe cố định hàng tháng (không chia theo tỷ lệ ngày ở),
--      cộng vào monthly_bill dưới dạng 2 cột riêng (snapshot tại thời điểm tạo hóa đơn,
--      giống cách rent_amount đã là snapshot chứ không tính lại theo thời gian thực).
--   2) ROOM_TYPE giờ thuộc về 1 CHI NHÁNH cụ thể (mỗi chi nhánh có danh sách loại phòng
--      riêng) — trước đây room_type dùng chung cho mọi chi nhánh. Đồng thời thêm bảng
--      ITEM (vật dụng, thuộc 1 chi nhánh, có giá + số lượng tồn kho + số lượng mặc định
--      mỗi phòng) và room_type_handover_item giờ CHỌN vật dụng từ bảng item thay vì nhập
--      tên tự do.
--   3) checkout_checklist_item chuyển từ "1 status duy nhất/dòng" sang lưu số lượng chia
--      3 phần: còn nguyên / hư hỏng / mất (tính từ total_quantity - damaged - lost).
--
-- LƯU Ý QUAN TRỌNG (dữ liệu dev, không phải production):
--   - room_type.branch_id được backfill bằng chi nhánh của phòng ĐẦU TIÊN (MIN branch_id)
--     đang dùng loại phòng đó. Nếu 1 loại phòng trước đây được dùng bởi phòng ở NHIỀU chi
--     nhánh khác nhau, migration này sẽ gán nó về 1 chi nhánh duy nhất (chọn tùy ý) — cần
--     kiểm tra lại dữ liệu hiện có trước khi chạy nếu việc này quan trọng.
--   - room_type_handover_item.item_name (tự nhập tay) không có Item tương ứng nên các
--     dòng cũ sẽ bị XÓA hết (không thể tự map tên tự do sang 1 Item cụ thể) — cần cấu
--     hình lại vật dụng bàn giao sau khi chạy migration này.
-- =====================================================================================

SET NAMES utf8mb4;
SET time_zone = '+07:00';

-- =====================================================================================
-- 1. ROOM — thêm phí wifi / phí gửi xe cố định hàng tháng
-- =====================================================================================
ALTER TABLE room
    ADD COLUMN wifi_fee    DECIMAL(15,0) NOT NULL DEFAULT 0 AFTER monthly_rent,
    ADD COLUMN parking_fee DECIMAL(15,0) NOT NULL DEFAULT 0 AFTER wifi_fee,
    ADD CONSTRAINT chk_room_wifi_fee    CHECK (wifi_fee >= 0),
    ADD CONSTRAINT chk_room_parking_fee CHECK (parking_fee >= 0);


-- =====================================================================================
-- 2. MONTHLY_BILL — cộng phí wifi/gửi xe (snapshot tại thời điểm tạo hóa đơn) vào tổng
-- =====================================================================================
ALTER TABLE monthly_bill
    ADD COLUMN wifi_fee    DECIMAL(15,0) NOT NULL DEFAULT 0 AFTER total_extra_fee,
    ADD COLUMN parking_fee DECIMAL(15,0) NOT NULL DEFAULT 0 AFTER wifi_fee,
    ADD CONSTRAINT chk_monthly_bill_wifi_fee    CHECK (wifi_fee >= 0),
    ADD CONSTRAINT chk_monthly_bill_parking_fee CHECK (parking_fee >= 0);

ALTER TABLE monthly_bill
    MODIFY COLUMN total_amount DECIMAL(15,0)
        GENERATED ALWAYS AS (rent_amount + total_extra_fee + wifi_fee + parking_fee) STORED,
    MODIFY COLUMN remaining_amount DECIMAL(15,0)
        GENERATED ALWAYS AS (rent_amount + total_extra_fee + wifi_fee + parking_fee - paid_amount) STORED;

-- trg_payment_after_insert không cần sửa: trigger chỉ đọc total_amount (đã tự bao gồm
-- wifi_fee/parking_fee qua công thức generated column mới ở trên).


-- =====================================================================================
-- 3. ROOM_TYPE — gắn vào 1 CHI NHÁNH cụ thể (trước đây dùng chung cho mọi chi nhánh)
-- =====================================================================================
ALTER TABLE room_type
    ADD COLUMN branch_id BIGINT UNSIGNED NULL AFTER id;

UPDATE room_type rt
SET branch_id = (
    SELECT MIN(r.branch_id) FROM room r WHERE r.room_type_id = rt.id
)
WHERE branch_id IS NULL;

-- Loại phòng không được phòng nào dùng tới (không suy ra được chi nhánh) -> xóa luôn,
-- vì không có cách nào biết loại phòng "mồ côi" này thuộc về chi nhánh nào.
DELETE FROM room_type WHERE branch_id IS NULL;

ALTER TABLE room_type
    MODIFY COLUMN branch_id BIGINT UNSIGNED NOT NULL,
    ADD CONSTRAINT fk_room_type_branch FOREIGN KEY (branch_id) REFERENCES branch (id);

ALTER TABLE room_type DROP CONSTRAINT uq_room_type_name;
ALTER TABLE room_type ADD CONSTRAINT uq_room_type_branch_name UNIQUE (branch_id, name);

CREATE INDEX idx_room_type_branch ON room_type (branch_id);


-- =====================================================================================
-- 4. ITEM — vật dụng thuộc 1 chi nhánh: tên, giá, số lượng tồn kho, số lượng mặc định/phòng
-- =====================================================================================
CREATE TABLE item (
    id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    branch_id                 BIGINT UNSIGNED NOT NULL,
    name                      VARCHAR(150)    NOT NULL,
    price                     DECIMAL(15,0)   NOT NULL DEFAULT 0,
    quantity_available        INT UNSIGNED    NOT NULL DEFAULT 0,
    default_quantity_per_room INT UNSIGNED    NOT NULL DEFAULT 1,
    created_at                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_item_branch_name UNIQUE (branch_id, name),
    CONSTRAINT fk_item_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT chk_item_price CHECK (price >= 0),
    CONSTRAINT chk_item_qty_available CHECK (quantity_available >= 0),
    CONSTRAINT chk_item_default_qty CHECK (default_quantity_per_room >= 1),
    CONSTRAINT chk_item_default_le_available CHECK (default_quantity_per_room <= quantity_available)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_item_branch ON item (branch_id);


-- =====================================================================================
-- 5. ROOM_TYPE_HANDOVER_ITEM — chọn vật dụng từ bảng ITEM thay vì nhập tên tự do
-- =====================================================================================
ALTER TABLE room_type_handover_item
    ADD COLUMN item_id BIGINT UNSIGNED NULL AFTER room_type_id;

-- Dữ liệu cũ dùng item_name tự nhập, không có Item tương ứng để map -> xóa, cấu hình lại.
DELETE FROM room_type_handover_item;

ALTER TABLE room_type_handover_item
    MODIFY COLUMN item_id BIGINT UNSIGNED NOT NULL,
    DROP COLUMN item_name,
    ADD CONSTRAINT fk_rthi_item FOREIGN KEY (item_id) REFERENCES item (id),
    ADD CONSTRAINT uq_rthi_room_type_item UNIQUE (room_type_id, item_id);

CREATE INDEX idx_rthi_item ON room_type_handover_item (item_id);


-- =====================================================================================
-- 6. CHECKOUT_CHECKLIST_ITEM — chia số lượng còn nguyên / hư hỏng / mất thay vì 1 status
-- =====================================================================================
ALTER TABLE checkout_checklist_item
    ADD COLUMN total_quantity   INT UNSIGNED NOT NULL DEFAULT 1 AFTER room_type_handover_item_id,
    ADD COLUMN damaged_quantity INT UNSIGNED NOT NULL DEFAULT 0 AFTER total_quantity,
    ADD COLUMN lost_quantity    INT UNSIGNED NOT NULL DEFAULT 0 AFTER damaged_quantity;

ALTER TABLE checkout_checklist_item DROP CHECK chk_cci_status;
ALTER TABLE checkout_checklist_item DROP COLUMN status;

ALTER TABLE checkout_checklist_item
    ADD CONSTRAINT chk_cci_quantities CHECK (damaged_quantity + lost_quantity <= total_quantity);

-- Còn nguyên (intact) = total_quantity - damaged_quantity - lost_quantity, không lưu cột
-- riêng, tính khi hiển thị/đọc (tránh 2 nguồn dữ liệu có thể lệch nhau).


-- =====================================================================================
-- END V3__room_fees_items_branch_room_types_checklist_quantities.sql
-- =====================================================================================

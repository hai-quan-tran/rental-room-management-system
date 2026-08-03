-- =====================================================================================
-- V1__init_schema.sql
-- Rental Room Management System - Initial Database Schema (MySQL 8)
-- =====================================================================================
-- Quy ước áp dụng trong toàn bộ file này:
--   - Engine: InnoDB, charset: utf8mb4, collation: utf8mb4_unicode_ci
--   - Khóa chính: id BIGINT UNSIGNED AUTO_INCREMENT
--   - "Enum" fields dùng VARCHAR + CHECK constraint (theo yêu cầu, dễ mở rộng giá trị
--     sau này chỉ cần ALTER CHECK, không cần đổi kiểu cột)
--   - Tiền tệ dùng DECIMAL(15,0) — VNĐ không có phần thập phân, tối đa ~999 tỷ / cột
--   - Tất cả các bảng nghiệp vụ chính đều có created_at / updated_at
--
-- Một số điều chỉnh so với bản spec ban đầu (lý do kỹ thuật, cần đối chiếu lại tài liệu
-- yêu cầu nếu bạn muốn giữ nguyên 100% theo spec cũ):
--   1) Bỏ cột `contract.representative_tenant_id` (denormalized, dễ lệch dữ liệu).
--      "Người đại diện ký hợp đồng" giờ là NGUỒN DỮ LIỆU DUY NHẤT tại
--      `contract_tenant.is_representative`, được ràng buộc: mỗi hợp đồng chỉ có ĐÚNG 1
--      người đại diện, thực thi bằng TRIGGER `trg_ct_before_insert`/`trg_ct_before_update`
--      (ban đầu định dùng generated column + unique index nhưng MySQL 8 báo lỗi 1215
--      khi ADD một STORED generated column phụ thuộc vào cột đang là khóa ngoại/khóa
--      chính — đây là hạn chế/bug đã biết của InnoDB — nên chuyển sang dùng trigger).
--   2) Bỏ `branch.total_rooms` và `branch.room_type_summary` — đây là dữ liệu tính toán
--      động (derived), không lưu cứng để tránh lệch dữ liệu khi thêm/xóa phòng. Sẽ tính
--      bằng query (hoặc VIEW) ở tầng backend, xem phần VIEW cuối file.
--   3) Bỏ `account.managed_branch_id` dạng cột JSON/list — quan hệ 1 account quản lý
--      N chi nhánh được suy ra tự nhiên từ `branch.manager_account_id` (nhiều dòng
--      branch có thể trỏ về cùng 1 account).
-- =====================================================================================

SET NAMES utf8mb4;
SET time_zone = '+07:00';

-- =====================================================================================
-- 1. ACCOUNT — tài khoản đăng nhập hệ thống
-- =====================================================================================
CREATE TABLE account (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(150)    NOT NULL,
    username        VARCHAR(100)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(30)     NOT NULL,
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_account_username UNIQUE (username),
    CONSTRAINT chk_account_role CHECK (role IN ('ADMIN_TONG', 'ADMIN_CAP_1', 'USER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_account_role ON account (role);
CREATE INDEX idx_account_is_active ON account (is_active);


-- =====================================================================================
-- 2. TENANT — người thuê
-- =====================================================================================
CREATE TABLE tenant (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name         VARCHAR(150)  NOT NULL,
    date_of_birth     DATE          NOT NULL,
    id_card_number    VARCHAR(20)   NOT NULL,
    phone_number      VARCHAR(20)   NOT NULL,
    email             VARCHAR(150)  NULL,
    created_by        BIGINT UNSIGNED NULL,
    updated_by        BIGINT UNSIGNED NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_tenant_id_card UNIQUE (id_card_number),
    CONSTRAINT fk_tenant_created_by FOREIGN KEY (created_by) REFERENCES account (id),
    CONSTRAINT fk_tenant_updated_by FOREIGN KEY (updated_by) REFERENCES account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_tenant_full_name ON tenant (full_name);
CREATE INDEX idx_tenant_phone_number ON tenant (phone_number);


-- =====================================================================================
-- 3. BRANCH — chi nhánh
-- =====================================================================================
CREATE TABLE branch (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(150)  NOT NULL,
    address             VARCHAR(300)  NOT NULL,
    manager_account_id  BIGINT UNSIGNED NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_branch_manager FOREIGN KEY (manager_account_id) REFERENCES account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_branch_manager ON branch (manager_account_id);


-- =====================================================================================
-- 4. ROOM_TYPE — loại phòng (vd "Có gác 4x4", "Không gác 4x4")
-- =====================================================================================
CREATE TABLE room_type (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100)  NOT NULL,
    area         VARCHAR(50)   NULL,     -- vd "4x4", "20m2"
    description  VARCHAR(500)  NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_room_type_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- =====================================================================================
-- 5. ROOM_TYPE_HANDOVER_ITEM — vật dụng bàn giao mặc định theo TỪNG LOẠI PHÒNG
--    (dùng chung cho mọi phòng cùng loại, không nhập riêng theo từng phòng/hợp đồng)
-- =====================================================================================
CREATE TABLE room_type_handover_item (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    room_type_id  BIGINT UNSIGNED NOT NULL,
    item_name     VARCHAR(150)    NOT NULL,
    quantity      INT UNSIGNED    NOT NULL DEFAULT 1,
    note          VARCHAR(300)    NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_rthi_room_type FOREIGN KEY (room_type_id) REFERENCES room_type (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_rthi_room_type ON room_type_handover_item (room_type_id);


-- =====================================================================================
-- 6. ROOM — phòng trọ thuộc 1 chi nhánh
-- =====================================================================================
CREATE TABLE room (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    branch_id      BIGINT UNSIGNED NOT NULL,
    room_code      VARCHAR(50)     NOT NULL,
    room_type_id   BIGINT UNSIGNED NOT NULL,
    monthly_rent   DECIMAL(15,0)   NOT NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'TRONG',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_room_branch_code UNIQUE (branch_id, room_code),
    CONSTRAINT fk_room_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT fk_room_room_type FOREIGN KEY (room_type_id) REFERENCES room_type (id),
    CONSTRAINT chk_room_status CHECK (status IN ('TRONG', 'DANG_THUE')),
    CONSTRAINT chk_room_monthly_rent CHECK (monthly_rent >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_room_branch ON room (branch_id);
CREATE INDEX idx_room_room_type ON room (room_type_id);
CREATE INDEX idx_room_status ON room (status);


-- =====================================================================================
-- 7. EXTRA_FEE_CATEGORY — danh mục chi phí phát sinh (Điện, Nước, Gửi xe, ...)
-- =====================================================================================
CREATE TABLE extra_fee_category (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    unit        VARCHAR(30)   NULL,   -- vd "kWh", "m3", "tháng"
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_extra_fee_category_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- =====================================================================================
-- 8. CONTRACT — hợp đồng thuê phòng
-- =====================================================================================
CREATE TABLE contract (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    room_id         BIGINT UNSIGNED NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE            NULL,     -- NULL = đang hiệu lực / chưa xác định ngày kết thúc
    monthly_rent    DECIMAL(15,0)   NOT NULL, -- giá thuê tại thời điểm ký, có thể khác giá mặc định của phòng
    deposit_amount  DECIMAL(15,0)   NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_by      BIGINT UNSIGNED NULL,
    updated_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_contract_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_contract_created_by FOREIGN KEY (created_by) REFERENCES account (id),
    CONSTRAINT fk_contract_updated_by FOREIGN KEY (updated_by) REFERENCES account (id),
    CONSTRAINT chk_contract_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT chk_contract_monthly_rent CHECK (monthly_rent >= 0),
    CONSTRAINT chk_contract_deposit CHECK (deposit_amount >= 0),
    CONSTRAINT chk_contract_dates CHECK (end_date IS NULL OR end_date >= start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_contract_room ON contract (room_id);
CREATE INDEX idx_contract_status ON contract (status);

-- Đảm bảo tại 1 thời điểm, mỗi phòng chỉ có TỐI ĐA 1 hợp đồng đang ACTIVE.
-- MySQL không hỗ trợ partial unique index trực tiếp. Cách "generated column + unique
-- index" (STORED column phụ thuộc vào room_id) BỊ LỖI trên nhiều bản MySQL 8 vì đây là
-- hạn chế/bug đã biết của InnoDB: không thể ADD một STORED generated column phụ thuộc
-- vào một cột đang là khóa ngoại (room_id là FK trong bảng này), dù có tắt
-- foreign_key_checks cũng không khắc phục được (lỗi xảy ra ở bước rebuild bảng nội bộ,
-- không phải do FK check thật sự).
--
-- => Ràng buộc "mỗi phòng chỉ có tối đa 1 hợp đồng ACTIVE" được chuyển sang thực thi
-- bằng TRIGGER (xem `trg_contract_before_insert` / `trg_contract_before_update` ở cuối
-- file), thay vì generated column + unique index.


-- =====================================================================================
-- 9. CONTRACT_TENANT — bảng trung gian N-N giữa Hợp đồng và Người thuê
--    is_representative = 1 đánh dấu "người đại diện ký hợp đồng" (đây là NGUỒN DỮ LIỆU
--    DUY NHẤT cho thông tin này — xem ghi chú đầu file).
-- =====================================================================================
CREATE TABLE contract_tenant (
    contract_id       BIGINT UNSIGNED NOT NULL,
    tenant_id         BIGINT UNSIGNED NOT NULL,
    is_representative TINYINT(1)      NOT NULL DEFAULT 0,
    joined_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (contract_id, tenant_id),
    CONSTRAINT fk_ct_contract FOREIGN KEY (contract_id) REFERENCES contract (id) ON DELETE CASCADE,
    CONSTRAINT fk_ct_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_ct_tenant ON contract_tenant (tenant_id);

-- Ràng buộc "mỗi hợp đồng chỉ có ĐÚNG 1 người đại diện ký hợp đồng" cũng gặp đúng lỗi
-- 1215 tương tự (contract_id vừa là PK vừa là FK trong bảng này). Chuyển sang thực thi
-- bằng TRIGGER (xem `trg_ct_before_insert` / `trg_ct_before_update` ở cuối file).


-- =====================================================================================
-- 10. MONTHLY_BILL — hóa đơn/chi phí theo tháng của 1 hợp đồng, có theo dõi công nợ
-- =====================================================================================
CREATE TABLE monthly_bill (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    contract_id       BIGINT UNSIGNED NOT NULL,
    bill_month        TINYINT UNSIGNED NOT NULL,
    bill_year         SMALLINT UNSIGNED NOT NULL,
    rent_amount       DECIMAL(15,0)   NOT NULL DEFAULT 0, -- đã tính theo tỷ lệ ngày ở nếu không tròn tháng
    total_extra_fee   DECIMAL(15,0)   NOT NULL DEFAULT 0,
    total_amount      DECIMAL(15,0)   GENERATED ALWAYS AS (rent_amount + total_extra_fee) STORED,
    paid_amount       DECIMAL(15,0)   NOT NULL DEFAULT 0,
    remaining_amount  DECIMAL(15,0)   GENERATED ALWAYS AS (rent_amount + total_extra_fee - paid_amount) STORED,
    payment_status    VARCHAR(30)     NOT NULL DEFAULT 'CHUA_THANH_TOAN',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_monthly_bill_period UNIQUE (contract_id, bill_year, bill_month),
    CONSTRAINT fk_monthly_bill_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
    CONSTRAINT chk_monthly_bill_month CHECK (bill_month BETWEEN 1 AND 12),
    CONSTRAINT chk_monthly_bill_status CHECK (
        payment_status IN ('CHUA_THANH_TOAN', 'THANH_TOAN_MOT_PHAN', 'DA_THANH_TOAN')
    ),
    CONSTRAINT chk_monthly_bill_paid CHECK (paid_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_monthly_bill_contract ON monthly_bill (contract_id);
CREATE INDEX idx_monthly_bill_period ON monthly_bill (bill_year, bill_month);
CREATE INDEX idx_monthly_bill_status ON monthly_bill (payment_status);

-- Lưu ý: total_amount / remaining_amount là GENERATED COLUMN (tự tính, không cho insert/update
-- trực tiếp). Khi thêm Payment mới, backend cập nhật lại paid_amount và payment_status
-- (xem trigger gợi ý ở cuối file, hoặc xử lý trong service layer bằng transaction).


-- =====================================================================================
-- 11. EXTRA_FEE_ITEM — chi tiết từng khoản chi phí phát sinh trong 1 hóa đơn tháng
-- =====================================================================================
CREATE TABLE extra_fee_item (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    monthly_bill_id       BIGINT UNSIGNED NOT NULL,
    extra_fee_category_id BIGINT UNSIGNED NOT NULL,
    amount                DECIMAL(15,0)   NOT NULL,
    note                  VARCHAR(300)    NULL,
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_efi_monthly_bill FOREIGN KEY (monthly_bill_id) REFERENCES monthly_bill (id) ON DELETE CASCADE,
    CONSTRAINT fk_efi_category FOREIGN KEY (extra_fee_category_id) REFERENCES extra_fee_category (id),
    CONSTRAINT chk_efi_amount CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_efi_monthly_bill ON extra_fee_item (monthly_bill_id);
CREATE INDEX idx_efi_category ON extra_fee_item (extra_fee_category_id);


-- =====================================================================================
-- 12. PAYMENT — lịch sử thanh toán (hỗ trợ thanh toán nhiều lần / công nợ)
--     Phương thức thanh toán (method) là free text theo yêu cầu, không ràng buộc CHECK.
-- =====================================================================================
CREATE TABLE payment (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    monthly_bill_id  BIGINT UNSIGNED NOT NULL,
    amount           DECIMAL(15,0)   NOT NULL,
    payment_date     DATE            NOT NULL,
    method           VARCHAR(100)    NULL,   -- free text: "tiền mặt", "chuyển khoản Vietcombank", ...
    note             VARCHAR(300)    NULL,
    created_by       BIGINT UNSIGNED NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_monthly_bill FOREIGN KEY (monthly_bill_id) REFERENCES monthly_bill (id),
    CONSTRAINT fk_payment_created_by FOREIGN KEY (created_by) REFERENCES account (id),
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_payment_monthly_bill ON payment (monthly_bill_id);
CREATE INDEX idx_payment_date ON payment (payment_date);


-- =====================================================================================
-- 13. CHECKOUT_CHECKLIST — checklist trả phòng / kết thúc hợp đồng (1 hợp đồng chỉ có
--     tối đa 1 checklist vì mỗi hợp đồng chỉ kết thúc 1 lần)
-- =====================================================================================
CREATE TABLE checkout_checklist (
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    contract_id             BIGINT UNSIGNED NOT NULL,
    checked_by              BIGINT UNSIGNED NULL,
    checked_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deposit_amount          DECIMAL(15,0)   NOT NULL, -- snapshot từ contract.deposit_amount tại thời điểm trả phòng
    deduction_amount        DECIMAL(15,0)   NOT NULL DEFAULT 0, -- tổng trừ do vật dụng hư hỏng/mất + công nợ còn lại
    deposit_refund_amount   DECIMAL(15,0)   NOT NULL DEFAULT 0, -- = GREATEST(deposit_amount - deduction_amount, 0)
    note                    VARCHAR(500)    NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_checklist_contract UNIQUE (contract_id),
    CONSTRAINT fk_checklist_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
    CONSTRAINT fk_checklist_checked_by FOREIGN KEY (checked_by) REFERENCES account (id),
    CONSTRAINT chk_checklist_deposit CHECK (deposit_amount >= 0),
    CONSTRAINT chk_checklist_deduction CHECK (deduction_amount >= 0),
    CONSTRAINT chk_checklist_refund CHECK (deposit_refund_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- =====================================================================================
-- 14. CHECKOUT_CHECKLIST_ITEM — chi tiết từng vật dụng khi kiểm tra trả phòng
--     Tham chiếu tới room_type_handover_item (vật dụng mẫu theo loại phòng)
-- =====================================================================================
CREATE TABLE checkout_checklist_item (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    checklist_id                BIGINT UNSIGNED NOT NULL,
    room_type_handover_item_id  BIGINT UNSIGNED NOT NULL,
    status                      VARCHAR(20)     NOT NULL,
    deduction_amount            DECIMAL(15,0)   NOT NULL DEFAULT 0,
    note                        VARCHAR(300)    NULL,

    CONSTRAINT fk_cci_checklist FOREIGN KEY (checklist_id) REFERENCES checkout_checklist (id) ON DELETE CASCADE,
    CONSTRAINT fk_cci_handover_item FOREIGN KEY (room_type_handover_item_id) REFERENCES room_type_handover_item (id),
    CONSTRAINT chk_cci_status CHECK (status IN ('CON_NGUYEN', 'HU_HONG', 'MAT')),
    CONSTRAINT chk_cci_deduction CHECK (deduction_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_cci_checklist ON checkout_checklist_item (checklist_id);


-- =====================================================================================
-- 15. DEBT_RECORD — công nợ phát sinh khi trả phòng vượt quá tiền cọc
--     (độc lập với monthly_bill, dùng để theo dõi thu hồi về sau)
-- =====================================================================================
CREATE TABLE debt_record (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    contract_id       BIGINT UNSIGNED NOT NULL,
    checklist_id      BIGINT UNSIGNED NULL,
    amount            DECIMAL(15,0)   NOT NULL,
    reason            VARCHAR(300)    NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'CHUA_THU',
    collected_amount  DECIMAL(15,0)   NOT NULL DEFAULT 0,
    note              VARCHAR(300)    NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_debt_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
    CONSTRAINT fk_debt_checklist FOREIGN KEY (checklist_id) REFERENCES checkout_checklist (id),
    CONSTRAINT chk_debt_status CHECK (status IN ('CHUA_THU', 'DA_THU')),
    CONSTRAINT chk_debt_amount CHECK (amount >= 0),
    CONSTRAINT chk_debt_collected CHECK (collected_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_debt_contract ON debt_record (contract_id);
CREATE INDEX idx_debt_status ON debt_record (status);


-- =====================================================================================
-- 16. REFRESH_TOKEN — lưu refresh token JWT để hỗ trợ revoke (Redis vẫn dùng cho
--     access-token blacklist theo spec; bảng này là lựa chọn lưu bền refresh token)
-- =====================================================================================
CREATE TABLE refresh_token (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id   BIGINT UNSIGNED NOT NULL,
    token_hash   VARCHAR(255)    NOT NULL,
    expires_at   DATETIME        NOT NULL,
    revoked      TINYINT(1)      NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_refresh_token_account ON refresh_token (account_id);
CREATE INDEX idx_refresh_token_expires ON refresh_token (expires_at);


-- =====================================================================================
-- VIEW: v_branch_room_summary — tổng số phòng & số phòng theo từng loại của 1 chi nhánh
--   (thay thế cho branch.total_rooms / branch.room_type_summary đã bỏ khỏi bảng branch)
-- =====================================================================================
CREATE OR REPLACE VIEW v_branch_room_summary AS
SELECT
    b.id                AS branch_id,
    b.name              AS branch_name,
    rt.id               AS room_type_id,
    rt.name             AS room_type_name,
    COUNT(r.id)         AS room_count,
    SUM(CASE WHEN r.status = 'TRONG' THEN 1 ELSE 0 END)      AS empty_room_count,
    SUM(CASE WHEN r.status = 'DANG_THUE' THEN 1 ELSE 0 END)  AS occupied_room_count
FROM branch b
JOIN room r ON r.branch_id = b.id
JOIN room_type rt ON rt.id = r.room_type_id
GROUP BY b.id, b.name, rt.id, rt.name;


-- =====================================================================================
-- TRIGGER GỢI Ý: tự động cập nhật payment_status của monthly_bill sau khi thêm Payment
--   (có thể để backend service layer xử lý trong transaction thay vì dùng trigger —
--    trigger ở đây chỉ là lựa chọn dự phòng đảm bảo tính nhất quán ở tầng DB)
-- =====================================================================================
DELIMITER $$

CREATE TRIGGER trg_payment_after_insert
AFTER INSERT ON payment
FOR EACH ROW
BEGIN
    UPDATE monthly_bill
    SET paid_amount = paid_amount + NEW.amount
    WHERE id = NEW.monthly_bill_id;

    UPDATE monthly_bill
    SET payment_status = CASE
        WHEN paid_amount <= 0 THEN 'CHUA_THANH_TOAN'
        WHEN paid_amount >= total_amount THEN 'DA_THANH_TOAN'
        ELSE 'THANH_TOAN_MOT_PHAN'
    END
    WHERE id = NEW.monthly_bill_id;
END$$

DELIMITER ;

-- =====================================================================================
-- TRIGGER: mỗi phòng chỉ có tối đa 1 hợp đồng đang ACTIVE tại một thời điểm
--   (thay thế cho cách "generated column + unique index" bị lỗi 1215 ở MySQL)
-- =====================================================================================
DELIMITER $$

CREATE TRIGGER trg_contract_before_insert
BEFORE INSERT ON contract
FOR EACH ROW
BEGIN
    IF NEW.status = 'ACTIVE' THEN
        IF EXISTS (
            SELECT 1 FROM contract
            WHERE room_id = NEW.room_id AND status = 'ACTIVE'
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Phòng này đã có 1 hợp đồng đang ACTIVE, không thể tạo thêm.';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_contract_before_update
BEFORE UPDATE ON contract
FOR EACH ROW
BEGIN
    IF NEW.status = 'ACTIVE' AND (OLD.status <> 'ACTIVE' OR OLD.room_id <> NEW.room_id) THEN
        IF EXISTS (
            SELECT 1 FROM contract
            WHERE room_id = NEW.room_id AND status = 'ACTIVE' AND id <> NEW.id
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Phòng này đã có 1 hợp đồng đang ACTIVE, không thể chuyển hợp đồng khác sang ACTIVE.';
        END IF;
    END IF;
END$$

DELIMITER ;


-- =====================================================================================
-- TRIGGER: mỗi hợp đồng chỉ có ĐÚNG 1 người đại diện ký hợp đồng (is_representative = 1)
--   (thay thế cho cách "generated column + unique index" bị lỗi 1215 ở MySQL)
-- =====================================================================================
DELIMITER $$

CREATE TRIGGER trg_ct_before_insert
BEFORE INSERT ON contract_tenant
FOR EACH ROW
BEGIN
    IF NEW.is_representative = 1 THEN
        IF EXISTS (
            SELECT 1 FROM contract_tenant
            WHERE contract_id = NEW.contract_id AND is_representative = 1
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Hợp đồng này đã có người đại diện ký hợp đồng.';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_ct_before_update
BEFORE UPDATE ON contract_tenant
FOR EACH ROW
BEGIN
    IF NEW.is_representative = 1 AND (OLD.is_representative <> 1 OR OLD.contract_id <> NEW.contract_id) THEN
        IF EXISTS (
            SELECT 1 FROM contract_tenant
            WHERE contract_id = NEW.contract_id
              AND is_representative = 1
              AND NOT (contract_id = OLD.contract_id AND tenant_id = OLD.tenant_id)
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Hợp đồng này đã có người đại diện ký hợp đồng.';
        END IF;
    END IF;
END$$

DELIMITER ;


-- =====================================================================================
-- END V1__init_schema.sql
-- =====================================================================================

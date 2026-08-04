SET NAMES utf8mb4;

-- =====================================================================================
-- 1. CHI NHÁNH
-- =====================================================================================
INSERT INTO branch (name, address, manager_account_id) VALUES
('Chi nhánh Quận 9', 'Quận 9', (SELECT id FROM account WHERE username = 'admin')),
('Chi nhánh Bình Dương', 'Bình Dương', (SELECT id FROM account WHERE username = 'admin'));

-- =====================================================================================
-- 2. VẬT DỤNG (ITEM) — theo từng chi nhánh
-- =====================================================================================
INSERT INTO item (branch_id, name, price, quantity_available, default_quantity_per_room)
SELECT b.id, x.name, x.price, x.qty, x.def
FROM branch b
JOIN (
    SELECT 'Tivi' AS name, 3500000 AS price, 15 AS qty, 1 AS def
    UNION ALL SELECT 'Tủ lạnh', 6000000, 35, 1
    UNION ALL SELECT 'Máy giặt', 6000000, 15, 1
    UNION ALL SELECT 'Bồn rửa tay(bếp)', 800000, 35, 1
    UNION ALL SELECT 'Bồn rửa tay(nhà vệ sinh)', 600000, 35, 1
    UNION ALL SELECT 'Vòi sen', 500000, 35, 1
    UNION ALL SELECT 'Bồn cầu', 1200000, 35, 1
    UNION ALL SELECT 'Đèn điện', 160000, 105, 3
) x
WHERE b.name = 'Chi nhánh Quận 9';

INSERT INTO item (branch_id, name, price, quantity_available, default_quantity_per_room)
SELECT b.id, x.name, x.price, x.qty, x.def
FROM branch b
JOIN (
    SELECT 'Tivi' AS name, 3500000 AS price, 20 AS qty, 1 AS def
    UNION ALL SELECT 'Tủ lạnh', 6000000, 50, 1
    UNION ALL SELECT 'Máy giặt', 6000000, 20, 1
    UNION ALL SELECT 'Bồn rửa tay(bếp)', 800000, 50, 1
    UNION ALL SELECT 'Bồn rửa tay(nhà vệ sinh)', 600000, 50, 1
    UNION ALL SELECT 'Vòi sen', 500000, 50, 1
    UNION ALL SELECT 'Bồn cầu', 1200000, 50, 1
    UNION ALL SELECT 'Đèn điện', 160000, 150, 3
) x
WHERE b.name = 'Chi nhánh Bình Dương';

-- =====================================================================================
-- 3. LOẠI PHÒNG — theo từng chi nhánh
-- =====================================================================================
INSERT INTO room_type (branch_id, name, area, description)
SELECT b.id, x.name, x.area, x.description
FROM branch b
JOIN (
    SELECT 'Có gác(TV&MG)' AS name, '4x4' AS area, 'có sẵn ti vi và máy giặt' AS description
    UNION ALL SELECT 'Có gác(Thường)', '4x4', NULL
) x
WHERE b.name IN ('Chi nhánh Quận 9', 'Chi nhánh Bình Dương');

-- =====================================================================================
-- 4. VẬT DỤNG BÀN GIAO theo loại phòng — quantity = default_quantity_per_room của item
-- =====================================================================================
-- Có gác(TV&MG): đủ 8 vật dụng
INSERT INTO room_type_handover_item (room_type_id, item_id, quantity)
SELECT rt.id, it.id, it.default_quantity_per_room
FROM room_type rt
JOIN item it ON it.branch_id = rt.branch_id
WHERE rt.name = 'Có gác(TV&MG)'
  AND it.name IN ('Tivi', 'Tủ lạnh', 'Máy giặt', 'Bồn rửa tay(bếp)', 'Bồn rửa tay(nhà vệ sinh)', 'Vòi sen', 'Bồn cầu', 'Đèn điện');

-- Có gác(Thường): không có Tivi/Máy giặt
INSERT INTO room_type_handover_item (room_type_id, item_id, quantity)
SELECT rt.id, it.id, it.default_quantity_per_room
FROM room_type rt
JOIN item it ON it.branch_id = rt.branch_id
WHERE rt.name = 'Có gác(Thường)'
  AND it.name IN ('Tủ lạnh', 'Bồn rửa tay(bếp)', 'Bồn rửa tay(nhà vệ sinh)', 'Vòi sen', 'Bồn cầu', 'Đèn điện');

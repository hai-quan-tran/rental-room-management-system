SET NAMES utf8mb4;

-- Chọn ngẫu nhiên 15 hợp đồng (trong 85) để thêm người ở ghép:
--   rn 1-10  -> thêm 1 người (tổng 2 người thuê/phòng)
--   rn 11-15 -> thêm 2 người (tổng 3 người thuê/phòng)
CREATE TEMPORARY TABLE tmp_extra_rooms AS
SELECT contract_id, ROW_NUMBER() OVER (ORDER BY RAND()) AS rn
FROM (SELECT id AS contract_id FROM contract ORDER BY RAND() LIMIT 15) t;

-- Người thuê chưa gắn với hợp đồng nào (tránh 1 người đứng tên 2 phòng cùng lúc)
CREATE TEMPORARY TABLE tmp_unused_tenants AS
SELECT id AS tenant_id, ROW_NUMBER() OVER (ORDER BY RAND()) AS rn
FROM tenant
WHERE id NOT IN (SELECT tenant_id FROM contract_tenant);

-- Thêm người thứ 2 cho cả 15 phòng đã chọn
INSERT INTO contract_tenant (contract_id, tenant_id, is_representative, joined_at)
SELECT er.contract_id, ut.tenant_id, 0, c.start_date
FROM tmp_extra_rooms er
JOIN tmp_unused_tenants ut ON ut.rn = er.rn
JOIN contract c ON c.id = er.contract_id;

-- Thêm người thứ 3 cho riêng 5 phòng rn 11-15
INSERT INTO contract_tenant (contract_id, tenant_id, is_representative, joined_at)
SELECT er.contract_id, ut.tenant_id, 0, c.start_date
FROM tmp_extra_rooms er
JOIN tmp_unused_tenants ut ON ut.rn = er.rn + 5
JOIN contract c ON c.id = er.contract_id
WHERE er.rn > 10;

DROP TEMPORARY TABLE tmp_extra_rooms;
DROP TEMPORARY TABLE tmp_unused_tenants;

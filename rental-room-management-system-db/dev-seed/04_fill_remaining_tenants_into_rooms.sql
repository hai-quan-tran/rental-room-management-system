SET NAMES utf8mb4;

-- Trong số phòng đang chỉ có 1 người thuê, chọn ngẫu nhiên 45 phòng để thêm người
-- (còn lại 25 phòng ngẫu nhiên khác vẫn giữ nguyên 1 người thuê).
CREATE TEMPORARY TABLE tmp_single_rooms AS
SELECT c.id AS contract_id, ROW_NUMBER() OVER (ORDER BY RAND()) AS rn
FROM contract c
JOIN (
    SELECT contract_id FROM contract_tenant GROUP BY contract_id HAVING COUNT(*) = 1
) single ON single.contract_id = c.id;

-- Toàn bộ người thuê chưa gắn phòng nào (sẽ dùng hết trong bước này)
CREATE TEMPORARY TABLE tmp_unused_tenants AS
SELECT id AS tenant_id, ROW_NUMBER() OVER (ORDER BY RAND()) AS rn
FROM tenant
WHERE id NOT IN (SELECT tenant_id FROM contract_tenant);

-- Bước 1: đảm bảo mỗi phòng trong 45 phòng được chọn có thêm ĐÚNG 1 người trước
-- (rn 1..45 của 2 bảng tạm khớp 1-1) -> các phòng này chắc chắn thoát khỏi nhóm "1 người".
INSERT INTO contract_tenant (contract_id, tenant_id, is_representative, joined_at)
SELECT sr.contract_id, ut.tenant_id, 0, c.start_date
FROM tmp_single_rooms sr
JOIN tmp_unused_tenants ut ON ut.rn = sr.rn
JOIN contract c ON c.id = sr.contract_id
WHERE sr.rn <= 45;

-- Bước 2: số người thuê còn lại (rn 46..95, tức 50 người) rải ngẫu nhiên vào 45 phòng đó
-- (1 phòng có thể nhận thêm nhiều hơn 1 người ở bước này).
CREATE TEMPORARY TABLE tmp_leftover_assign AS
SELECT ut.tenant_id, FLOOR(RAND() * 45) + 1 AS room_rn
FROM tmp_unused_tenants ut
WHERE ut.rn > 45;

INSERT INTO contract_tenant (contract_id, tenant_id, is_representative, joined_at)
SELECT sr.contract_id, la.tenant_id, 0, c.start_date
FROM tmp_leftover_assign la
JOIN tmp_single_rooms sr ON sr.rn = la.room_rn
JOIN contract c ON c.id = sr.contract_id;

DROP TEMPORARY TABLE tmp_single_rooms;
DROP TEMPORARY TABLE tmp_unused_tenants;
DROP TEMPORARY TABLE tmp_leftover_assign;

-- CCCD chỉ bắt buộc với người thuê từ 18 tuổi trở lên; dưới 18 tuổi không bắt buộc nhập.
-- Ràng buộc unique (uq_tenant_id_card) giữ nguyên — MySQL cho phép nhiều dòng NULL trong unique index.
ALTER TABLE tenant MODIFY COLUMN id_card_number VARCHAR(20) NULL;

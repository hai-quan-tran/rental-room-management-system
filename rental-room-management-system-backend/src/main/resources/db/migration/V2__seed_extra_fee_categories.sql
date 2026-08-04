-- =====================================================================================
-- V2__seed_extra_fee_categories.sql
-- Seed dữ liệu mặc định cho danh mục chi phí phát sinh (extra_fee_category)
-- =====================================================================================

INSERT IGNORE INTO extra_fee_category (name, unit) VALUES
    ('Điện', 'kWh'),
    ('Nước', 'm3'),
    ('Khác', NULL);

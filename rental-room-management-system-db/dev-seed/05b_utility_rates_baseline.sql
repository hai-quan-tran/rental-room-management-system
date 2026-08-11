-- Đơn giá điện/nước lịch sử, hiệu lực từ trước tháng hóa đơn sớm nhất (2/2026), để
-- 05_generate_monthly_bills.js tính tiền điện/nước cho toàn bộ khoảng 2-7/2026.
-- Không đụng 2 dòng utility_rate có sẵn (chi nhánh 1, hiệu lực từ 8/2026 — vẫn đúng cho tương lai).
INSERT INTO utility_rate (branch_id, extra_fee_category_id, unit_price, effective_from)
VALUES
  (1, 1, 3500, '2026-01-01'),  -- Quận 9 - Điện
  (1, 2, 20000, '2026-01-01'), -- Quận 9 - Nước
  (2, 1, 3500, '2026-01-01'),  -- Bình Dương - Điện
  (2, 2, 20000, '2026-01-01'); -- Bình Dương - Nước

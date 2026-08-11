-- Xóa sạch dữ liệu hóa đơn hiện có (test data còn sót từ các phiên trước) trước khi sinh lại đầy đủ
-- qua 05_generate_monthly_bills.js. KHÔNG đụng utility_rate/debt_record.
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM payment;
DELETE FROM extra_fee_item;
DELETE FROM meter_reading;
DELETE FROM monthly_bill;

ALTER TABLE payment AUTO_INCREMENT = 1;
ALTER TABLE extra_fee_item AUTO_INCREMENT = 1;
ALTER TABLE meter_reading AUTO_INCREMENT = 1;
ALTER TABLE monthly_bill AUTO_INCREMENT = 1;

SET FOREIGN_KEY_CHECKS = 1;

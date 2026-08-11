# TỔNG HỢP PHIÊN LÀM VIỆC 9: SEED LẠI DỮ LIỆU HÓA ĐƠN + KHÓA SỬA CHỈ SỐ KHI ĐÃ THANH TOÁN
*(Nối tiếp `tong-hop-phien-8-tu-dong-tinh-tien-dien-nuoc.md`. Phiên này gồm 2 phần độc lập: xóa và
sinh lại toàn bộ dữ liệu hóa đơn hằng tháng cho 85 phòng theo đúng công thức điện/nước Giai đoạn 1
đã build ở phiên 8, và 1 fix nghiệp vụ nhỏ ở màn "Nhập chỉ số điện nước".)*

---

## 1. Xóa & sinh lại dữ liệu hóa đơn hằng tháng cho toàn bộ 85 phòng

Yêu cầu: DB dev có 32 hóa đơn test rải rác, không đầy đủ, còn sót lại từ các phiên click-through
trước. Cần xóa sạch rồi sinh lại **1 hóa đơn/phòng/tháng, từ tháng nhận phòng đến hết tháng 7/2026**,
điện/nước tính hợp lý theo chỉ số công tơ (chỉ số khác biệt mỗi tháng ngẫu nhiên), toàn bộ đã thanh
toán đủ **trừ** mỗi chi nhánh chừa 3 phòng chưa thanh toán hóa đơn tháng 7 (2/3 phòng đó cũng chỉ
thanh toán 1 phần hóa đơn tháng 6).

### 1.1. Vào Plan Mode trước khi đụng vào DB thật
Vì đây là thao tác xóa dữ liệu (dù chỉ trên DB dev) + sinh khối lượng lớn dữ liệu tính toán, đã dùng
Plan Mode: đọc trực tiếp DB thật (`root/admin@127.0.0.1:3306`, xác nhận migration V4 đã áp dụng) và
đọc code thật (`RentCalculator`, `BillingService.seedMeteredExtraFeeItems`, `UtilityRateService
.findCurrentRate`, trigger `trg_payment_after_insert`) để tái hiện đúng công thức nghiệp vụ thay vì
đoán, rồi mới viết plan trình người dùng duyệt.

### 1.2. Sự cố nhỏ giữa chừng: `utility_rate` tự đổi giá trị
Khi chèn 4 dòng `utility_rate` lịch sử (đơn giá hiệu lực từ 2026-01-01, phủ khoảng cần sinh hóa đơn),
lệnh `INSERT` báo lỗi trùng khóa — kiểm tra lại thì 2 dòng `utility_rate` có sẵn của chi nhánh 1 (đọc
được lúc đầu phiên với `effective_from` 8/2026) đã tự đổi `effective_from` thành đúng giá trị
2026-01-01 mà không có `UPDATE` nào trong các lệnh SQL đã chạy. Đã báo lại cho người dùng thay vì tự
suy đoán — người dùng xác nhận đang thao tác song song trên DB. Xử lý: xóa 2 dòng đó, chèn lại sạch 4
dòng (Điện 3.500đ/kWh, Nước 20.000đ/m³, cả 2 chi nhánh, hiệu lực từ 2026-01-01).

### 1.3. Script dev-seed mới: `dev-seed/05_generate_monthly_bills.js` (+ `05a`/`05b`)
Theo đúng pattern các script dev-seed có sẵn từ phiên 5 (Node thuần, không cài thêm package) — điểm
khác: script này **đọc trực tiếp hợp đồng ACTIVE hiện có trong DB** qua `child_process.execSync`
gọi `mysql` CLI (không hardcode lại danh sách phòng).

- `05a_clear_bills.sql`: xóa `payment`/`extra_fee_item`/`meter_reading`/`monthly_bill` theo đúng thứ
  tự FK, reset `AUTO_INCREMENT`. Không đụng `utility_rate`/`debt_record`.
- `05b_utility_rates_baseline.sql`: 4 dòng đơn giá lịch sử nêu ở mục 1.2.
- `05_generate_monthly_bills.js`: với mỗi hợp đồng, lặp từng tháng từ lúc nhận phòng đến 7/2026 —
  tiền thuê tháng đầu tính theo tỷ lệ ngày ở (JS re-implement đúng `RentCalculator`, đã verify khớp
  100% với 1 dòng dữ liệu cũ), các tháng sau tính đủ; chỉ số điện/nước chạy chuỗi nối tiếp
  (`oldReading` tháng sau = `newReading` tháng trước, đúng hành vi `MeterReadingService` thật), tiêu
  thụ ngẫu nhiên có tỉ lệ theo số người ở trong phòng cho hợp lý; ghi `note` đúng format app thật
  (`"Tự động: chỉ số X→Y × Zđ"`). Chọn ngẫu nhiên 3 phòng/chi nhánh chưa thanh toán tháng 7, trong đó
  2 phòng cũng chỉ thanh toán 1 phần tháng 6 (40-70% tổng tiền), còn lại thanh toán đủ. Ghi SQL ra
  `seed_05_monthly_bills.sql` (gitignore, không commit) dùng `SET @bid = LAST_INSERT_ID()` để nối
  đúng FK giữa các bảng, bọc `START TRANSACTION`/`COMMIT`.
- **Bug đã gặp và fix**: `execSync` trên Windows chạy qua `cmd.exe`, cmd.exe xử lý sai chuỗi SQL
  nhiều dòng truyền vào `mysql -e "..."` — trả về **0 dòng, không báo lỗi gì cả** (im lặng sai, khó
  phát hiện). Fix: collapse SQL về 1 dòng (`sql.replace(/\s+/g, ' ')`) trước khi truyền cho `mysql
  -e`. Ghi chú lại trong code (`runQuery()`) vì đây là cạm bẫy sẽ gặp lại nếu viết script Node đọc DB
  tương tự trên máy Windows này.

### 1.4. Kết quả & verify
307 `monthly_bill`, 614 `meter_reading`, 614 `extra_fee_item`, 301 `payment`. Phân bố
`payment_status`: 297 `DA_THANH_TOAN`, 6 `CHUA_THANH_TOAN` (đúng 3 phòng/chi nhánh, tháng 7), 4
`THANH_TOAN_MOT_PHAN` (đúng 2 phòng/chi nhánh, tháng 6) — khớp 100% yêu cầu. Verify thêm: chuỗi
`meter_reading` không đứt gãy (0 broken link trên toàn bộ dữ liệu), số hóa đơn mỗi hợp đồng khớp
đúng số tháng kỳ vọng (0 sai lệch trên cả 85 hợp đồng).

---

## 2. Khóa sửa chỉ số điện/nước khi hóa đơn tháng đó đã thanh toán đủ

Yêu cầu: màn "Nhập chỉ số điện nước" trước đó luôn cho sửa chỉ số bất kể trạng thái hóa đơn. Cần chặn
nếu phòng đã có hóa đơn `DA_THANH_TOAN` cho đúng tháng đó.

- Trước đây: `MeterReadingService.upsertReading()` vẫn lưu được chỉ số mới, chỉ có
  `syncMeteredExtraFeeItem()` (gọi sau khi lưu) là bỏ qua không cập nhật số tiền lên hóa đơn (trả về
  `BillSyncStatus.SKIPPED_PAID`) — tức chỉ số bị đổi "âm thầm" dù không ảnh hưởng tới hóa đơn đã chốt.
- **Backend**: `upsertReading()` thêm guard `isBillFullyPaid(roomId, billYear, billMonth)` (query
  `MonthlyBillRepository.findByContract_Room_IdAndBillYearAndBillMonth`, kiểm tra có hóa đơn nào
  `DA_THANH_TOAN` không) — ném lỗi conflict ngay từ đầu, không cho lưu. `MeterReadingCellResponse`
  thêm field `billFullyPaid` để `listGrid()` cũng trả về cờ này cho từng ô, giúp frontend khóa input
  ngay từ khi tải trang thay vì phải bấm Lưu mới biết bị chặn. Thêm unit test
  `billAlreadyFullyPaid_rejectsReadingChange`.
- **Frontend**: 2 ô nhập (chỉ số cũ/mới) ở màn `/meter-readings` bị `[disabled]` khi
  `cell.billFullyPaid`, nút Lưu thay bằng icon khóa (`pi-lock`) kèm tooltip
  `METER_READING.BILL_FULLY_PAID_LOCKED` (đã thêm i18n vi/en).
- Compile-verify sạch: backend 175 file (javac 17 workaround), frontend `ng build` sạch (chỉ còn
  warning bundle-size cũ, không liên quan).

---

## 3. Việc còn lại / lưu ý cho phiên sau

- [ ] Chưa test click-through thật cho cả 2 phần trên (cần backend JDK 21 live) — mọi thứ mới compile
  -verify, giống hạn chế mọi phiên trước.
- [ ] Nếu backend đang chạy live song song lúc agent thao tác DB (như sự cố mục 1.2), cần cẩn thận
  hơn — luôn đọc lại DB ngay trước khi ghi để tránh xung đột dữ liệu.
- [ ] Dữ liệu demo (307 hóa đơn) chỉ tồn tại trong DB dev hiện tại, không phải Flyway migration —
  script sinh lại đã lưu ở `rental-room-management-system-db/dev-seed/` (bước 5 trong README), chạy
  lại được nếu DB bị xóa/tạo lại (dữ liệu sinh ra ngẫu nhiên khác mỗi lần chạy, giống các script
  dev-seed khác).

---

*File này bổ sung cho `tong-hop-phien-8-tu-dong-tinh-tien-dien-nuoc.md` và các file trước đó — đọc từ
file mới nhất lùi dần nếu cần đầy đủ ngữ cảnh.*

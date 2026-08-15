# TỔNG HỢP PHIÊN LÀM VIỆC 11: TRẠNG THÁI "CHƯA XÁC NHẬN" + KHÓA CHỈNH SỬA HÓA ĐƠN
*(Nối tiếp `tong-hop-phien-10-man-hinh-nhan-vien-va-lien-ket-account.md`. Phiên này sửa 1 lỗ hổng
nghiệp vụ thật: hóa đơn đã gửi cho người thuê nhưng chưa thanh toán hết vẫn có thể bị sửa số tiền,
gây sai lệch giữa số đã gửi và số thực tế trong hệ thống.)*

---

## 0. Bối cảnh & yêu cầu

Người dùng phát hiện: `MonthlyBill.paymentStatus` trước đây chỉ có 3 giá trị (chưa thanh toán/thanh
toán một phần/đã thanh toán), và chi phí phát sinh (điện/nước/wifi/...) có thể bị thêm/xóa tự do
miễn hóa đơn chưa `DA_THANH_TOAN`. Yêu cầu: thêm trạng thái mới **"Chưa xác nhận"** làm trạng thái
khởi tạo — hóa đơn mới tạo ở trạng thái này được sửa thoải mái; sau khi nhập đủ chi phí và bấm
"Xác nhận" thì chuyển sang "Chưa thanh toán" và **khóa hoàn toàn**, không sửa được gì nữa (chỉ còn
ghi nhận thanh toán). Xác nhận có thể làm từng hóa đơn (vào Room Detail) hoặc hàng loạt bằng
checkbox ở màn "Hóa đơn hàng tháng".

Đã dùng Plan Mode: 2 Explore agent song song rà soát backend (`BillingService`, trigger
`trg_payment_after_insert`, `MeterReadingService`'s paid-lock có sẵn từ phiên 9) và frontend
(`monthly-bills-page`, `room-detail-page`, `status-severity.util.ts`) trước khi viết plan.

---

## 1. Phát hiện quan trọng: trigger DB có thể "lách" bước xác nhận

`trg_payment_after_insert` (chạy sau mỗi INSERT vào bảng `payment`) tự tính lại `payment_status` dựa
trên `paid_amount` so với `total_amount`, không biết gì về `CHUA_XAC_NHAN`. Nếu cho phép ghi nhận
thanh toán khi hóa đơn còn chưa xác nhận, trigger sẽ tự động đẩy status sang trạng thái đã xác nhận
mà **không qua bước bấm "Xác nhận" thủ công** — phá vỡ đúng bất biến tính năng này cần đảm bảo. Vì
vậy `recordPayment` bắt buộc phải chặn khi hóa đơn còn `CHUA_XAC_NHAN`, không chỉ là UX mà là điều
kiện bắt buộc để tính năng đúng.

## 2. Backend

### 2.1. Database — migration mới `V7__monthly_bill_confirm_status.sql`
Mở rộng CHECK constraint `chk_monthly_bill_status` thêm `CHUA_XAC_NHAN`, đổi default cột
`payment_status` thành `CHUA_XAC_NHAN`. **Không backfill** dữ liệu cũ — mọi hóa đơn đã seed (phiên 9)
đang giữ 1 trong 3 status cũ, dưới mô hình mới đều đồng nghĩa "đã xác nhận" (đã qua cổng
`CHUA_XAC_NHAN`), vẫn đúng ngữ nghĩa và tự động bị khóa như hóa đơn đã xác nhận thật.

### 2.2. `PaymentStatus` + `MonthlyBill`
Thêm `CHUA_XAC_NHAN` làm giá trị đầu enum; `MonthlyBill.paymentStatus` default đổi theo.

### 2.3. `BillingService`
- `assertNotFullyPaid` đổi tên thành `assertEditable`, điều kiện đổi từ `== DA_THANH_TOAN` (chỉ khóa
  khi đã thanh toán đủ) sang `!= CHUA_XAC_NHAN` (khóa ngay khi đã xác nhận) — đây là **thắt chặt hành
  vi thật**, không chỉ đổi tên: hóa đơn `CHUA_THANH_TOAN` trước đây sửa được, giờ bị khóa trừ khi vẫn
  còn `CHUA_XAC_NHAN`.
- `recordPayment` thêm guard chặn khi `paymentStatus == CHUA_XAC_NHAN` (xem mục 1).
- Thêm `confirmBill(billId)` (chặn xác nhận 2 lần) và `confirmBulk(billIds)` (đếm `skippedCount` cho
  hóa đơn đã xác nhận trước đó, không coi là lỗi) + endpoint `PATCH /api/monthly-bills/{id}/confirm`
  và `PATCH /api/monthly-bills/confirm-bulk` — theo khuôn `DebtRecordService.collect`.
- `syncMeteredExtraFeeItem` đổi điều kiện skip từ `== DA_THANH_TOAN` sang `!= CHUA_XAC_NHAN`;
  `BillSyncStatus.SKIPPED_PAID` đổi tên thành `SKIPPED_CONFIRMED`.

### 2.4. `MeterReadingService` — mở rộng khóa sang "đã xác nhận"
Chỉ số điện/nước nuôi thẳng vào hóa đơn qua `syncMeteredExtraFeeItem` — nếu không khóa cùng lúc, sửa
chỉ số sau khi xác nhận vẫn âm thầm đổi tiền điện/nước trên hóa đơn đã khóa, vô hiệu hóa toàn bộ tính
năng. `isBillFullyPaid` đổi tên `isBillLocked`, điều kiện `!= CHUA_XAC_NHAN`; field response
`billFullyPaid` đổi tên `billLocked`.

### 2.5. `DashboardRepository`
`unpaidInvoiceRooms` lọc theo `remaining_amount > 0`, không theo status — hóa đơn mới tạo còn chưa
xác nhận cũng có `remaining_amount > 0` nên lọt vào bảng "phòng chưa thanh toán" dù chưa hề gửi cho
khách. Thêm điều kiện `AND mb.payment_status != 'CHUA_XAC_NHAN'`.

### 2.6. Test
Thêm case cho `BillingServiceTest` (confirm thành công/2 lần, chặn sửa/xóa chi phí và ghi nhận thanh
toán đúng theo trạng thái, `confirmBulk` đếm đúng skipped) và `MeterReadingServiceTest` (khóa theo
trạng thái xác nhận, không chỉ theo đã thanh toán đủ).

---

## 3. Frontend

- `PaymentStatus` enum thêm `CHUA_XAC_NHAN`; `status-severity.util.ts` thêm severity `'secondary'`
  cho trạng thái mới + 2 hàm dùng chung mới `canEditBillItems`/`canRecordPayment`, thay 4 chỗ so sánh
  chuỗi cứng `paymentStatus !== 'DA_THANH_TOAN'` lặp lại ở `monthly-bills-page`/`room-detail-page`.
- `billing.service.ts`/`billing.model.ts`: thêm `confirmBill`/`confirmBulk` + model
  `BulkBillConfirmResult`.
- **Room Detail** (tab Hóa đơn & Công nợ): thêm nút "Xác nhận hóa đơn", chỉ hiện khi còn
  `CHUA_XAC_NHAN`; khu vực thanh toán ẩn hẳn khi chưa xác nhận (chưa tới lúc thu tiền).
- **Màn "Hóa đơn hàng tháng"**: thêm checkbox chọn nhiều dòng (chỉ chọn được hóa đơn còn chưa xác
  nhận) + nút "Xác nhận hàng loạt" → dialog kết quả đếm xác nhận/bỏ qua. Nhân tiện **xóa hẳn signal
  `detailEditable`** — cờ này trước đây chỉ để phân biệt "mở từ bảng bulk-create" (cho sửa chi phí)
  với "mở từ bảng chính" (chỉ cho xem thanh toán), nay đã có khái niệm thật (`canEditBillItems` theo
  status) nên giữ `detailEditable` sẽ tái tạo đúng bug ban đầu — 1 hóa đơn chưa xác nhận mở lại từ
  bảng chính (không phải ngay sau khi bulk-create) sẽ không cho sửa dù đáng lẽ phải cho.
- **`/meter-readings`**: đổi tên `EditableCell.billFullyPaid` → `billLocked` xuyên suốt (model,
  component, template `[disabled]`, icon khóa) khớp field backend đổi tên.
- i18n: thêm đủ key `BILLING.STATUS_CHUA_XAC_NHAN`/`CONFIRM_BILL_*`/`BILL_LOCKED_NOTICE`,
  `MONTHLY_BILLS.CONFIRM_BULK_*`, đổi `METER_READING.BILL_FULLY_PAID_LOCKED` → `BILL_LOCKED`,
  `SYNC_SKIPPED_PAID` → `SYNC_SKIPPED_CONFIRMED` (cả `vi.json` và `en.json`).

---

## 4. Kiểm thử đã làm trong môi trường agent

- Backend: compile-verify sạch (main + test) bằng recipe `mvn -o dependency:build-classpath` +
  `javac --release 17 -encoding UTF-8` — không có JDK 21 trong môi trường agent nên chưa chạy
  `mvn clean test`/Spring context/DB thật.
- Frontend: `ng build` sạch, chỉ còn cảnh báo bundle-size initial đã tồn tại từ trước (không liên
  quan thay đổi phiên này).

---

## 5. Việc còn lại / lưu ý cho phiên sau

- [ ] **Chạy migration V7 + `mvn clean test` thật bằng JDK 21** trên máy người dùng, restart backend,
  rebuild frontend (`ng build`) — mọi thứ trong phiên này mới compile-verify, chưa chạy live.
- [ ] **Đang nghi vấn 1 bug live**: người dùng báo hóa đơn đã `DA_THANH_TOAN` vẫn cho sửa chỉ số điện
  nước ở `/meter-readings`. Rà soát code trong repo (cả backend `isBillLocked` lẫn frontend
  `cell.billLocked`) đều đúng logic mới — nghi ngờ cao nhất là lệch phiên bản giữa backend/frontend
  đang chạy thật (1 bên chưa rebuild nên field JSON tên cũ `billFullyPaid` gặp field tên mới
  `billLocked` phía kia → `undefined` → bị coi là không khóa). Người dùng xác nhận **chưa kiểm tra**
  trạng thái deploy — đã hướng dẫn xem Network tab + rebuild/restart cả 2 phía, còn chờ phản hồi để
  xác nhận đã hết bug hay cần đào sâu thêm.
- [ ] Chưa test click-through thật: tạo hóa đơn → sửa chi phí phát sinh → xác nhận (đơn lẻ ở Room
  Detail, hàng loạt bằng checkbox ở Hóa đơn hàng tháng) → xác nhận khóa đúng cả chi phí phát sinh lẫn
  chỉ số điện nước → ghi nhận thanh toán vẫn hoạt động sau xác nhận → Dashboard "phòng chưa thanh
  toán" không hiện hóa đơn còn chưa xác nhận.
- [ ] **Thay đổi hành vi thật với dữ liệu đã seed (phiên 9)**: mọi hóa đơn ở trạng thái chưa/thanh
  toán một phần (không phải chưa xác nhận) từ giờ không sửa được chi phí phát sinh nữa — trước đây
  sửa được tới khi thanh toán đủ. Đây là đúng mục đích phiên này, không phải regression khi verify.
- [ ] Cố tình chưa xử lý: `BillingService.upsertRentForCheckoutMonth` (gọi từ `CheckoutService` khi
  trả phòng) vẫn có thể cập nhật `rentAmount` của hóa đơn tháng cuối kể cả khi đã xác nhận — đây là
  recompute hệ thống gắn sự kiện trả phòng thật, không phải sửa tay, tạm để nguyên.
- [ ] Zalo integration, Giai đoạn 2-3 tự động hóa điện/nước không thuộc phạm vi phiên này, vẫn treo
  từ các phiên trước.

---

*File này bổ sung cho `tong-hop-phien-10-man-hinh-nhan-vien-va-lien-ket-account.md` và các file
trước đó — đọc từ file mới nhất lùi dần nếu cần đầy đủ ngữ cảnh.*

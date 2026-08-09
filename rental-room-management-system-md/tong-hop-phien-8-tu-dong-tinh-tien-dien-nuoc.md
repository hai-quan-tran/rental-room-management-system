# TỔNG HỢP PHIÊN LÀM VIỆC 8: NGHIÊN CỨU TÍCH HỢP ZALO + TỰ ĐỘNG TÍNH TIỀN ĐIỆN NƯỚC (GIAI ĐOẠN 1)
*(Nối tiếp `tong-hop-phien-7-dashboard-real-api-va-fix-redis.md`. Phiên này gồm 2 phần độc lập:
một đợt nghiên cứu thuần túy (chưa code) về tích hợp gửi tin Zalo, và triển khai đầy đủ Giai đoạn 1
của việc tự động hóa tính phí điện/nước — từ phân tích hiện trạng, lên plan, đến code xong cả
backend lẫn frontend.)*

---

## 1. Nghiên cứu: gửi tin nhắn qua Zalo (chỉ nghiên cứu, chưa code)

Yêu cầu: tìm hiểu phương án gửi tin Zalo để tích hợp thông báo cho người thuê (nhắc hóa đơn, xác
nhận thanh toán...), chưa cần code.

- **2 hướng chính**: Zalo OA API (nhắn qua Official Account, miễn phí trong khung 48h sau tương tác
  của người dùng, nhưng bắt buộc người thuê phải follow OA trước — không thực tế với người thuê trọ)
  vs. **Zalo ZNS** (gửi thông báo có mẫu (template) đã duyệt trước thẳng tới số điện thoại, không
  cần follow OA, tính phí ~200-800đ/tin thành công, cần OA xác thực doanh nghiệp + Zalo Business
  Account).
- **Khuyến nghị**: ZNS phù hợp hơn cho use case của dự án (nhắc hóa đơn/công nợ, xác nhận thanh
  toán, hợp đồng sắp hết hạn) vì hệ thống đã có sẵn `Tenant.phoneNumber`, không phụ thuộc follow OA.
- Phác thảo kiến trúc khi triển khai thật: module backend gọi OAuth Zalo (access/refresh token),
  kích hoạt từ `BillingService`/`ContractService`/`CheckoutService`, chạy bất đồng bộ, có bảng log
  gửi tin.
- **Chưa quyết định** hướng chính thức, danh sách sự kiện cần gửi tin, hay ai đứng ra xác thực OA
  doanh nghiệp — để ngỏ cho phiên sau. Chi tiết đầy đủ lưu trong memory `project_zalo_integration_research.md`.

---

## 2. Phân tích hiện trạng tính phí điện/nước + đề xuất giải pháp

Trước khi code, đã xác nhận qua đọc code thật (không đoán): phí điện/nước hiện tại **không có công
thức tính nào cả** — `extra_fee_category` chỉ có `unit` là nhãn hiển thị ("kWh"/"m3", không tham gia
tính toán), `extra_fee_item.amount` là số tiền gõ tay tự do y hệt "Wifi"/"Khác". Không có chỉ số công
tơ, không có đơn giá lưu trữ, không có gì thu thập tự động — mọi số liệu đến từ quy trình hoàn toàn
ngoài hệ thống (đi ghi số bằng tay).

Đề xuất giải pháp theo 3 giai đoạn:
1. **Giai đoạn 1** (đã triển khai trong phiên này — xem mục 3): model hóa đơn giá + chỉ số, vẫn ghi
   tay nhưng hệ thống tự tính tiền.
2. **Giai đoạn 2**: đính kèm ảnh chụp đồng hồ khi ghi chỉ số (xác minh, giảm sai sót) — chưa làm.
3. **Giai đoạn 3**: đồng hồ điện/nước IoT tự động gửi số về hệ thống — chưa làm, cần khảo sát chi
   phí thiết bị khi quy mô đủ lớn.

Chi tiết đầy đủ lưu trong memory `project_utility_billing_analysis.md`.

---

## 3. Triển khai Giai đoạn 1: đơn giá điện/nước + chỉ số công tơ → tự tính tiền hóa đơn

### 3.1. Yêu cầu chốt trước khi code
Khi tạo hóa đơn tháng (đơn lẻ ở Room Detail hoặc hàng loạt ở màn Hóa đơn hằng tháng) phải **luôn tự
động có sẵn 2 dòng "Điện"/"Nước"** — tự tính tiền nếu đã có chỉ số + đơn giá, để 0đ kèm ghi chú nếu
chưa có, admin vẫn sửa tay được như hiện tại. Đã lên plan chi tiết qua Plan Mode (2 Explore agent +
1 Plan agent phản biện thiết kế) trước khi code.

### 3.2. Database — migration mới `V4__utility_rates_meter_readings.sql`
Đặt ở cả `rental-room-management-system-db/` và `backend/src/main/resources/db/migration/`.

- `extra_fee_category` thêm cột `is_metered` (bật cho Điện/Nước) — dùng cờ dữ liệu thay vì so khớp
  tên "Điện"/"Nước" trong code Java, vì category đã có CRUD đổi tên qua API sẵn có (dù chưa có UI
  frontend dùng tới) nên hardcode tên sẽ rủi ro âm thầm hỏng logic nếu bị đổi tên.
- Bảng mới **`utility_rate`**: đơn giá điện/nước theo từng chi nhánh, có `effective_from` (ngày hiệu
  lực) — đổi giá = thêm 1 dòng mới, không sửa/xóa dòng cũ, đúng pattern "snapshot tại thời điểm tạo
  hóa đơn" đã dùng cho `rentAmount`/`wifiFee`.
- Bảng mới **`meter_reading`**: chỉ số cũ/mới theo từng phòng theo từng tháng, gắn theo `room_id`
  (không phải `contract_id`, vì đồng hồ là hạ tầng vật lý của phòng — giống `room.wifiFee`).

### 3.3. Backend
- `BillingService.createBill`/`bulkCreate`: thêm `seedMeteredExtraFeeItems()` — với mỗi category
  `is_metered=true`, tìm `MeterReading` + đơn giá hiện hành của chi nhánh tại tháng hóa đơn, tạo 1
  dòng `ExtraFeeItem` (amount = tiêu thụ × đơn giá, làm tròn `HALF_UP`; 0đ + ghi chú nếu thiếu dữ
  liệu — không bao giờ throw lỗi, tránh 1 chi nhánh quên cấu hình giá làm hỏng cả loạt hóa đơn).
- `BillingService.syncMeteredExtraFeeItem()` (package-private): khi 1 chỉ số được ghi/sửa **sau
  khi** hóa đơn đã tồn tại (luồng tạo hàng loạt trước, ghi chỉ số sau), cập nhật lại đúng dòng
  Điện/Nước đã có trên hóa đơn đó — trả về `BillSyncStatus` (`UPDATED`/`SKIPPED_PAID`/
  `SKIPPED_AMBIGUOUS_CONTRACT`/`NO_BILL_YET`) để không "âm thầm" bỏ qua khi hóa đơn đã thanh toán
  đủ hoặc khi phòng có 2 hóa đơn trùng tháng (đổi hợp đồng giữa tháng).
- `UtilityRateService`/`Controller` mới (branch-scoped, theo đúng pattern `ItemService`): chỉ
  thêm, không sửa/xóa đơn giá.
- `MeterReadingService`/`Controller` mới: API grid xem/nhập chỉ số theo chi nhánh+tháng (1 dòng/1
  phòng đang có hợp đồng active, cột sinh động theo category `is_metered`, không hardcode 2 cột
  điện/nước) + API lưu 1 chỉ số/phòng (chỉ số cũ tự động lấy từ **lần ghi gần nhất trước đó**, không
  nhất thiết đúng tháng liền kề, để không vỡ nếu có tháng bị bỏ sót).
- `ExtraFeeCategoryService.delete()`: thêm guard chặn xóa category đang có đơn giá/chỉ số tham chiếu.
- Unit test mới (Mockito, không Spring context): `UtilityRateServiceTest`, `MeterReadingServiceTest`
  (auto-chain chỉ số cũ qua tháng bị bỏ sót, làm tròn tiền, case thiếu rate/reading, case hóa đơn đã
  thanh toán, case N>1 hóa đơn trùng phòng/tháng), `BillingServiceTest` (seed đúng 2 dòng khi tạo
  hóa đơn, có/không có dữ liệu).

### 3.4. Frontend
- Model/service mới: `utility-rate.model/service.ts`, `meter-reading.model/service.ts`; thêm
  `metered` vào `ExtraFeeCategoryResponse`.
- **Màn mới "Đơn giá điện nước"** (`/utility-rates`): filter chi nhánh, bảng lịch sử giá, form thêm
  dòng inline — chỉ thêm không sửa/xóa, đúng convention "add-only" hệ thống đang dùng cho
  `ExtraFeeItem`/`Payment`.
- **Màn mới "Nhập chỉ số điện nước"** (`/meter-readings`): filter chi nhánh + tháng, bảng 1 dòng/1
  phòng, cột sinh động theo category trả về từ API — admin chỉ cần gõ chỉ số mới (chỉ số cũ tự hiện
  sẵn), xem trước tiêu thụ/thành tiền tính live trước khi lưu, nút Lưu riêng từng ô; toast sau khi
  lưu phản ánh đúng `billSyncStatus` trả về (đã cập nhật hóa đơn / hóa đơn chưa tạo / hóa đơn đã
  thanh toán nên chưa cập nhật / phòng có nhiều hóa đơn trùng tháng nên cần cập nhật tay).
- `monthly-bills-page`/`room-detail-page` **không cần sửa gì** — 2 dòng Điện/Nước tự sinh ra hiển
  thị qua đúng UI add/xóa chi phí phát sinh đã có sẵn.
- Thêm route + `roleGuard` (cả 2 role, giống `items`) + mục sidebar + đầy đủ key i18n vi/en cho 2
  màn mới; đồng thời sửa lại nội dung `MONTHLY_BILLS.BULK_CREATE_CONFIRM` (vi+en) vì câu cũ nói
  "điện/nước vẫn cần nhập riêng" không còn đúng sau thay đổi này.

### 3.5. Kiểm thử đã làm trong môi trường agent
- Backend: compile-verify sạch bằng recipe `mvn -o dependency:build-classpath` (cả compile lẫn test
  scope) + `javac --release 17 -encoding UTF-8` (đã dùng từ phiên 7) — **175 file main + 10 file
  test compile sạch, 0 lỗi**. Lưu ý: lần đầu chạy thiếu `-encoding UTF-8` gây hàng loạt lỗi
  "unmappable character" giả (không phải bug thật) vì javac mặc định đọc theo windows-1252 trên máy
  này — thêm cờ là hết.
- Frontend: `ng build` sạch, chỉ còn cảnh báo bundle-size initial (712KB > 500KB budget) đã tồn tại
  từ trước, không phải do phiên này gây ra.

---

## 4. Việc còn lại / lưu ý cho phiên sau

- [ ] **Cần chạy migration V4 + `mvn clean test` thật trên máy JDK 21 của người dùng** — mọi thứ
  trong phiên này mới chỉ compile-verify bằng javac 17, chưa chạy Spring context/DB thật.
- [ ] Cần vào màn "Đơn giá điện nước" cấu hình giá cho từng chi nhánh trước khi thấy số tiền tự tính
  đúng — nếu chưa cấu hình, dòng Điện/Nước vẫn xuất hiện nhưng ở mức 0đ kèm ghi chú.
- [ ] Chưa test click-through thật (nhập chỉ số → tạo hóa đơn đơn lẻ/hàng loạt → xác nhận đúng số
  tiền; ghi chỉ số **sau** khi đã tạo hóa đơn hàng loạt để xác nhận đồng bộ lại đúng) — cần backend
  live thật để test, giống hạn chế mọi phiên trước.
- [ ] Zalo: chưa quyết định OA API hay ZNS, chưa xác định danh sách sự kiện cần gửi tin, chưa có ai
  đứng ra đăng ký xác thực OA doanh nghiệp — thuần túy nghiên cứu, chưa có dòng code nào.
- [ ] Giai đoạn 2 (ảnh xác minh chỉ số) và Giai đoạn 3 (đồng hồ IoT tự động) của việc tự động hóa
  điện/nước vẫn chưa làm, chỉ mới có đề xuất hướng.

---

*File này bổ sung cho `tong-hop-phien-7-dashboard-real-api-va-fix-redis.md` và các file trước đó —
đọc từ file mới nhất lùi dần nếu cần đầy đủ ngữ cảnh.*

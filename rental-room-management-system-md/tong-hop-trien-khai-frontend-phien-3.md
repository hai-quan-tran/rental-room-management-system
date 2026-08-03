# TỔNG HỢP PHIÊN LÀM VIỆC 3: HOÀN THIỆN NGHIỆP VỤ HỢP ĐỒNG/HÓA ĐƠN + MÀN HÓA ĐƠN HẰNG THÁNG + TẠO HÀNG LOẠT
*(File này gộp toàn bộ nội dung đã làm trong phiên làm việc thứ 3 — nối tiếp `tong-hop-trien-khai-frontend-phien-2.md`. Đọc kèm `tong-hop-du-an-quan-ly-phong-tro.md` và `tong-hop-trien-khai-backend.md` để có bối cảnh spec và API đầy đủ.)*

---

## 0. Phạm vi phiên này

Phiên 2 đã xây xong Detail/Create/Edit cho mọi entity + Room Detail 4 tab (Hợp đồng/Hóa đơn/Trả phòng). Phiên này tập trung:
1. Sửa các lỗi UX/nghiệp vụ phát hiện khi dùng thử thực tế màn Room Detail.
2. Hoàn thiện nghiệp vụ hợp đồng (ngày kết thúc, lịch sử, công nợ, người thuê cũ) và hóa đơn/thanh toán (khóa chặt khi đã thanh toán đủ, chặn vượt số tiền nợ).
3. Xây màn hình mới **Hóa đơn hằng tháng của các phòng** (`/monthly-bills`) — xem hóa đơn mọi phòng theo tháng, lọc theo chi nhánh.
4. Bổ sung tính năng **tạo hóa đơn hàng loạt** cho màn hình trên.

---

## 1. Sửa lỗi & cải thiện nhỏ

- **Số điện thoại**: bỏ yêu cầu `+84`, chỉ cần đúng định dạng VN 10 số (`0` + đầu số `3/5/7/8/9` + 8 số) — regex trong `TenantRequest.java` (backend).
- **Room Detail bị co hẹp**: màn hình dùng chung class `.detail-page` (max-width 48rem, hợp lý cho form đơn giản) nhưng Room Detail có 4 tab đầy bảng nên bị bó hẹp vô lý — thêm modifier `.detail-page--wide` (bỏ max-width) chỉ áp cho Room Detail, các màn Detail đơn giản khác giữ nguyên.
- **Tìm người thuê hiện "[object Object]"**: PrimeNG v21 `p-autocomplete` dùng input `optionLabel`, không phải `field` (API cũ của bản v17) — đã sửa cả 2 chỗ dùng autocomplete tìm người thuê trong `room-detail-page.html`.

---

## 2. Hợp đồng: ngày kết thúc, lịch sử, công nợ, người thuê cũ

- **Ngày kết thúc hợp đồng**: trước đây `endDate` chỉ được set lúc trả phòng. Đã thêm:
  - Field `endDate` (tùy chọn) khi tạo hợp đồng mới.
  - Endpoint riêng `PUT /api/contracts/{id}/end-date` để sửa ngày kết thúc bất kỳ lúc nào khi hợp đồng còn `ACTIVE` (validate `endDate >= startDate`, xóa được để đưa về "chưa xác định").
- **Lịch sử hợp đồng** (tab Hợp đồng của Room Detail) trước đây chỉ hiện ngày bắt đầu/kết thúc/cọc, không rõ nợ tháng nào — đã thêm:
  - Nút **"Xem hóa đơn"**: dialog liệt kê toàn bộ `MonthlyBill` của hợp đồng cũ đó kèm `remainingAmount` và trạng thái thanh toán, để biết ngay tháng nào còn nợ bao nhiêu.
  - Nút **"Xem người thuê"**: dialog liệt kê danh sách người thuê từng ở trong hợp đồng cũ đó (kèm tag người đại diện).
- **Tạo hợp đồng mới**: tiền cọc mặc định tự điền = giá thuê 1 tháng của phòng (vẫn sửa được thủ công).
- **Màn Công nợ** (`/debt-records`): thêm cột "Phòng" (`roomCode — branchName`) — backend `DebtRecordResponse` bổ sung `roomId`/`roomCode`/`branchName`.

---

## 3. Hóa đơn & thanh toán — khóa nghiệp vụ chặt hơn

- Dialog ghi nhận thanh toán hiện thêm **"Tổng tiền cần thanh toán"** và **"Số tiền chưa thanh toán"**.
- **Chặn thanh toán vượt quá số tiền còn nợ** — cả 2 lớp:
  - Backend: `BillingService.recordPayment()` ném lỗi `VALIDATION_ERROR` nếu `amount > remainingAmount`.
  - Frontend: input `[max]` = remainingAmount, nút disable + lỗi inline khi nhập vượt.
- **Hóa đơn đã `DA_THANH_TOAN` bị khóa hoàn toàn** (áp dụng ở cả Room Detail lẫn màn Hóa đơn hằng tháng):
  - Ẩn form ghi nhận thanh toán (thay bằng thông báo "đã thanh toán đủ").
  - Ẩn form/nút thêm-xóa chi phí phát sinh — trước đây Room Detail vẫn cho sửa dù đã thanh toán đủ, đây là lỗ hổng đã đóng lại.
  - Backend thêm guard `assertNotFullyPaid()` trong `addExtraFeeItem`/`deleteExtraFeeItem` để chặn cả khi bypass UI.
- Màn Hóa đơn hằng tháng: sau khi ghi nhận thanh toán thành công, dialog **tự đóng**.

---

## 4. Danh mục chi phí phát sinh — seed dữ liệu

Bảng `extra_fee_category` trước đây trống hoàn toàn (không ai từng seed) khiến dropdown "Chi phí phát sinh" rỗng. Đã thêm migration `V2__seed_extra_fee_categories.sql` (Điện/kWh, Nước/m3, Wifi, Khác — đúng 4 mục theo yêu cầu, dùng `INSERT IGNORE` để chạy lại an toàn). Đặt file ở cả `rental-room-management-system-db` (nguồn gốc) và copy vào `backend/src/main/resources/db/migration/` — tự áp dụng khi backend restart lần tới.

---

## 5. Màn hình mới: Hóa đơn hằng tháng của các phòng (`/monthly-bills`)

Trước đây chỉ xem được hóa đơn từng phòng một qua Room Detail. Màn mới cho xem **hóa đơn mọi phòng cùng lúc** theo tháng/năm:

- Bộ lọc: date picker chọn tháng/năm (mặc định tháng hiện tại) + dropdown chi nhánh (chỉ hiện với `ADMIN_TONG`; `ADMIN_CAP_1` tự khóa theo `branchIds` trong JWT, không có dropdown).
- Backend: `MonthlyBillListResponse` (bổ sung `roomCode`/`branchName` mà `MonthlyBillResponse` gốc không có), `MonthlyBillRepository.findByBillYearAndBillMonthAndContract_Room_BranchIdIn`, `BillingService.listAll()` (copy y hệt pattern branch-scoping của `DashboardService`), endpoint `GET /api/monthly-bills`.
- Click 1 dòng → dialog xem chi phí phát sinh (**chỉ đọc**) + lịch sử thanh toán + form ghi nhận thanh toán mới. **Chủ ý không cho tạo/sửa hóa đơn từ màn này** — việc đó vẫn thuộc Room Detail, trừ ngoại lệ ở mục 6 bên dưới.
- Đã đăng ký route `/monthly-bills` + mục menu sidebar `NAV.MONTHLY_BILLS` (cả 2 role).

---

## 6. Tạo hóa đơn hàng loạt (bulk-create)

Tạo hóa đơn từng phòng một qua Room Detail rất mất thời gian khi có nhiều phòng. Đã thêm nút **"Tạo hóa đơn hàng loạt"** trên màn Hóa đơn hằng tháng, dùng đúng tháng/năm/chi nhánh đang lọc, có xác nhận trước khi chạy:

- Backend: `ContractRepository.findByStatusAndRoom_BranchIdIn`, `BillingService.bulkCreate()` — duyệt mọi hợp đồng `ACTIVE` trong phạm vi: bỏ qua nếu đã có hóa đơn tháng đó (`alreadyExistsCount`), bỏ qua nếu `RentCalculator` trả về 0 tức hợp đồng không thực sự áp dụng cho tháng đó — ví dụ hợp đồng bắt đầu tháng sau (`notApplicableCount`), còn lại tạo hóa đơn **chỉ gồm tiền thuê** (`totalExtraFee = 0`, vì điện/nước mỗi phòng khác nhau không thể sinh hàng loạt). Endpoint `POST /api/monthly-bills/bulk`, response gồm danh sách hóa đơn vừa tạo (`MonthlyBillListResponse`) + 2 số đếm bỏ qua.
- Sau khi chạy xong: **dialog kết quả** liệt kê các hóa đơn vừa tạo kèm dòng tóm tắt "Đã tạo: X. Đã bỏ qua: Y."
- Click 1 dòng trong dialog kết quả → mở dialog chi tiết (dùng chung component với dialog xem-hóa-đơn ở mục 5) nhưng ở **chế độ editable** — cho thêm/xóa chi phí phát sinh ngay tại đây, không cần vào Room Detail. Đây là **ngoại lệ duy nhất** cho quy tắc "màn Hóa đơn hằng tháng chỉ xem" ở mục 5, vì hóa đơn vừa tạo hàng loạt còn thiếu điện/nước cần nhập ngay.
- Ở chế độ editable, phần **"Lịch sử thanh toán" bị ẩn hoàn toàn** (chưa có gì để thanh toán lúc đang nhập chi phí phát sinh cho hóa đơn vừa tạo).
- Mọi thay đổi (thêm/xóa chi phí phát sinh) tự đồng bộ lại cả 3 nơi: bảng chính, dialog kết quả đang mở, và dialog chi tiết đang mở (`refreshAfterBillMutation()`).
- Nếu hóa đơn (dù mở từ dialog editable) đã `DA_THANH_TOAN`, các control chỉnh sửa vẫn tự ẩn theo đúng khóa nghiệp vụ ở mục 3.

---

## 7. Ghi chú kỹ thuật quan trọng khác

- **Chọn tháng/năm**: mọi nơi trước đây dùng 2 ô `p-inputnumber` riêng (tháng/năm) đã đổi sang `p-datepicker view="month"` (1 ô, mặc định tháng hiện tại) — áp dụng cho cả form "Tạo hóa đơn tháng này" ở Room Detail lẫn bộ lọc màn Hóa đơn hằng tháng.
- **ngx-translate v18**: `instant()` chưa từng được dùng với tham số interpolation (`instant(key, params)`) trong dự án — khi cần build message có chèn số (VD dòng tóm tắt bulk-create), ghép chuỗi thủ công trong TS thay vì dùng interpolation, để tránh đưa vào 1 pattern chưa kiểm chứng cho riêng 1 tính năng.
- **Reuse DTO**: `MonthlyBillListResponse` (đã tạo cho màn list) được tái dùng luôn cho response của bulk-create, nhờ vậy dialog kết quả bulk-create dùng chung cấu trúc cột với bảng chính của màn Hóa đơn hằng tháng.

---

## 8. Việc còn lại (chưa làm / chưa test được)

- [ ] **Backend cần rebuild + restart bằng JDK 21** để áp dụng toàn bộ thay đổi phiên này (máy agent chỉ có JDK 17) — bao gồm migration `V2` mới, các endpoint mới (`PUT /contracts/{id}/end-date`, `GET /monthly-bills`, `POST /monthly-bills/bulk`), và các guard nghiệp vụ mới (`assertNotFullyPaid`, chặn vượt số tiền nợ).
- [ ] Chưa test round-trip thật với backend chạy thật cho luồng bulk-create + chỉnh sửa chi phí phát sinh ở chế độ editable (chỉ review code kỹ + `ng build` sạch mỗi bước).
- [ ] Dashboard vẫn dùng dữ liệu mẫu, chưa nối `GET /api/dashboard` thật (vẫn treo từ các phiên trước).

---

*File này bổ sung cho `tong-hop-trien-khai-frontend-phien-2.md`, `tong-hop-trien-khai-frontend.md`, `tong-hop-trien-khai-backend.md`, `tong-hop-du-an-quan-ly-phong-tro.md` — đọc cả 5 file để có ngữ cảnh đầy đủ trước khi tiếp tục phát triển.*

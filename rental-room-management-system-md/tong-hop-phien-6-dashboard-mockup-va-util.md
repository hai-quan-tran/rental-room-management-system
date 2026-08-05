# TỔNG HỢP PHIÊN LÀM VIỆC 6: DASHBOARD MOCKUP (3 BẢNG MỚI) + TẠO UTIL FRONTEND/BACKEND
*(Nối tiếp `tong-hop-phien-5-fix-flyway-manager-seed-du-lieu-demo.md`. Phiên này gồm 2 phần độc lập:
mở rộng Dashboard bằng dữ liệu mockup theo yêu cầu nghiệp vụ cụ thể, và một đợt refactor có kế
hoạch để gom logic lặp lại vào các file util dùng chung cho cả frontend lẫn backend.)*

---

## 1. Dashboard: 3 bảng mới + filter thời gian (dữ liệu mockup, chưa nối API)

Yêu cầu ban đầu: thêm vào Dashboard 3 bảng dùng data mockup (khoan nối backend):

1. **Số người đang ở theo chi nhánh** — bảng đơn giản (chi nhánh, số người). Có ghi chú code rõ quy
   tắc dedup cần áp dụng khi nối API thật: 1 người đại diện ký nhiều hợp đồng cùng lúc (thuê 2+
   phòng) chỉ tính là 1 người, không tính theo số hợp đồng.
2. **Phòng chưa có hóa đơn tháng trước** — áp dụng đúng quy tắc nghiệp vụ: hóa đơn tháng trước được
   tạo trong khoảng ngày 1–9 mỗi tháng (vd ngày 5/8 thì tháng cần có hóa đơn là tháng 7). Tháng mục
   tiêu được tính **động theo ngày hệ thống thực tế** (hàm `addMonths`/`dateToYearMonth`), không
   hardcode — data mẫu có cả trường hợp 1 phòng thiếu hóa đơn 2 tháng liên tiếp để minh họa bảng
   không giả định mọi phòng cùng thiếu 1 kỳ.
3. **Phòng chưa thanh toán đủ hóa đơn** — phòng, tổng tiền, đã thanh toán, còn nợ.

### 1.1. Điều chỉnh sau phản hồi của người dùng
- Ban đầu 2 chart "lượt trả phòng/vào ở" và "doanh thu" dùng dropdown chọn số tháng hiển thị (3/6/12)
  — người dùng yêu cầu đổi thành **2 date picker (Từ tháng / Đến tháng)**, mặc định "Đến tháng" =
  tháng hiện tại, "Từ tháng" = 6 tháng trước tháng hiện tại. Data mock các tháng sinh bằng hàm
  giả-ngẫu-nhiên có seed theo (năm, tháng) (`pseudoRandom`) để cùng 1 tháng luôn ra cùng số liệu dù
  đổi qua lại khoảng thời gian, tránh "nhảy số" gây khó hiểu khi demo.
- 2 bảng "chưa có hóa đơn"/"chưa thanh toán đủ" được thêm **scroll dọc trong khung cố định** (28rem)
  khi vượt quá 10 dòng — `[scrollable]`/`[scrollHeight]` chỉ bật khi `rows.length > 10`, dưới 10 dòng
  vẫn hiển thị bình thường không có khoảng trắng thừa. Đã tăng data mock lên 13 dòng mỗi bảng để test
  đúng ngưỡng này.

### 1.2. i18n
Thêm các key mới vào `vi.json`/`en.json` dưới `DASHBOARD.*` (`OCCUPANTS_BY_BRANCH`,
`OCCUPANTS_COUNT`, `MISSING_INVOICES`, `MISSING_INVOICES_HINT`, `UNPAID_INVOICES`, `FROM_MONTH`,
`TO_MONTH`) và `BILLING.PAID_AMOUNT` (key còn thiếu, phát hiện khi làm cột "Đã thanh toán").

---

## 2. Kế hoạch tạo util cho frontend + backend

Người dùng yêu cầu rà soát toàn bộ `src` của cả 2 phía để xác định nên tạo những util nào (string,
date, ...). Đã dùng 2 Explore agent song song rà soát toàn bộ frontend/backend, sau đó lập plan chi
tiết (qua Plan Mode) trước khi code — chỉ gom logic **thực sự trùng lặp ≥3 nơi**, không tạo abstraction
cho những chỗ mới chỉ xuất hiện 1 lần (vd regex CCCD/SĐT ở backend chỉ có ở `TenantRequest`, chưa
đáng để tách).

### 2.1. Frontend — 4 file util mới

| File | Hàm | Gom từ |
|---|---|---|
| `core/utils/date.util.ts` | `toIsoDate`, `fromIsoDate`, `dateToYearMonth`, `addMonths`, `shiftMonthDate` | 4 file (`monthly-bills-page`, `room-detail-page`, `tenant-detail-page`, `dashboard-page`) từng tự định nghĩa `toIsoDate`/`fromIsoDate` giống hệt nhau |
| `core/utils/list-query.util.ts` | `toListQuery(event, defaultSize)` | Khối `Math.floor(first/rows)` + ternary sort lặp lại y hệt ở **8 màn list** (tenants/branches/accounts/room-types/items/rooms/debt-records/monthly-bills) |
| `shared/utils/status-severity.util.ts` | `paymentStatusSeverity`, `roomStatusSeverity`, `debtStatusSeverity` | Ternary tô màu `p-tag` lặp lại ở `monthly-bills-page`, `room-detail-page` (x2), `rooms-page`, `debt-records-page` |
| `shared/utils/display.util.ts` | `roomBranchLabel`, `displayOr` | Label `"roomCode — branchName"` (6 chỗ) và fallback `?? '—'` (~13 chỗ) |

Quy ước áp dụng: mỗi hàm import vào component rồi gán `readonly xxx = xxx;` để template gọi được
(Angular template chỉ gọi được method/field của class, không gọi thẳng hàm module-level).

Trong lúc rà soát, phát hiện `accounts-page.html` (`account.active ? 'success' : 'danger'` — tô màu
tag trạng thái) và `account-detail-page.html` (`account.active ? 'danger' : 'success'` — tô màu nút
Kích hoạt/Ngừng hoạt động) trông như cùng 1 bug đảo ngược logic. Kiểm tra kỹ thì **không phải bug**:
2 chỗ có ngữ nghĩa khác nhau (tag trạng thái vs màu nút hành động theo quy ước "hành động nguy hiểm
= màu đỏ" đã có từ phiên 2) — đã không sửa.

### 2.2. Backend — 2 file util mới + 1 method thêm vào util có sẵn

| Vị trí | Hàm | Gom từ |
|---|---|---|
| `security/SecurityUtils.resolveBranchScope(requestedBranchId, allBranchIdsSupplier)` | method mới, thêm vào class có sẵn (không tạo file mới vì đây là mối quan tâm bảo mật/branch-scope, đặt cạnh `assertCanAccessBranch` hợp lý hơn) | 3 khối branch-scoping giống hệt nhau ở `BillingService.listAll`, `BillingService.bulkCreate`, `DashboardService.getDashboard`. Dùng `Supplier` để `branchRepository.findAll()` chỉ chạy khi thực sự cần (ADMIN_TONG không truyền branchId) |
| `common/util/MoneyUtils.summing()` | `Collector<BigDecimal,?,BigDecimal>` thay `.reduce(BigDecimal.ZERO, BigDecimal::add)` | 3 chỗ giống hệt (`BillingService` x2, `CheckoutService`) |
| `common/util/DateValidationUtils.assertNotBefore(date, reference, message)` | gộp guard `!= null && isBefore` | 3 chỗ kiểm tra ngày gần giống nhau (`ContractService` x2, `CheckoutService`) |

Không migrate `DebtRecordService`/`AuthService` (có ternary tương tự resolveBranchScope nhưng shape
khác — `DebtRecordService` không có tham số branchId, dùng repository method khác cho ADMIN_TONG).

**Chưa build lại được bằng JDK 21 thật** (máy agent chỉ có JDK 17, giống hạn chế mọi phiên trước) —
đã review kỹ bằng mắt (khớp import, kiểu tham số `LocalDate`/`BigDecimal`, các DTO liên quan) thay vì
compile thật.

### 2.3. Phát hiện: backend đang chạy thật trên máy dev

Khác các phiên trước, lần này backend **đang chạy sẵn** trên `localhost:8080` (do người dùng tự khởi
động bằng JDK 21 riêng, ngoài môi trường agent — agent vẫn chỉ có JDK 17). Nhờ vậy, thay vì test frontend
bằng JWT tự ký giả lập như các phiên trước, phiên này **test được round-trip thật**: đăng nhập
`admin`/`admin` qua API thật, duyệt qua toàn bộ các màn bị ảnh hưởng (dashboard, monthly-bills, rooms,
room-detail, tenants, accounts, branches, room-types, items, debt-records) bằng Playwright — không có
lỗi console. Đã xác nhận trực quan `roomStatusSeverity`/`displayOr` hoạt động đúng với data thật (màn
Phòng trọ, Room Detail). Riêng `paymentStatusSeverity`/`debtStatusSeverity`/`roomBranchLabel` chưa có
data mẫu (hóa đơn/công nợ) trong DB dev hiện tại để xem trực quan — không tạo dữ liệu test vào DB thật
của người dùng chỉ để chụp ảnh minh họa; độ tin cậy dựa vào type-check nghiêm ngặt của `ng build` +
logic giữ nguyên 100% so với bản gốc.

---

## 3. Việc còn lại / lưu ý cho phiên sau

- [ ] **Backend cần build/test thật bằng JDK 21** (`mvn clean test`) để xác nhận 3 thay đổi util
  (`resolveBranchScope`, `MoneyUtils`, `DateValidationUtils`) compile đúng — mới chỉ review bằng mắt.
- [ ] Dashboard 3 bảng mới + filter thời gian vẫn là **mockup thuần túy**, chưa nối
  `GET /api/dashboard` thật — khi nối cần đúng 2 điểm đã ghi chú sẵn trong code:
  - Đếm "số người đang ở" phải distinct theo tenant đại diện, không đếm theo hợp đồng.
  - "Phòng chưa có hóa đơn" cần trả về **tất cả** tháng còn thiếu cho mỗi phòng (không chỉ tháng gần
    nhất) để khớp đúng cách hiển thị hiện tại (1 dòng/tháng thiếu).
- [ ] Dashboard chart doanh thu/lượt ra vào vẫn dùng data mẫu (tồn đọng nhiều phiên).
- [ ] Chưa test round-trip thật cho `paymentStatusSeverity`/`debtStatusSeverity`/`roomBranchLabel` vì
  DB dev hiện tại chưa có hóa đơn/công nợ nào được seed — cần tạo hóa đơn/công nợ thật (qua UI, không
  qua script) rồi kiểm tra lại nếu muốn chắc chắn 100%.

---

*File này bổ sung cho `tong-hop-phien-5-fix-flyway-manager-seed-du-lieu-demo.md` và các file trước đó
— đọc từ file mới nhất lùi dần nếu cần đầy đủ ngữ cảnh.*

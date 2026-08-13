# TỔNG HỢP PHIÊN LÀM VIỆC 10: MÀN HÌNH NHÂN VIÊN + THIẾT KẾ LẠI LIÊN KẾT ACCOUNT
*(Nối tiếp `tong-hop-phien-9-seed-hoa-don-va-khoa-chi-so-da-thanh-toan.md`. Phiên này thêm hẳn 1
module mới (Nhân viên) chưa từng có trong spec gốc, theo yêu cầu trực tiếp của người dùng — không
phải fix/mở rộng tính năng cũ.)*

---

## 0. Bối cảnh & yêu cầu

Người dùng yêu cầu thêm màn hình quản lý **Nhân viên** (List + Detail), hoàn toàn mới, chưa có
trong 12 file spec/tổng hợp trước đó:
- Field: họ tên, ngày sinh, CCCD, SĐT, email — **tất cả bắt buộc** (khác Tenant, nơi email optional).
- Chỉ `ADMIN_TONG` được truy cập, đúng quy tắc đã áp dụng cho Chi nhánh (phiên 2).
- Mỗi nhân viên có đúng 1 tài khoản đăng nhập (Account) — người dùng yêu cầu phân tích rõ nên setup
  account ở màn Nhân viên hay màn Account trước khi code.

Đã dùng Plan Mode: 2 Explore agent song song rà soát pattern Tenant/Account/Branch (backend +
frontend) trước khi viết plan, có xin duyệt qua `ExitPlanMode` trước khi code.

---

## 1. Quyết định thiết kế: setup Account ở màn Nhân viên (đợt 1)

Khuyến nghị & đã áp dụng: setup account ngay trên form Nhân viên, không bắt phải tạo Account riêng
trước ở màn khác — tránh nhập trùng họ tên 2 nơi, và tái dùng nguyên vẹn `AccountService.create()`
có sẵn (không viết lại hash/validate). Chi tiết đầy đủ xem trong file plan đã duyệt của phiên
(`giggly-hopping-papert.md`, không lưu trong repo).

### 1.1. Database — migration mới `V5__employee.sql`
Bảng `employee`: `full_name`, `date_of_birth`, `id_card_number` (unique), `phone_number`, `email`
(tất cả `NOT NULL`), `account_id` (`UNIQUE`, FK tới `account`) — Employee giữ chiều sở hữu quan hệ
1-1 với Account (ràng buộc unique ở DB, không phải chỉ ở tầng service).

### 1.2. Backend
`Employee` entity (`@OneToOne` owning side tới `Account`), `EmployeeRepository`,
`EmployeeCreateRequest`/`EmployeeUpdateRequest`/`EmployeeAccountRequest`/`EmployeeResponse`,
`EmployeeService`, `EmployeeController` (`/api/employees`, `@PreAuthorize("hasRole('ADMIN_TONG')")`)
— theo đúng khuôn `TenantService`/`TenantController`. Điểm khác Tenant: `EmployeeService.create()`
tự dựng `AccountCreateRequest` rồi gọi thẳng `AccountService.create()` có sẵn để tạo account đi kèm
trong cùng giao dịch; `EmployeeService.delete()` **không xóa cứng** account liên kết mà gọi
`AccountService.setActive(accountId, false)` — giữ đúng quy ước soft-delete account đã có từ spec
gốc, tránh mất audit/lịch sử tham chiếu `account_id`.

### 1.3. Frontend
`employee.model.ts`/`employee.service.ts`, màn `/employees` (List) + `/employees/:id` (Detail) theo
khuôn Tenant List/Detail. Ở chế độ tạo mới: form cá nhân + section "Tài khoản đăng nhập"
(username/password/role, mặc định role `USER` — khớp đúng ý spec gốc "role USER dự phòng cho nhân
viên chi nhánh"). Ở chế độ sửa: chỉ sửa được thông tin cá nhân; nút Đổi mật khẩu/Kích hoạt-Ngừng
hoạt động tái dùng thẳng API Account có sẵn qua `accountId` lấy từ response, không viết lại logic.
Route + sidebar chỉ `ADMIN_TONG`, đầy đủ i18n vi/en.

---

## 2. Thiết kế lại: chọn account có sẵn HOẶC tạo mới (đợt 2, theo phản hồi ngay sau đó)

Người dùng phản hồi tiếp: ở màn Detail Nhân viên, cần cho phép **chọn 1 account có sẵn** (nếu account
đó chưa gắn với nhân viên nào) thay vì luôn bắt tạo mới; đồng thời **màn Account không cần field họ
tên nữa** — họ tên giờ chỉ quản lý ở màn Nhân viên.

### 2.1. Database — migration mới `V6__account_fullname_nullable.sql`
`account.full_name` đổi từ `NOT NULL` sang nullable — vì giờ có những account chưa từng gắn nhân
viên nào (account bootstrap `admin`, hoặc account vừa tạo ở màn Account nhưng chưa liên kết) hợp lệ
không có tên hiển thị.

### 2.2. Backend
- `AccountCreateRequest`/`AccountUpdateRequest`: bỏ `@NotBlank` (create) / bỏ hẳn field (update) cho
  `fullName` — màn Account không còn thu thập field này; `AccountService.update()` không còn đụng
  tới `fullName` nữa.
- `AccountRepository.findUnassigned()` (JPQL `NOT IN` subquery tới `Employee`) +
  `AccountService.listUnassigned()` + endpoint mới `GET /api/accounts/unassigned` — danh sách
  account chưa gắn nhân viên nào, dùng cho dropdown "chọn account có sẵn".
- `EmployeeCreateRequest`: đổi field `account` (bắt buộc) thành `existingAccountId` **hoặc**
  `newAccount` — đúng 1 trong 2, validate thủ công trong `EmployeeService.create()` (không thể diễn
  đạt bằng annotation đơn thuần), ném `VALIDATION_ERROR` nếu cả 2 cùng có/cùng thiếu. Nhánh chọn
  account có sẵn còn kiểm tra `EmployeeRepository.existsByAccountId(...)` để chặn gắn trùng.
- `EmployeeService.create()`/`update()`: đồng bộ `account.fullName = employee.fullName` sau mỗi lần
  tạo/gắn/sửa — họ tên hiển thị của 1 account giờ **luôn** do bản ghi Employee đang giữ nó quyết
  định, màn Account chỉ đọc lại gián tiếp qua đây (không tự quản lý nữa).

### 2.3. Frontend
- Màn Account (List + Detail): bỏ hẳn field/cột "Họ tên".
- Màn Nhân viên (Detail, chế độ tạo mới): thêm dropdown "Loại tài khoản" — **Tạo tài khoản mới**
  (giữ nguyên form cũ) hoặc **Chọn tài khoản có sẵn** (dropdown load từ `/accounts/unassigned`, hiện
  thông báo riêng nếu danh sách rỗng).
- Fix ăn theo: dropdown chọn quản lý chi nhánh (`branch-detail-page.ts`, phiên 5) trước đó luôn hiện
  `"${fullName} (${username})"` — nay fallback về `username` khi `fullName` là `null` (tránh hiện
  `" (username)"` trống tên).

---

## 3. Kiểm thử đã làm trong môi trường agent

- Backend: compile-verify sạch cả 2 đợt bằng recipe `mvn -o dependency:build-classpath` +
  `javac --release 17 -encoding UTF-8` (đã dùng từ phiên 7) — không có JDK 21 trong môi trường agent
  nên chưa chạy Spring context/DB thật.
- Frontend: `ng build` sạch cả 2 đợt, chỉ còn cảnh báo bundle-size initial đã tồn tại từ trước (không
  liên quan tới thay đổi phiên này).

---

## 4. Việc còn lại / lưu ý cho phiên sau

- [ ] **Chạy migration V5 + V6 và `mvn clean test` thật bằng JDK 21** trên máy người dùng — mọi thứ
  trong phiên này mới compile-verify bằng javac 17, chưa chạy live.
- [ ] Chưa test click-through thật: tạo nhân viên (cả 2 nhánh tạo-mới-account và chọn-account-có-sẵn)
  → đăng nhập bằng tài khoản vừa tạo/gắn → sửa thông tin cá nhân → xác nhận họ tên account đồng bộ
  đúng → đổi mật khẩu/ngừng hoạt động từ Employee Detail → xác nhận `ADMIN_CAP_1` bị chặn khỏi
  `/employees` (redirect `/dashboard`, giống test đã làm với `/branches` ở phiên 2).
- [ ] Chưa seed dữ liệu demo nào cho Employee (khác Tenant/Room ở phiên 5) — nếu cần dữ liệu mẫu để
  click-through, phải tạo tay qua UI hoặc viết script `dev-seed` mới.
- [ ] Dashboard chart doanh thu/lượt ra vào và 2 việc tồn đọng khác (Zalo integration, Giai đoạn 2-3
  của tự động hóa điện/nước) không thuộc phạm vi phiên này, vẫn treo từ các phiên trước.

---

*File này bổ sung cho `tong-hop-phien-9-seed-hoa-don-va-khoa-chi-so-da-thanh-toan.md` và các file
trước đó — đọc từ file mới nhất lùi dần nếu cần đầy đủ ngữ cảnh.*

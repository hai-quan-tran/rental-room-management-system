# TỔNG HỢP DỰ ÁN: WEB APP QUẢN LÝ PHÒNG TRỌ
*(File này gộp toàn bộ nội dung đã thống nhất trong phiên làm việc — dùng để tra cứu nhanh hoặc làm ngữ cảnh cho các bước tiếp theo, không cần đọc lại toàn bộ lịch sử chat)*

---

## 0. Bối cảnh & mục tiêu
Xây dựng web app quản lý phòng trọ cho chuỗi nhiều chi nhánh: quản lý phòng, người thuê, hợp đồng, chi phí phát sinh, công nợ và doanh thu theo tháng.

---

## 1. Tech stack

| Layer | Công nghệ |
|---|---|
| Frontend | Angular + PrimeNG v21, theme primary color = **xanh dương (blue)** |
| Backend | Java 21, Spring Boot 4.x, Spring Data JPA, Spring Security + JWT |
| Test | JUnit 5 + Mockito — mock repository, **không kết nối DB thật** (dùng H2/Testcontainers nếu cần test integration) |
| Cache | Redis — cache dashboard, cache danh sách ít đổi (chi nhánh/loại phòng), blacklist JWT khi logout |
| Database | MySQL 8, quản lý migration bằng Flyway |

**Quy ước kỹ thuật đã chốt:**
- Frontend state: Angular Signals (không cần NgRx trừ khi app phức tạp hơn)
- Biểu đồ: `primeng/chart` (Chart.js)
- Đặt tên bảng/cột: snake_case, có `created_at`/`updated_at`, một số bảng nghiệp vụ chính có thêm `created_by`/`updated_by`
- Response API chuẩn hóa (success/error/message/data), xử lý lỗi tập trung bằng `@ControllerAdvice`

---

## 2. Phân quyền (Role-based access control)

| Role | Mô tả |
|---|---|
| `ADMIN_TONG` | Toàn quyền: account, tất cả chi nhánh, tất cả phòng, dashboard toàn hệ thống |
| `ADMIN_CAP_1` | Quản lý người thuê, chi nhánh được gán, phòng thuộc chi nhánh được gán, dashboard giới hạn theo chi nhánh mình quản lý |
| `USER` | **Đã tạo sẵn khung phân quyền (enum, guard, `@PreAuthorize`) nhưng chưa gán chức năng/màn hình nào** — để dành mở rộng sau này |

**Ghi chú kỹ thuật:**
- JWT payload chứa: `userId`, `username`, `role`, danh sách `branchIds` quản lý (suy ra từ `branch.manager_account_id`, không lưu list trên account)
- `ADMIN_CAP_1` chỉ thao tác được trên chi nhánh mình quản lý — backend lấy `branchIds` từ JWT context, **không nhận `branchId` từ query param của client**

---

## 3. Mô hình dữ liệu (Entities) — bản chốt cuối cùng

> Đây là bản đã áp dụng các điều chỉnh kỹ thuật khi thiết kế schema thật (xem giải thích ở mục 5).

- **Account**: fullName, username, passwordHash, role, isActive
- **Tenant**: fullName, dateOfBirth, idCardNumber (unique), phoneNumber, email (nullable)
- **Branch**: name, address, managerAccountId (FK → Account) — *tổng số phòng & số lượng theo loại phòng KHÔNG lưu cứng, tính động qua view*
- **RoomType**: name (vd "Có gác 4x4"), area, description
- **RoomTypeHandoverItem**: roomTypeId, itemName, quantity, note — **vật dụng bàn giao gắn theo LOẠI PHÒNG**, dùng chung cho mọi phòng cùng loại
- **Room**: branchId, roomCode, roomTypeId, monthlyRent, status (`TRONG` / `DANG_THUE`)
- **Contract**: roomId, startDate, endDate (nullable), monthlyRent, depositAmount, status (`ACTIVE` / `ENDED`) — *không còn field representativeTenantId, xem ContractTenant*
- **ContractTenant** (N-N Contract ↔ Tenant): isRepresentative (nguồn dữ liệu duy nhất cho "người đại diện ký hợp đồng", mỗi hợp đồng chỉ có đúng 1 người)
- **ExtraFeeCategory**: name (Điện, Nước, Gửi xe, Tiền mạng, Phí sửa chữa...), unit
- **MonthlyBill**: contractId, month, year, rentAmount, totalExtraFee, totalAmount (tính), paidAmount, remainingAmount (tính), paymentStatus (`CHUA_THANH_TOAN` / `THANH_TOAN_MOT_PHAN` / `DA_THANH_TOAN`)
- **ExtraFeeItem**: monthlyBillId, extraFeeCategoryId, amount, note
- **Payment**: monthlyBillId, amount, paymentDate, **method (free text)**, note, createdBy
- **CheckoutChecklist**: contractId (1-1), checkedBy, checkedAt, depositAmount (snapshot), deductionAmount, depositRefundAmount, note
- **CheckoutChecklistItem**: checklistId, roomTypeHandoverItemId, status (`CON_NGUYEN`/`HU_HONG`/`MAT`), deductionAmount, note
- **DebtRecord**: contractId, checklistId, amount, reason, status (`CHUA_THU`/`DA_THU`), collectedAmount, note — **bản ghi công nợ riêng, độc lập với MonthlyBill**, tạo khi tiền trừ lúc trả phòng vượt quá tiền cọc
- **RefreshToken**: accountId, tokenHash, expiresAt, revoked — hỗ trợ revoke JWT bền vững (bổ sung ngoài spec gốc)

---

## 4. Chi tiết màn hình (Screens)

### 4.1 Quản lý Account (List/Detail) — chỉ `ADMIN_TONG`
Tên, username (unique), password (ẩn khi xem, có nút đổi riêng), role, cờ active (soft-delete bằng `isActive`, không xóa cứng).

### 4.2 Quản lý Người thuê (List/Detail) — `ADMIN_TONG`, `ADMIN_CAP_1`
Họ tên, ngày sinh, CCCD (unique), SĐT, email (optional). Xem lịch sử phòng đã/đang thuê.

### 4.3 Quản lý Chi nhánh (List/Detail) — `ADMIN_TONG`, `ADMIN_CAP_1` (chỉ chi nhánh được gán)
Địa chỉ, tổng số phòng & số lượng theo loại phòng (tính động), người quản lý.

### 4.4 Quản lý Phòng trọ (List/Detail) — `ADMIN_TONG`, `ADMIN_CAP_1`
Chi tiết phòng gồm:
1. **Thông tin cơ bản**: loại phòng, giá thuê mặc định
2. **Vật dụng bàn giao**: hiển thị theo loại phòng (sửa ở màn hình Loại phòng, không sửa riêng từng phòng)
3. **Hợp đồng thuê**: hợp đồng hiệu lực (ngày bắt đầu/kết thúc, tiền cọc) + lịch sử hợp đồng cũ (kèm checklist trả phòng đã lưu)
4. **Chi phí phát sinh theo tháng**: chọn danh mục (select) + số tiền + ghi chú
5. **Tổng tiền phải trả & Công nợ**:
   - `Tổng tiền = Tiền thuê tháng đó + Tổng chi phí phát sinh`
   - Tròn tháng → tính đủ `monthlyRent`; không tròn tháng → `monthlyRent / số_ngày_thực_tế_trong_tháng × số_ngày_thực_ở`
   - Số ngày trong tháng dùng **số ngày thực tế** (28/29/30/31), không quy ước cố định 30 ngày
   - Công nợ: nhiều lần thanh toán (Payment), phương thức thanh toán **tự do**, tự cộng dồn `paidAmount`/`remainingAmount`/`paymentStatus`
6. **Checklist trả phòng**: kiểm tra từng vật dụng (Còn nguyên/Hư hỏng/Mất) → tính `Tiền cọc hoàn lại = MAX(0, cọc - tổng trừ)`; **nếu tổng trừ vượt cọc → tự động tạo `DebtRecord` riêng** để theo dõi thu sau
7. **Người thuê trong phòng**: người đại diện ký hợp đồng hiển thị đầu tiên kèm tag; thêm người thuê từ danh sách có sẵn hoặc tạo mới ngay tại màn hình

### 4.5 Dashboard

**ADMIN_TONG** (có bộ lọc chi nhánh, mặc định = tất cả):
1. Cột: số phòng trống vs đang cho thuê
2. Cột theo tháng: số trả phòng vs vào mới
3. Cột/đường theo tháng: tổng tiền thuê thu được

**ADMIN_CAP_1** (KHÔNG có bộ lọc, cố định theo chi nhánh được quản lý — lấy `branchIds` từ JWT):
- 3 biểu đồ tương tự trên, chỉ tính trên chi nhánh mình quản lý

**USER**: chưa có chức năng, chưa có dashboard.

---

## 5. Database Schema — đã triển khai

File: **`V1__init_schema.sql`** (Flyway migration, đặt vào `src/main/resources/db/migration/`)

**Quy ước áp dụng:**
- Engine InnoDB, charset `utf8mb4_unicode_ci`
- Enum → **VARCHAR + CHECK constraint** (không dùng ENUM native, dễ mở rộng giá trị sau này)
- Tiền tệ → **DECIMAL(15,0)** (VNĐ không thập phân)
- `id BIGINT UNSIGNED AUTO_INCREMENT` cho mọi bảng

**16 bảng + 1 view + 1 trigger:**
`account`, `tenant`, `branch`, `room_type`, `room_type_handover_item`, `room`, `extra_fee_category`, `contract`, `contract_tenant`, `monthly_bill`, `extra_fee_item`, `payment`, `checkout_checklist`, `checkout_checklist_item`, `debt_record`, `refresh_token` + view `v_branch_room_summary` + trigger `trg_payment_after_insert`.

**3 điều chỉnh kỹ thuật so với spec ban đầu (cần bạn xác nhận lại nếu muốn giữ nguyên):**
1. Bỏ `contract.representative_tenant_id` — người đại diện chỉ xác định qua `contract_tenant.is_representative`, ràng buộc **đúng 1 người/hợp đồng** bằng generated column + unique index.
2. Bỏ `branch.total_rooms`/`room_type_summary` dạng cột — thay bằng **VIEW `v_branch_room_summary`** tính động.
3. Bỏ `account.managed_branch_id` — quan hệ 1 account quản lý N chi nhánh suy ra tự nhiên từ `branch.manager_account_id`.

**Ràng buộc đáng chú ý khác:**
- Mỗi phòng chỉ có tối đa 1 hợp đồng `ACTIVE` cùng lúc — thực thi bằng **trigger** `trg_contract_before_insert`/`trg_contract_before_update` *(ban đầu dùng generated column + unique index nhưng MySQL 8 báo lỗi 1215 khi ADD một STORED generated column phụ thuộc vào cột đang là khóa ngoại/khóa chính — hạn chế/bug đã biết của InnoDB — nên đã chuyển sang trigger)*
- Mỗi hợp đồng chỉ có đúng 1 người đại diện ký hợp đồng — thực thi bằng **trigger** `trg_ct_before_insert`/`trg_ct_before_update` (cùng lý do đổi cách làm như trên)
- `monthly_bill.total_amount`/`remaining_amount` là generated column tự tính (cột này KHÔNG phụ thuộc FK/PK nên không bị lỗi 1215, vẫn dùng generated column bình thường)
- Trigger `trg_payment_after_insert` tự cập nhật `paid_amount`/`payment_status` khi thêm Payment (có thể chuyển logic này về service layer nếu muốn kiểm soát tập trung hơn)
- Thêm bảng `refresh_token` (ngoài spec gốc) để hỗ trợ revoke JWT bền vững, kết hợp Redis cho access-token blacklist

⚠️ *Lưu ý: schema chưa được chạy thử trên MySQL thật (môi trường làm việc không có sẵn MySQL/network) — nên test lại trước khi dùng production.*

---

## 6. Yêu cầu phi chức năng

- Bảo mật: password hash BCrypt, JWT access token ngắn hạn (15-30p) + refresh token (7 ngày, revoke được)
- Validate: CCCD unique đúng định dạng, SĐT đúng định dạng VN, email đúng định dạng nếu có nhập
- Audit log cho các bảng nghiệp vụ chính (hợp đồng, chi phí, hóa đơn)
- Phân trang/tìm kiếm/sort ở tầng backend cho mọi màn hình list
- Unit test Mockito cho service quan trọng: tính tiền thuê theo tỷ lệ ngày ở, tổng hợp chi phí phát sinh, cập nhật công nợ

---

## 7. Việc tiếp theo (chưa làm)

- [ ] JPA Entity classes (Java) khớp với schema
- [ ] API design (danh sách endpoint + request/response DTO)
- [ ] Khung dự án Angular (module/routing/service) và Spring Boot (package structure)
- [ ] Xác nhận lại 3 điều chỉnh kỹ thuật ở mục 5 nếu muốn quay lại spec gốc

---

*File này thay thế cho việc phải đọc lại toàn bộ lịch sử chat — dùng làm ngữ cảnh đầu vào cho các bước phát triển tiếp theo.*

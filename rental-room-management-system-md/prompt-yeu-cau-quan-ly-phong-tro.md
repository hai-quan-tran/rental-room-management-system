# PROMPT YÊU CẦU XÂY DỰNG WEB APP QUẢN LÝ PHÒNG TRỌ

## 0. Bối cảnh & mục tiêu
Xây dựng một web application quản lý phòng trọ (rental room management system) cho phép chuỗi nhiều chi nhánh (branch) quản lý phòng, người thuê, hợp đồng, chi phí phát sinh và doanh thu theo tháng.

---

## 1. Tech stack bắt buộc

**Frontend**
- Angular (phiên bản mới nhất tương thích PrimeNG 21)
- PrimeNG v21 làm UI component library
- Theme: PrimeNG theme preset với **primary color = blue (xanh dương)**
- State management: chọn 1 trong Signals/NgRx (đề xuất dùng Angular Signals cho đơn giản, không cần NgRx nếu app không quá phức tạp)
- Biểu đồ: sử dụng `primeng/chart` (Chart.js) cho các biểu đồ dashboard (bar chart, line chart)

**Backend**
- Java 21 (dùng các feature mới: records, pattern matching, virtual threads nếu phù hợp)
- Spring Boot 4.x
- Spring Data JPA (Hibernate) cho tầng persistence
- Spring Security + JWT (access token + refresh token) cho authentication/authorization
- Validation: Jakarta Bean Validation
- Test: JUnit 5 + Mockito
  - Unit test cho service layer: mock repository, không kết nối DB thật
  - Nếu cần test tầng repository/integration, dùng H2 in-memory DB hoặc Testcontainers (KHÔNG được động vào MySQL thật)
  - Coverage tối thiểu cho service layer nghiệp vụ quan trọng (tính tiền thuê, tính chi phí phát sinh)

**Cache**
- Redis dùng cho:
  - Cache dữ liệu dashboard (biểu đồ) theo TTL ngắn (vd 5-10 phút)
  - Cache danh sách chi nhánh/loại phòng (dữ liệu ít thay đổi)
  - Lưu blacklist JWT token khi logout / revoke token

**Database**
- MySQL 8
- Dùng Flyway hoặc Liquibase để quản lý migration schema (đề xuất Flyway)
- Đặt tên bảng/cột theo snake_case, có `created_at`, `updated_at`, `created_by`, `updated_by` (audit fields) cho các bảng nghiệp vụ chính

---

## 2. Phân quyền hệ thống (Role-based access control)

| Role | Mô tả |
|---|---|
| `ADMIN_TONG` (Super Admin) | Toàn quyền: quản lý account, tất cả chi nhánh, tất cả phòng, dashboard toàn hệ thống |
| `ADMIN_CAP_1` (Branch Admin) | Quản lý người thuê, chi nhánh (được gán), phòng trọ thuộc chi nhánh được gán, dashboard giới hạn theo chi nhánh mình quản lý |
| `USER` | Role được tạo sẵn trong hệ thống (enum, phân quyền, cấu trúc account) nhưng **hiện tại chưa có chức năng/màn hình cụ thể nào được cấp cho role này**. Mục đích là để dễ mở rộng sau này (ví dụ: nhân viên chi nhánh chỉ xem, không sửa). Khi implement, cần dựng sẵn khung phân quyền (guard, decorator, `@PreAuthorize`) cho role này nhưng chưa cần gán quyền truy cập màn hình nào — tạm thời route/API dành cho USER có thể trả về "chưa có quyền truy cập" hoặc ẩn hết menu. |

**Ghi chú kỹ thuật phân quyền:**
- Áp dụng `@PreAuthorize` theo role ở tầng Controller/Service
- `ADMIN_CAP_1` chỉ được thao tác trên các chi nhánh mà mình được gán quản lý (cần bảng liên kết account ↔ chi nhánh quản lý, hoặc field `managed_branch_id` trên account)
- Token JWT payload cần chứa: `userId`, `username`, `role`, danh sách `branchIds` được quản lý (nếu là admin cấp 1)

---

## 3. Mô hình dữ liệu chính (Entities) — đề xuất

### Account
- id, fullName, username, passwordHash, role (enum: ADMIN_TONG, ADMIN_CAP_1, USER), isActive (boolean), managedBranchIds (nếu ADMIN_CAP_1), createdAt, updatedAt

### Tenant (Người thuê)
- id, fullName, dateOfBirth, idCardNumber (CCCD, unique), phoneNumber, email (nullable), createdAt, updatedAt

### Branch (Chi nhánh)
- id, address, managerAccountId (quản lý chi nhánh), totalRooms (tính toán hoặc lưu cache), roomTypeSummary (số lượng phòng theo từng loại — có thể tính động từ bảng Room thay vì lưu cứng)

### RoomType (Loại phòng)
- id, name (vd "Có gác 4x4", "Không gác 4x4"), area/size, description

### RoomTypeHandoverItem (Vật dụng bàn giao mặc định theo từng loại phòng)
- id, roomTypeId, itemName (vd giường, tủ, quạt, bình nóng lạnh...), quantity, note
- Đây là **template dùng chung cho mọi phòng cùng loại phòng** (không nhập riêng cho từng phòng cụ thể, không nhập riêng cho từng hợp đồng)

### Room (Phòng trọ)
- id, branchId, roomCode, roomTypeId, monthlyRent (giá thuê mặc định), status (enum: TRONG, DANG_THUE)
- Danh sách vật dụng bàn giao của phòng = lấy từ `RoomTypeHandoverItem` theo `roomTypeId` của phòng đó (hiển thị, không lưu trùng lặp ở tầng Room)

### Contract (Hợp đồng thuê)
- id, roomId, startDate, endDate (nullable nếu đang hiệu lực/không xác định), monthlyRent (giá thuê tại thời điểm ký, có thể khác giá mặc định của phòng), depositAmount (tiền cọc), status (ACTIVE, ENDED), representativeTenantId (người đại diện ký hợp đồng)

### ContractTenant (bảng trung gian Hợp đồng ↔ Người thuê, N-N)
- contractId, tenantId, isRepresentative (boolean) — dùng để đánh dấu & hiển thị tag "Người ký hợp đồng" và sắp xếp người này lên đầu danh sách

### ExtraFeeCategory (Danh mục chi phí phát sinh)
- id, name (Điện, Nước, Gửi xe, Tiền mạng, Phí sửa chữa, ...), unit (nếu cần, vd điện/nước theo số điện-nước)

### MonthlyBill (Hóa đơn/chi phí tháng của phòng theo hợp đồng)
- id, contractId, month, year, rentAmount (đã tính theo tỷ lệ ngày ở nếu không tròn tháng), totalExtraFee, totalAmount
- **Công nợ**: paymentStatus (enum: CHUA_THANH_TOAN / THANH_TOAN_MOT_PHAN / DA_THANH_TOAN), paidAmount (tổng đã thu), remainingAmount (còn nợ = totalAmount - paidAmount)

### ExtraFeeItem (Chi tiết chi phí phát sinh trong 1 MonthlyBill)
- id, monthlyBillId, extraFeeCategoryId, amount, note

### Payment (Lịch sử thanh toán của 1 MonthlyBill — hỗ trợ thanh toán nhiều lần / công nợ)
- id, monthlyBillId, amount, paymentDate, method (tiền mặt/chuyển khoản...), note, createdBy

### CheckoutChecklist (Checklist khi trả phòng / kết thúc hợp đồng)
- id, contractId, checkedAt, checkedBy (accountId thực hiện kiểm tra), depositAmount (tiền cọc ban đầu, lấy từ Contract), deductionAmount (tổng tiền trừ do hư hỏng/mất vật dụng hoặc còn nợ chưa thanh toán), depositRefundAmount (số tiền cọc thực trả lại = depositAmount - deductionAmount, không âm), note

### CheckoutChecklistItem (Chi tiết từng vật dụng khi kiểm tra trả phòng)
- id, checklistId, roomTypeHandoverItemId (tham chiếu vật dụng mẫu theo loại phòng), status (enum: CON_NGUYEN / HU_HONG / MAT), deductionAmount (tiền trừ cho item này nếu có), note

### DebtRecord (Công nợ phát sinh khi trả phòng vượt quá tiền cọc)
- id, contractId, checklistId, amount (số tiền khách còn nợ sau khi đã trừ hết cọc), reason (vd "Vượt cọc do hư hỏng vật dụng", "Còn nợ tiền thuê/chi phí phát sinh"), status (enum: CHUA_THU / DA_THU), collectedAmount, note, createdAt
- Dùng để theo dõi và thu hồi công nợ phát sinh sau khi hợp đồng đã kết thúc (khách đã trả phòng nhưng còn thiếu tiền)

---

## 4. Chi tiết các màn hình (Screens)

### 4.1 Quản lý Account (List + Detail) — chỉ ADMIN_TONG
- List: bảng hiển thị tên, username, role, trạng thái hoạt động (active/inactive), có filter theo role/status, search theo tên/username, phân trang
- Detail/Create/Edit: form nhập tên, username (unique), password (chỉ hiện khi tạo mới hoặc có nút "đổi mật khẩu" riêng, không hiển thị password cũ), chọn role (dropdown), toggle switch cờ active
- Xóa/deactivate account: nên soft-delete bằng cờ `isActive`, không xóa cứng

### 4.2 Quản lý Người thuê (List + Detail) — ADMIN_TONG, ADMIN_CAP_1
- List: họ tên, CCCD, SĐT, filter/search theo tên hoặc CCCD, phân trang
- Detail/Create/Edit: họ tên, ngày sinh (date picker), CCCD (validate unique, format), SĐT (validate định dạng VN), email (optional, validate format nếu nhập)
- Hiển thị lịch sử: người thuê này từng/đang thuê phòng nào (liên kết qua ContractTenant)

### 4.3 Quản lý Chi nhánh (List + Detail) — ADMIN_TONG, ADMIN_CAP_1 (chỉ xem/sửa chi nhánh mình quản lý)
- List: tên/địa chỉ chi nhánh, tổng số phòng, người quản lý, filter theo quản lý
- Detail: địa chỉ, danh sách số lượng phòng theo từng loại phòng (bảng tổng hợp, tính động từ dữ liệu Room), chọn quản lý chi nhánh (dropdown account có role ADMIN_CAP_1)

### 4.4 Quản lý Phòng trọ trong chi nhánh (List + Detail) — ADMIN_TONG, ADMIN_CAP_1
**List:** theo từng chi nhánh, hiển thị mã phòng, loại phòng, trạng thái (trống/đang thuê), giá thuê

**Detail phòng gồm các tab/section:**
1. **Thông tin cơ bản**: loại phòng (dropdown: có gác 4x4, không gác 4x4, ...), giá thuê hằng tháng mặc định
2. **Vật dụng bàn giao**: hiển thị danh sách vật dụng bàn giao **theo loại phòng** (lấy từ `RoomTypeHandoverItem` của `roomTypeId` mà phòng này thuộc về) — tên vật dụng, số lượng, ghi chú. Vì gắn theo loại phòng nên mọi phòng cùng loại sẽ có chung 1 danh sách; muốn thay đổi thì sửa ở màn hình quản lý Loại phòng, không sửa riêng lẻ theo từng phòng
3. **Hợp đồng thuê**:
   - Hợp đồng đang hiệu lực: ngày bắt đầu – ngày kết thúc (hoặc "chưa xác định" nếu không có ngày kết thúc cố định), số tiền cọc
   - Lịch sử hợp đồng: bảng danh sách các hợp đồng trước đó của phòng (đã kết thúc), có thể xem lại checklist trả phòng và số tiền cọc đã hoàn của từng hợp đồng
   - Khi tạo hợp đồng mới: nhập số tiền cọc, chọn/tạo người thuê (xem mục 4.5), chọn 1 người là đại diện ký hợp đồng
4. **Chi phí phát sinh theo tháng**:
   - Form thêm chi phí: select danh mục (Điện/Nước/Gửi xe/Tiền mạng/Phí sửa chữa/...), field nhập số tiền, field ghi chú
   - Danh sách chi phí đã nhập trong tháng, có thể sửa/xóa trước khi chốt hóa đơn
5. **Tổng tiền phải trả tháng đó & Công nợ**:
   - Công thức: `Tổng tiền = Tiền thuê tháng đó + Tổng chi phí phát sinh`
   - **Tiền thuê tháng đó**:
     - Nếu ở tròn nguyên tháng (từ ngày 1 đến hết tháng, hoặc đủ tháng tính theo chu kỳ hợp đồng): tính đủ `monthlyRent`
     - Nếu không tròn tháng (tháng bắt đầu ở hoặc tháng trả phòng): tính theo tỷ lệ ngày ở
       - Công thức: `Tiền thuê = monthlyRent / số_ngày_thực_tế_trong_tháng × số_ngày_thực_ở_trong_tháng`
       - Số ngày trong tháng dùng **số ngày thực tế của tháng đó** (tháng 2 là 28 hoặc 29 ngày tùy năm nhuận, tháng 4/6/9/11 là 30 ngày, các tháng còn lại 31 ngày) — KHÔNG quy ước cố định 30 ngày/tháng
       - Ví dụ: hợp đồng bắt đầu ngày 15/2/2026 (năm không nhuận, tháng 2 có 28 ngày), số ngày thực ở trong tháng 2 = 14 ngày (từ 15 đến hết 28) → Tiền thuê tháng 2 = `monthlyRent / 28 × 14`
   - **Quản lý công nợ**:
     - Mỗi MonthlyBill có trạng thái thanh toán: Chưa thanh toán / Thanh toán một phần / Đã thanh toán đủ
     - Cho phép nhập nhiều lần thanh toán (Payment) cho 1 hóa đơn, mỗi lần thanh toán chỉ cần: số tiền, ngày thanh toán, **phương thức thanh toán nhập tự do (free text)**, ghi chú — không giới hạn theo danh sách phương thức cố định
     - Hệ thống tự cộng dồn `paidAmount` và tính lại `remainingAmount`, tự động cập nhật `paymentStatus`
     - Màn hình phòng hiển thị rõ số tiền còn nợ của tháng hiện tại và có thể xem lịch sử công nợ các tháng trước (danh sách MonthlyBill kèm trạng thái thanh toán)
     - Dashboard/báo cáo (mở rộng sau) có thể lọc theo phòng/hợp đồng đang còn nợ

6. **Checklist trả phòng (khi kết thúc hợp đồng)**:
   - Khi thao tác "Kết thúc hợp đồng / Trả phòng", hệ thống mở form checklist dựa trên danh sách vật dụng bàn giao theo loại phòng (mục 2 - Vật dụng bàn giao, lấy từ `RoomTypeHandoverItem`)
   - Với mỗi vật dụng: đánh dấu tình trạng (Còn nguyên / Hư hỏng / Mất), nếu Hư hỏng/Mất thì nhập số tiền trừ (deductionAmount) và ghi chú
   - Hệ thống tự tính:
     - `Tổng tiền trừ = Tổng tiền trừ do vật dụng hư hỏng/mất + Công nợ tiền thuê/chi phí phát sinh còn lại (nếu có)`
     - `Tiền cọc hoàn lại = MAX(0, depositAmount - Tổng tiền trừ)`
     - **Nếu Tổng tiền trừ > depositAmount** (vượt quá tiền cọc): hệ thống tự động tạo 1 bản ghi `DebtRecord` (công nợ riêng, độc lập với MonthlyBill) với số tiền = phần vượt quá, trạng thái "Chưa thu", để theo dõi và thu hồi sau này (khách đã trả phòng nhưng vẫn còn thiếu tiền)
   - Sau khi hoàn tất checklist, hợp đồng chuyển trạng thái ENDED, phòng chuyển trạng thái TRỐNG, lưu lại toàn bộ checklist (và DebtRecord nếu có) vào lịch sử hợp đồng của phòng (xem lại được trong mục 3 - Lịch sử hợp đồng)
   - Cần có màn hình/tab riêng (hoặc tích hợp vào màn hình Người thuê) để xem danh sách công nợ (`DebtRecord`) đang "Chưa thu" và cập nhật khi thu được tiền (chuyển sang "Đã thu", ghi nhận `collectedAmount`)

7. **Người thuê trong phòng**:
   - Danh sách người thuê hiện tại (lấy từ hợp đồng đang hiệu lực)
   - Người đại diện ký hợp đồng hiển thị **đầu tiên** trong danh sách, kèm tag "Người ký hợp đồng"
   - Nút thêm người thuê: cho phép chọn từ danh sách Tenant có sẵn (autocomplete/search) HOẶC mở form tạo mới Tenant ngay tại đây rồi thêm vào phòng

### 4.5 Dashboard

**Dashboard cho ADMIN_TONG:**
- Bộ lọc chung: dropdown chọn chi nhánh (mặc định = "Tất cả chi nhánh")
- Biểu đồ 1 (cột): số phòng trống vs số phòng đang cho thuê
- Biểu đồ 2 (cột theo tháng): số lượt trả phòng vs số lượt vào ở mới theo từng tháng
- Biểu đồ 3 (cột/đường theo tháng): tổng số tiền thuê thu được theo từng tháng

**Dashboard cho ADMIN_CAP_1:**
- KHÔNG có bộ lọc chọn chi nhánh — dữ liệu mặc định và cố định là (các) chi nhánh mà admin cấp 1 đó đang quản lý
- Biểu đồ 1 (cột): số phòng trống vs số phòng đang cho thuê, tính trên (các) chi nhánh được quản lý
- Biểu đồ 2 (cột theo tháng): số lượt trả phòng vs số lượt vào ở mới theo từng tháng, tính trên (các) chi nhánh được quản lý
- Biểu đồ 3 (cột/đường theo tháng): tổng số tiền thuê thu được theo từng tháng, tính trên (các) chi nhánh được quản lý
- Về mặt kỹ thuật: backend cần lấy `managedBranchIds` từ JWT/context của account đang đăng nhập để filter dữ liệu, không nhận `branchId` từ query param của client (tránh admin cấp 1 xem được dữ liệu chi nhánh không thuộc quyền quản lý)

**Dashboard cho USER:**
- Hiện tại role USER chưa có chức năng nào được cấp, do đó cũng chưa có dashboard. Khi mở rộng sau này có thể bổ sung tương tự cấu trúc trên.

---

## 5. Yêu cầu phi chức năng (Non-functional requirements)

- **Bảo mật**: mật khẩu hash bằng BCrypt, JWT access token thời gian sống ngắn (vd 15-30 phút) + refresh token (vd 7 ngày), refresh token lưu ở Redis hoặc DB để revoke được
- **Validate dữ liệu**: CCCD đúng định dạng và unique, SĐT đúng định dạng VN, email đúng định dạng nếu có nhập
- **Audit log**: ghi nhận ai tạo/sửa các bản ghi quan trọng (hợp đồng, chi phí, hóa đơn)
- **Phân trang & tìm kiếm**: tất cả màn hình list đều cần phân trang, search, sort ở tầng backend (không load hết dữ liệu về frontend)
- **Response chuẩn hóa**: API trả về theo format thống nhất (success/error, message, data), xử lý lỗi tập trung bằng `@ControllerAdvice`
- **Testing**: Unit test cho các service quan trọng, đặc biệt là logic tính tiền thuê theo tỷ lệ ngày ở và tổng hợp chi phí phát sinh — dùng Mockito mock repository, không kết nối MySQL thật khi chạy test

---

## 6. Tổng kết trạng thái spec
Tất cả các điểm mở trước đó đã được xác nhận:
- Role USER: đã tạo sẵn khung phân quyền, chưa gán chức năng (mở rộng sau)
- Dashboard ADMIN_CAP_1: cố định theo chi nhánh quản lý, không có bộ lọc
- Tính tiền thuê không tròn tháng: theo số ngày thực tế của tháng
- Có quản lý công nợ (MonthlyBill + Payment) và có luồng checklist trả phòng (CheckoutChecklist + DebtRecord)
- Payment: phương thức thanh toán nhập tự do (free text)
- Vượt quá tiền cọc khi trả phòng: tạo `DebtRecord` riêng để theo dõi thu sau
- Vật dụng bàn giao: gắn theo **loại phòng** (`RoomTypeHandoverItem`), dùng chung cho mọi phòng cùng loại

Spec đã sẵn sàng để triển khai bước tiếp theo (database schema chi tiết, API design, hoặc dựng khung dự án Angular/Spring Boot).

---

*Tài liệu này dùng làm prompt/spec đầu vào để triển khai chi tiết từng phần (database schema, API design, giao diện Angular) ở các bước tiếp theo.*

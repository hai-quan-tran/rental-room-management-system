# TỔNG HỢP PHIÊN LÀM VIỆC: TRIỂN KHAI BACKEND SPRING BOOT
*(File này gộp toàn bộ nội dung đã làm trong phiên triển khai backend — dùng để tra cứu nhanh hoặc làm ngữ cảnh cho các phiên tiếp theo, không cần đọc lại toàn bộ lịch sử chat. Đọc kèm `tong-hop-du-an-quan-ly-phong-tro.md` để có bối cảnh spec đầy đủ.)*

---

## 0. Phạm vi phiên này

Tiếp nối sau khi đã có `V1__init_schema.sql`, phiên này triển khai toàn bộ backend Spring Boot: dựng khung project → JPA entities → đầy đủ tầng repository/service/controller/DTO cho tất cả module nghiệp vụ → phát hiện và sửa các lỗi thực tế khi chạy với MySQL thật.

Vị trí project: `D:\Project\rental-room-management-system\rental-room-management-system-backend`

---

## 1. Khung project (pom.xml, package structure)

- **Spring Boot 4.1.0**, Java 21, `groupId=com.rentalroom`, base package `com.rentalroom.management`
- Dependencies chính: web, data-jpa, mysql-connector-j, `spring-boot-starter-flyway` + flyway-mysql, security, jjwt 0.13.0, validation, data-redis + cache, lombok, configuration-processor; test: starter-test, spring-security-test, H2
- Package structure: `config/`, `common/` (ApiResponse, PageResponse), `exception/` (BusinessException, ErrorCode, GlobalExceptionHandler), `security/`, `entity/`, `repository/`, `service/`, `controller/`, `dto/request/`, `dto/response/`, `enums/`
- `application.yml`: datasource MySQL qua biến môi trường (`DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`), JPA `ddl-auto: validate` (Flyway chủ quản schema), Redis, JWT config
- Migration `V1__init_schema.sql` copy vào `src/main/resources/db/migration/` — **phải đồng bộ lại mỗi khi file gốc trong `rental-room-management-system-db` được cập nhật**

### Security
- `SecurityConfig`: stateless JWT hoàn toàn, **không có** `AuthenticationManager`/`UserDetailsService` — `AuthService.login()` kiểm tra username/password trực tiếp qua `AccountRepository` + `PasswordEncoder`. `@EnableMethodSecurity` để dùng `@PreAuthorize`.
- `JwtTokenProvider`: sinh access token (claims: userId, username, role, branchIds) + refresh token; `remainingValidity()` để tính TTL blacklist.
- `JwtAuthenticationFilter`: đọc token từ header, kiểm tra Redis blacklist (`jwt:blacklist:<sha256(token)>`) trước khi set `Authentication`.
- `SecurityUtils`: helper tĩnh đọc `UserPrincipal` hiện tại từ `SecurityContextHolder`; `assertCanAccessBranch(branchId)` — nền tảng của toàn bộ RBAC branch-scoping.
- `TokenHasher`: SHA-256 hex, dùng cho cả blacklist key và refresh token hash lưu DB.

---

## 2. JPA Entities

Toàn bộ 16 bảng trong schema + `ContractTenantId` (composite key) + `BranchRoomSummaryId`/`BranchRoomSummary` (map read-only view `v_branch_room_summary`, `@Immutable`).

**Quy ước áp dụng:**
- Lombok `@Getter/@Setter/@NoArgsConstructor` — **không dùng `@Data`/`@EqualsAndHashCode`** trên entity (tránh lỗi proxy/collection kinh điển của Hibernate); `equals`/`hashCode` viết tay theo `id`.
- `@CreationTimestamp`/`@UpdateTimestamp` (Hibernate) cho mọi cột `created_at`/`updated_at` thay vì chỉ dựa vào DB default.
- `created_by`/`updated_by`/`checked_by` là field `Long` thô, không phải quan hệ `@ManyToOne` tới Account (tránh kéo theo association không cần thiết chỉ vì audit).
- Cột generated ở DB (`total_amount`, `remaining_amount` của `monthly_bill`) map với `insertable=false, updatable=false`.
- 5 enum mới: `RoomStatus`, `ContractStatus`, `PaymentStatus`, `ChecklistItemStatus`, `DebtStatus` (cộng `Role` có sẵn từ trước).

**Lưu ý quan trọng — lỗi kiểu cột UNSIGNED (đã gặp thật và đã sửa, xem mục 5):**
- `monthly_bill.bill_month` (TINYINT UNSIGNED), `bill_year` (SMALLINT UNSIGNED) → cần `@JdbcTypeCode(SqlTypes.TINYINT/SMALLINT)` trên field `Integer`
- `v_branch_room_summary.empty_room_count`/`occupied_room_count` (từ `SUM(CASE...)`, MySQL trả `DECIMAL` chứ không phải `BIGINT` như `COUNT()`) → cần `@JdbcTypeCode(SqlTypes.DECIMAL)` trên field `Long`
- Bất kỳ cột UNSIGNED hẹp hoặc cột view tính toán mới thêm sau này đều cần kiểm tra lại kiểu tương tự khi chạy với MySQL thật.

---

## 3. Tầng nghiệp vụ đầy đủ (repository + service + controller + DTO)

Thiết kế REST theo hướng tài nguyên (resource-oriented), **không** gộp toàn bộ chi tiết phòng thành 1 DTO khổng lồ — mỗi tab màn hình Phòng trọ (spec 4.4) là 1 nhóm endpoint riêng, do 1 service riêng quản lý.

| Module | Endpoint chính | Ghi chú |
|---|---|---|
| **Auth** | `/api/auth/login,refresh,logout` | JWT access+refresh, refresh token hash lưu DB để revoke, access token blacklist Redis khi logout |
| **Account** | `/api/accounts/**` | Chỉ `ADMIN_TONG`, soft-delete qua `isActive` |
| **Tenant** | `/api/tenants/**` | Cả 2 role — **cố ý KHÔNG branch-scope** (xem mục 4) |
| **Branch** | `/api/branches/**` | `ADMIN_CAP_1` tự lọc theo chi nhánh quản lý; room-type summary cache Redis (`v_branch_room_summary`) |
| **RoomType + handover items** | `/api/room-types/**`, `/api/room-types/{id}/handover-items` | Đọc cả 2 role, ghi chỉ `ADMIN_TONG`; thay toàn bộ danh sách vật dụng (replace-all), cache Redis |
| **Room** | `/api/branches/{branchId}/rooms`, `/api/rooms/{id}` | Branch-scoped |
| **ExtraFeeCategory** | `/api/extra-fee-categories/**` | Danh mục toàn hệ thống, đọc cả 2 role |
| **Contract** | `/api/rooms/{roomId}/contracts`, `/api/contracts/{id}`, `/api/contracts/{id}/tenants/**` | Tạo hợp đồng bắt buộc đúng 1 người đại diện; chuyển trạng thái phòng TRONG↔DANG_THUE |
| **Billing** | `/api/contracts/{id}/monthly-bills`, `/api/monthly-bills/{id}/extra-fee-items`, `/api/monthly-bills/{id}/payments` | Tính tiền thuê theo tỷ lệ ngày ở (`RentCalculator`, có unit test riêng); ghi payment dựa vào trigger DB (xem mục 4) |
| **Checkout + DebtRecord** | `/api/contracts/{id}/checkout`, `/api/debt-records/**` | Tính lại tiền thuê tháng trả phòng, cộng dồn công nợ chưa thu, tự tạo `DebtRecord` nếu vượt cọc |
| **Dashboard** | `/api/dashboard` | 3 biểu đồ theo role, `ADMIN_CAP_1` luôn bị khóa theo chi nhánh JWT (bỏ qua param client), cache Redis TTL ngắn |

---

## 4. Quyết định kiến trúc quan trọng (đọc trước khi sửa code liên quan)

- **Branch-scoping pattern nhất quán**: mọi service đụng tới dữ liệu thuộc chi nhánh gọi `SecurityUtils.assertCanAccessBranch(branchId thật của entity)` — **luôn** lấy từ entity đang thao tác (`room.getBranch().getId()`, `contract.getRoom().getBranch().getId()`...), **không bao giờ** tin `branchId` client gửi lên.
- **Tenant KHÔNG branch-scope — đây là chủ đích, không phải bug**: bảng RBAC trong spec ghi "quản lý người thuê" cho `ADMIN_CAP_1` mà không kèm điều kiện "thuộc chi nhánh được gán" (khác Room/Dashboard); đồng thời tính năng "chọn từ danh sách Tenant có sẵn" khi tạo hợp đồng (spec 4.4.7) cần tra cứu CCCD toàn hệ thống (một người có thể từng thuê ở chi nhánh khác). Không thêm branch filter vào `TenantService` nếu chưa xác nhận lại lý do này không còn đúng.
- **Payment ↔ trigger DB**: `trg_payment_after_insert` tự cập nhật `paid_amount`/`payment_status` sau khi insert `Payment`. `BillingService.recordPayment()` **không** tự cập nhật các field này (sẽ cộng dồn sai) — chỉ `save()` rồi `entityManager.flush()+refresh()` để đọc lại giá trị trigger đã ghi. Mọi chỗ insert `Payment` mới đều phải theo đúng pattern flush+refresh này.
- **Tính lại tiền thuê tháng trả phòng**: `BillingService.upsertRentForCheckoutMonth()` (package-private, chỉ gọi từ `CheckoutService`) tính lại `rentAmount` của tháng trả phòng sau khi biết `contract.endDate` chính xác — vì hóa đơn tháng đó có thể đã được tạo trước đó khi hợp đồng còn đang mở (chưa biết ngày kết thúc).
- **Dashboard cache**: `@Cacheable` đặt trực tiếp trên method public `getDashboard()` — **không** tách ra method private tự gọi (self-invocation sẽ vô hiệu hóa cache do giới hạn AOP proxy của Spring). Cache key gồm cả `branchId` lẫn `userId` hiện tại để tránh admin khác nhau dùng chung cache.
- **`RentCalculator`** (`service/RentCalculator.java`): utility tĩnh thuần, có unit test riêng (`RentCalculatorTest`, 6 case gồm năm nhuận) — spec yêu cầu bắt buộc coverage phần này.

---

## 5. Các lỗi thực tế đã gặp và đã sửa

1. **RBAC**: `DebtRecordService.list()` ban đầu không lọc theo chi nhánh cho `ADMIN_CAP_1` → đã thêm `findByStatusAndContract_Room_BranchIdIn`.
2. **Flyway không chạy (database trống hoàn toàn, không cả bảng `flyway_schema_history`)**: Spring Boot 4.x đã tách autoconfiguration của Flyway ra module riêng `org.springframework.boot:spring-boot-flyway` — chỉ khai báo `flyway-core` (engine) là **không đủ**, Spring Boot không hề nhận diện Flyway (không log gì liên quan, kể cả banner version). **Dấu hiệu nhận biết**: `SchemaManagementException: missing table [X]` trên schema trống, log không có dòng nào chứa "Flyway". **Cách sửa**: dùng `spring-boot-starter-flyway` thay vì `flyway-core` trực tiếp (vẫn giữ `flyway-mysql` riêng).
3. **Lệch kiểu cột UNSIGNED** (xem chi tiết mục 2) — đã sửa bằng `@JdbcTypeCode`.

**Đã xác minh thành công với MySQL 8 thật chạy sẵn trên máy** (`root@localhost:3306`, MySQL Shell tại `C:\Program Files\MySQL\MySQL Shell 8.0\bin\mysqlsh`): Flyway tạo đủ 16 bảng + view + 5 trigger, Hibernate validate pass, `mvn spring-boot:run` boot thành công. Toàn bộ `mvn clean test` xanh (7/7, gồm cả smoke test boot full Spring context bằng H2).

---

## 6. Việc còn lại

- [ ] Frontend Angular + PrimeNG (chưa bắt đầu)
- [ ] Test tích hợp sâu hơn với dữ liệu thật (mới verify schema/boot, chưa test luồng nghiệp vụ end-to-end qua API thật)
- [ ] Unit test Mockito cho các service khác ngoài `RentCalculator` (spec yêu cầu coverage cho "chi phí phát sinh", "cập nhật công nợ" — hiện chỉ có test cho phần tính tiền thuê)
- [ ] Deploy/Docker Compose cho MySQL + Redis (hiện đang chạy MySQL cài sẵn trên máy, chưa containerize)

---

*File này bổ sung cho `tong-hop-du-an-quan-ly-phong-tro.md` — đọc cả 2 file để có ngữ cảnh đầy đủ trước khi tiếp tục phát triển.*

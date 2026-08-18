# TỔNG HỢP PHIÊN LÀM VIỆC 12: VALIDATE NGƯỜI THUÊ/ĐẠI DIỆN, CONFIRM DIALOG CHO NÚT LƯU, PLAN EMAIL NHẮC HÓA ĐƠN
*(Nối tiếp `tong-hop-phien-11-xac-nhan-hoa-don-khoa-chinh-sua.md`. Phiên này gồm 3 việc độc lập:
2 việc đã code + compile-verify xong, 1 việc mới dừng ở plan theo yêu cầu người dùng.)*

---

## 1. Migration `V8`: 3 quy tắc nghiệp vụ mới cho Người thuê / Đại diện hợp đồng

Dùng Plan Mode (research trực tiếp, không cần Explore agent vì phạm vi rõ) trước khi code. 3 quy tắc:

1. **CCCD chỉ bắt buộc nếu người thuê ≥ 18 tuổi** (tính động theo ngày sinh so với ngày hệ thống hiện tại) — dưới 18 thì không bắt buộc.
2. **Người được chọn làm đại diện ký hợp đồng bắt buộc phải có email** — chặn lưu (tạo hợp đồng mới / thêm người thuê làm đại diện) nếu không có.
3. **Sửa thông tin người thuê, nếu xóa email trong lúc người đó đang là đại diện của một hợp đồng đang ACTIVE** → chặn lưu. Hợp đồng đã kết thúc/checkout thì không bị chặn (đã hỏi và xác nhận với người dùng — chỉ tính hợp đồng đang hiệu lực).

### 1.1. Backend
- Migration `V8__tenant_id_card_optional_for_minors.sql`: `ALTER TABLE tenant MODIFY COLUMN id_card_number VARCHAR(20) NULL` — giữ nguyên unique index `uq_tenant_id_card` (MySQL cho phép nhiều dòng NULL).
- `Tenant.idCardNumber` bỏ `nullable = false`; `TenantRequest.idCardNumber` bỏ `@NotBlank` (giữ `@Pattern`, Bean Validation bỏ qua khi null).
- `TenantService`: thêm `assertCccdRequiredIfAdult(dateOfBirth, idCardNumber)` (`Period.between(dob, now()).getYears() >= 18`) gọi ở đầu `create()`/`update()` — bean validation chuẩn không làm được cross-field nên viết tay theo kiểu `DateValidationUtils` đã có. Chuẩn hóa CCCD rỗng → `null` trước khi lưu/check trùng (tránh đụng unique index giữa các người thuê vị thành niên); đổi so sánh CCCD cũ trong `update()` từ `.equals()` sang `Objects.equals()` (tenant cũ có thể đã CCCD null).
- `update()` thêm guard: nếu email rỗng **và** `contractTenantRepository.existsById_TenantIdAndRepresentativeTrueAndContract_Status(id, ACTIVE)` → chặn (method mới trong `ContractTenantRepository`, derived query kiểu underscore-navigate giống `existsById_ContractIdAndRepresentativeTrue` đã có).
- `ContractService.attachTenant()` (điểm chốt duy nhất set `is_representative`, dùng chung `create()`+`addTenant()`): nếu `representative==true` mà `tenant.email` rỗng → chặn (VALIDATION_ERROR). Vì class `@Transactional`, throw giữa vòng lặp `create()` rollback nguyên hợp đồng.

### 1.2. Frontend
- `date.util.ts` thêm `calculateAge(dateOfBirth): number` (tính đủ năm, có xét đã qua sinh nhật năm nay chưa).
- **2 nơi tạo Tenant riêng biệt, cả 2 đều cần sửa** — dễ sót cái thứ 2: `tenant-detail-page.ts` (màn CRUD Tenant chính) VÀ `room-detail-page.ts`'s `submitNewTenant()` (dialog tạo nhanh người thuê ngay trong tab Hợp đồng). Cả 2 đổi guard CCCD từ bắt buộc tuyệt đối sang chỉ bắt buộc khi `isAdult()`/`tenantIsAdult()`.
- `tenant-detail-page.ts` thêm `emailRequiredAsRepresentative` (computed dựa trên `rentalHistory()` đã có sẵn field `representative`/`status`) chặn `save()` + hiện lỗi inline dưới field Email.
- `room-detail-page.ts`: `NewContractTenantRow` thêm field `email`; computed `newContractRepresentativeHasEmail` chặn `createContract()` + hiện lỗi inline (theo đúng mẫu `CONTRACT.NEED_AT_LEAST_ONE_TENANT` đã có sẵn — **không dùng toast**, dùng `<small class="field-error">` giống các validate khác trong cùng trang, đã kiểm tra template thật trước khi làm để không đoán sai pattern).
- `TenantResponse.idCardNumber`/`TenantRequest.idCardNumber`/`TenantInContractResponse.idCardNumber` nới kiểu `string | null`.

### 1.3. Kiểm thử
Backend compile-verify bằng `javac --release 17 -encoding UTF-8` (0 lỗi/182 file — nhớ `-encoding UTF-8` không thì mọi chuỗi tiếng Việt cũ báo lỗi "unmappable character", không phải bug thật). Frontend `ng build` sạch. **Chưa chạy migration V8 + `mvn clean test` thật trên JDK 21** — cần làm trước khi tin tưởng hoàn toàn.

---

## 2. Confirm dialog cho toàn bộ nút "Lưu" chính

Người dùng yêu cầu rà toàn bộ `src`, mọi nút lưu/xóa/sửa phải có confirm dialog. Dùng Explore agent quét toàn app: nút **Xóa** ở mọi nơi đã có `ConfirmService` sẵn từ trước (đúng quy ước cũ), nhưng **0 nút Lưu nào có** — 19 chỗ. Hỏi lại người dùng phạm vi: chọn **chỉ áp dụng cho 8 nút "Lưu" của form chính**, không đụng các form con trong Room Detail (tạo hợp đồng/hóa đơn/thanh toán/thêm phí...) và **không** áp dụng cho ô lưu inline trong lưới Meter Readings (người dùng từ chối — mỗi ô 1 popup sẽ rất phiền khi nhập nhiều phòng liên tiếp).

- Thêm key dùng chung `COMMON.SAVE_CONFIRM` ("Bạn có chắc chắn muốn lưu các thay đổi?") — 1 message chung cho cả 8 chỗ, không tách theo entity như `*.DELETE_CONFIRM` (lưu không có rủi ro đặc thù theo entity như xóa).
- 8 chỗ: `account-detail-page.ts`, `branch-detail-page.ts` (chỗ này phải inject `ConfirmService` mới vì Branch không có nút xóa nên trước đó chưa có), `employee-detail-page.ts`, `item-detail-page.ts`, `room-type-detail-page.ts`, `tenant-detail-page.ts` — mỗi cái `save()`; và `room-detail-page.ts` có 2 chỗ (`saveRoom()` tab 1, `saveEndDate()` tab 2).
- Pattern: bọc nguyên khối `save$.subscribe({...})` hiện có vào `confirmService.confirm(translate.instant('COMMON.SAVE_CONFIRM'), () => {...})` — validate guard (`if (!field()) return`) vẫn để **ngoài** confirm, chỉ phần gọi API mới bọc trong.

### 2.1. Kiểm thử — lần đầu Playwright verify chính tính năng mới (không chỉ `ng build`)
Cài Playwright+chromium vào scratchpad, `ng serve --port 4300` chạy thật với backend JDK 21 sống của người dùng, login `admin`/`admin`:
- Bấm "Lưu" ở Branch/Item detail → dialog hiện đúng nội dung mới.
- Bấm "Đồng ý" → API thật được gọi (nhận về lỗi validate nghiệp vụ có sẵn không liên quan — chứng minh luồng confirm→accept→API hoạt động đúng, không phải bug do thay đổi này).
- Bấm "Hủy" → API **không** được gọi, không rời trang.

`ng build` sạch. Đã dọn dev server/process sau khi test xong, không có thay đổi dữ liệu thật nào bị lưu trong lúc test.

---

## 3. Điều tra "lỗi giao diện" ở Loại phòng — kết luận: KHÔNG phải bug

Người dùng báo: mở 1 loại phòng có sẵn để sửa, select "vật dụng bàn giao" ở dòng đang tồn tại chỉ hiện 3 item và không cuộn được; thêm dòng mới thì chỉ có 2 item và cũng không cuộn được.

Tái hiện trực tiếp bằng Playwright (không lưu/sửa gì thật vào DB) trên loại phòng thật (branch "Chi nhánh Quận 9", room-type id 4): chi nhánh này có đúng 8 vật dụng, checklist bàn giao của loại phòng đó đã có 6 dòng dùng 6 vật dụng khác nhau. Hệ thống có quy tắc **1 vật dụng chỉ được gán vào 1 dòng duy nhất trong cùng 1 loại phòng** (chặn ở cả frontend `optionsForRow()` lẫn backend `RoomTypeService` — thông báo "Một vật dụng chỉ được chọn 1 lần trong danh sách"). Vì vậy dòng đang sửa chỉ còn 3 lựa chọn hợp lệ (8 - 5 dòng khác đã dùng), dòng mới chỉ còn 2 (8 - 6 dòng đã dùng) — đúng số liệu người dùng báo, không phải bug đếm sai.

Verify thêm: xóa tạm 5/6 dòng trên trình duyệt (không bấm Lưu) để 1 select hiện đủ cả 8 vật dụng — lúc đó danh sách dài hơn khung hiển thị (`scrollHeight` 254px > khung 200px) và **cuộn chuột hoạt động bình thường**. Vậy cơ chế cuộn của PrimeNG `p-select` không hề lỗi, chỉ là khi danh sách hợp lệ ngắn (3 hoặc 2 mục) thì vừa khít khung nên không có gì để cuộn — đúng thiết kế.

Đã hỏi lại người dùng có muốn đổi gì không (thêm ghi chú UI giải thích, hoặc bỏ hẳn quy tắc không cho chọn trùng) — **người dùng chọn giữ nguyên, không sửa gì**. Ghi lại phát hiện này để phiên sau không mất công điều tra lại cùng 1 "bug" không có thật.

---

## 4. Plan (chưa code): Email nhắc quản lý chi nhánh về hóa đơn thiếu/chưa xác nhận

Người dùng yêu cầu lên plan cho tính năng: job chạy ngày 7, 8, 9 hằng tháng, gửi email cho quản lý từng chi nhánh liệt kê các phòng đang cho thuê mà **chưa có hóa đơn tháng trước** hoặc **hóa đơn tháng trước còn `CHUA_XAC_NHAN`**, yêu cầu tạo/xác nhận hóa đơn; chi nhánh không có phòng nào như vậy thì không gửi. Đã research kỹ (2 Explore agent song song) + hỏi lại 2 điểm mấu chốt trước khi chốt plan — **người dùng nói "chấp nhận, tạm thời lưu lại, khoan thực hiện"**, nên phiên này dừng ở plan, chưa code. Ghi lại nguyên plan ở đây để không phụ thuộc vào file plan cục bộ (`~/.claude/plans/`, có thể bị phiên sau ghi đè/mất).

### 4.1. Phát hiện quan trọng khi research
- Hệ thống **chưa có** hạ tầng gửi email lẫn scheduled job nào (`pom.xml` không có `spring-boot-starter-mail`, không có `@Scheduled`/`@EnableScheduling` ở đâu cả) — tính năng hoàn toàn mới, không phải mở rộng cái có sẵn.
- `Account` **không có field email** — email quản lý chi nhánh phải tra qua `Employee.email` (liên kết 1-1 `employee.account_id`, owning side là Employee). Không phải account nào cũng có Employee liên kết (VD tài khoản admin seed sẵn) → phải xử lý null an toàn, bỏ qua + log warning chứ không lỗi cả job.
- `Branch.managerAccount` (`manager_account_id`) có thể `NULL` — chi nhánh không có quản lý thì bỏ qua.
- `monthly_bill` không có cột `room_id`/`branch_id` trực tiếp — chỉ nối được qua `contract.room_id`.

### 4.2. Quyết định đã chốt với người dùng
- **Tháng kiểm tra = tháng trước** (chạy ngày 7-9/8 → kiểm tra hóa đơn tháng 7) — khớp quy ước có sẵn trong hệ thống (Dashboard's `missingInvoiceRooms` đã mặc định `YearMonth.now().minusMonths(1)`).
- **Không kèm link phòng trong email** — chỉ liệt kê text (mã phòng + lý do: thiếu hóa đơn / chưa xác nhận).
- Job **stateless**, không lưu "đã gửi" — mỗi lần chạy (7, 8, 9) tự query lại, nếu vấn đề đã được xử lý thì tự nhiên không gửi nữa, đúng yêu cầu "không có phòng phù hợp thì không gửi" mà không cần thêm cột trạng thái.
- Không migration DB mới — tính năng chỉ đọc dữ liệu có sẵn.

### 4.3. Thiết kế backend (chưa code)
1. Thêm `spring-boot-starter-mail` vào `pom.xml` (SMTP chuẩn qua `JavaMailSender`, tương thích mọi nhà cung cấp).
2. `application.yml` thêm `spring.mail.*` (host/port/username/password qua biến môi trường) + `app.invoice-reminder.cron` (mặc định `0 0 8 7-9 * *` — 8h sáng ngày 7,8,9).
3. `EmployeeRepository` thêm `Optional<Employee> findByAccountId(Long accountId)` (hiện chỉ có `existsByAccountId`).
4. 1 query SQL trực tiếp mới trong `DashboardRepository` (không cần kiểu 2-query+Java-loop như `missingInvoiceRooms` vì chỉ cần đúng 1 tháng, không phải lookback nhiều tháng):
   ```sql
   SELECT r.id AS roomId, r.room_code AS roomCode, r.branch_id AS branchId, b.name AS branchName,
          CASE WHEN mb.id IS NULL THEN 'MISSING' ELSE 'UNCONFIRMED' END AS reason
   FROM contract c
   JOIN room r ON r.id = c.room_id
   JOIN branch b ON b.id = r.branch_id
   LEFT JOIN monthly_bill mb ON mb.contract_id = c.id AND mb.bill_year = :year AND mb.bill_month = :month
   WHERE c.status = 'ACTIVE' AND (mb.id IS NULL OR mb.payment_status = 'CHUA_XAC_NHAN')
   ORDER BY b.name, r.room_code
   ```
   Projection mới `RoomInvoiceActionRow` (roomId, roomCode, branchId, branchName, reason).
5. `EmailService` mới — wrapper mỏng quanh `JavaMailSender` (`sendHtml(to, subject, htmlBody)`).
6. `InvoiceReminderService` mới — `@Scheduled(cron = "${app.invoice-reminder.cron}")`: tính `targetYearMonth = now().minusMonths(1)` → query → nếu rỗng thì dừng → group theo branch → với mỗi branch tra `managerAccount` → `Employee.email` (null thì skip+log) → build nội dung → gửi (try/catch riêng từng chi nhánh, 1 chi nhánh lỗi không hỏng cả job). Kèm 1 endpoint thủ công `POST /api/admin/invoice-reminders/run` (ADMIN_TONG-only) để test không cần chờ đúng ngày 7-9.
7. Bật `@EnableScheduling`.

### 4.4. Việc cần làm khi triển khai thật (chưa làm)
- Người dùng tự điền `MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD` thật (agent không có SMTP thật để test gửi sống).
- Chạy migration (nếu có), `mvn clean test`, gọi thử endpoint trigger thủ công trước khi để `@Scheduled` chạy tự nhiên.

---

## 5. Việc còn lại / lưu ý cho phiên sau

- [ ] Chạy migration `V8` + `mvn clean test` thật bằng JDK 21, click-through UI theo checklist ở mục 1.3 (tenant dưới/đủ 18 tuổi, đại diện thiếu email, xóa email của đại diện đang active vs. đã kết thúc).
- [ ] Confirm dialog cho nút Lưu: đã live-test bằng Playwright, coi như xong — không còn việc treo.
- [ ] "Bug" select vật dụng bàn giao: đã xác nhận không phải bug, không cần làm gì thêm.
- [ ] **Email nhắc hóa đơn vẫn ở dạng plan, chưa có 1 dòng code nào** — khi người dùng sẵn sàng, triển khai theo đúng mục 4 (không cần research lại, plan đã đủ chi tiết).
- [ ] Zalo integration, Giai đoạn 2-3 tự động hóa điện/nước vẫn treo từ các phiên trước, không thuộc phạm vi phiên này.

---

*File này bổ sung cho `tong-hop-phien-11-xac-nhan-hoa-don-khoa-chinh-sua.md` và các file trước đó — đọc từ file mới nhất lùi dần nếu cần đầy đủ ngữ cảnh.*

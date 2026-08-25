# TỔNG HỢP PHIÊN LÀM VIỆC 15: EMAIL NHẮC THANH TOÁN HÓA ĐƠN GỬI NGƯỜI ĐẠI DIỆN, NGÀY 11 HÀNG THÁNG

*(Nối tiếp `tong-hop-phien-14-fix-button-css-va-vietqr-giai-doan-1.md`.)*

---

## 1. Yêu cầu

Bổ sung 1 luồng email tự động **mới, độc lập** với tính năng `InvoiceReminderService` đã có (phiên 13): luồng cũ chạy ngày 7-9, gửi cho **quản lý chi nhánh**, chỉ liệt kê phòng thiếu/chưa xác nhận hóa đơn. Luồng mới này chạy ngày 11, gửi trực tiếp cho **người đại diện hợp đồng**, nội dung là **chi tiết hóa đơn tháng trước đã tồn tại** (đã xác nhận, còn nợ) — liệt kê từng khoản phí, riêng Điện/Nước ghi rõ số cũ/số mới — để nhắc họ thanh toán.

Đi qua Plan Mode (research trực tiếp, không cần Explore agent vì đã có đủ ngữ cảnh từ phiên 13/14) trước khi code. 2 quyết định chốt qua `AskUserQuestion`:
- Phạm vi hóa đơn: **chỉ hóa đơn tháng liền trước**, điều kiện `payment_status != CHUA_XAC_NHAN` và `remaining_amount > 0`.
- Email **có kèm ảnh mã QR VietQR** (tái dùng cơ chế Quick Link `img.vietqr.io` đã làm ở Room Detail phiên 14).

## 2. Thiết kế & code

- **`application.yml`**: thêm `app.tenant-bill-reminder.cron` (mặc định `0 0 8 11 * *`), song song với `app.invoice-reminder.cron` đã có.
- **Repository**: `MonthlyBillRepository.findByBillYearAndBillMonth` (query theo tháng, lọc điều kiện xác nhận/còn nợ bằng Java stream — theo đúng tiền lệ `missingInvoiceRooms` ưu tiên vòng lặp Java dễ review hơn SQL phức tạp); `ContractTenantRepository.findById_ContractIdAndRepresentativeTrue` (trigger DB đã đảm bảo tối đa 1 đại diện/hợp đồng nên `Optional` là đủ an toàn).
- **`common/util/VietQrUtil.java`** (mới): bản Java tương đương `shared/utils/vietqr.util.ts` (frontend) — vì email dựng HTML phía server nên không tái dùng được code TypeScript. Giữ đúng format `addInfo` = `"RRMS {roomCode} T{billMonth}.{billYear}"` không dấu, khớp với Room Detail để đối soát thủ công nhất quán dù khách trả qua QR trong mail hay trong app.
- **`TenantBillReminderService`** (mới): theo đúng khung `InvoiceReminderService` (stateless — không lưu trạng thái "đã gửi", mỗi lần chạy tự truy vấn lại; mỗi hóa đơn xử lý trong try/catch riêng để 1 lỗi không chặn cả lô). Với mỗi hóa đơn đủ điều kiện: tìm người đại diện qua `ContractTenant`, bỏ qua + log warn nếu không có đại diện hoặc đại diện không có email (phòng thủ — dù quy tắc nghiệp vụ phiên 12 đã bắt buộc đại diện phải có email); dựng bảng chi phí (Tiền phòng, Wifi/Giữ xe nếu > 0, từng `ExtraFeeItem`); với category `isMetered()` (Điện/Nước) tra thêm `MeterReadingRepository` theo room+category+tháng để in số cũ/số mới/tiêu thụ — nếu không có reading (trường hợp fallback "0 + note" của Stage-1 billing) thì chỉ hiện số tiền, không suy diễn số liệu; dựng QR qua `VietQrUtil` (bỏ qua nếu chi nhánh chưa cấu hình ngân hàng, email vẫn gửi bình thường).
- **`TenantBillReminderController`** (mới): `POST /api/admin/tenant-bill-reminders/run`, `ADMIN_TONG`-only, đúng khuôn `InvoiceReminderController` — trigger thủ công để test không cần chờ ngày 11.
- **`TenantBillReminderRunResponse`** (mới): `(targetYear, targetMonth, billsFound, emailsSent, skipped)`.
- Không có migration — tính năng chỉ đọc dữ liệu sẵn có (`monthly_bill`, `extra_fee_item`, `meter_reading`, `contract_tenant`, `branch.bank_*` đã có từ V9).

## 3. Kiểm thử — chạy thật trên JDK 21, không chỉ compile-verify

`TenantBillReminderServiceTest` (8 case, Mockito-only, đúng convention: mock repository/`EmailService`, `@MockitoSettings(strictness = Strictness.LENIENT)`): không có hóa đơn đủ điều kiện; hóa đơn `CHUA_XAC_NHAN` bị loại; hóa đơn đã thanh toán hết (`remaining=0`) bị loại; không tìm thấy đại diện → skip; đại diện email blank → skip; happy path có `MeterReading` → assert email chứa đúng số cũ/mới qua `ArgumentCaptor`/matcher; chi nhánh chưa cấu hình ngân hàng vẫn gửi được (không QR); `EmailService` ném `MessagingException` → tính vào skipped, không throw.

Session này có sẵn JDK 21 trong môi trường agent (không cần workaround javac-17 như nhiều phiên trước) — chạy `mvn test` thật cho cả module mới lẫn toàn bộ suite: **194/194 pass** (186 cũ + 8 mới), bao gồm `ApplicationContextSmokeTest` (Spring context boot thật trên H2) xác nhận bean `TenantBillReminderService`/`TenantBillReminderController` nạp đúng, không xung đột với `@Scheduled` cron mới.

**Chưa kiểm thử được**: không có backend đang chạy live + không có `MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD` thật trong phiên này, nên **chưa gửi được email thật**, chưa gọi `POST /api/admin/tenant-bill-reminders/run` qua HTTP thật, và `@Scheduled` chưa từng chạy live. Trước khi tin tưởng tính năng: thiết lập SMTP thật, khởi backend JDK 21, gọi endpoint thủ công, kiểm tra hộp thư nhận thật — đối chiếu số cũ/số mới đúng với `/meter-readings`, đối chiếu QR quét ra đúng `remainingAmount`.

## 4. Tách cấu hình `application.yml` thành dev/prod, cấu hình + verify SMTP Gmail thật

Sau khi tính năng mục 1-3 xong, người dùng cung cấp 1 tài khoản Gmail cá nhân + App Password thật để dùng làm SMTP server cho môi trường dev, và yêu cầu tách hẳn cấu hình theo môi trường thay vì 1 file `application.yml` dùng chung có default trộn lẫn dev/prod như trước.

**Cấu trúc mới** (`rental-room-management-system-backend/src/main/resources/`):
- **`application.yml`** — chỉ còn phần chung, không môi trường-cụ thể, không bí mật: tên app, `jpa`/`flyway`, `server.port`, `management`, `app.jwt.*-expiration-*`, `app.invoice-reminder.cron`, `app.tenant-bill-reminder.cron`, `logging`. Thêm `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` — mặc định chạy `mvn spring-boot:run` bình thường sẽ tự vào profile `dev`, không cần set gì thêm.
- **`application-dev.yml`** (mới) — datasource/redis local (`localhost`, `root/admin`), `app.jwt.secret` dev, và **`spring.mail.*` trỏ thẳng vào Gmail SMTP thật của người dùng** (`smtp.gmail.com:587` + App Password thật) để dev dùng được ngay không cần set biến môi trường.
- **`application-prod.yml`** (mới) — toàn bộ datasource/redis/mail/jwt đều là `${VAR}` **không có giá trị mặc định** — thiếu biến nào là Spring Boot báo lỗi ngay lúc khởi động thay vì âm thầm chạy với giá trị rỗng/không an toàn.

**Bảo mật**: vì `application-dev.yml` chứa App Password thật, đã thêm dòng `application-dev.yml` vào `.gitignore` gốc — đi ngược lại quy ước "commit gần như mọi thứ" của các phiên trước, nhưng là ngoại lệ bắt buộc vì đây là bí mật thật, không phải code. Đã xác nhận bằng `git status --ignored` (`!!`) rằng file không lọt vào bất kỳ commit nào.

**Tác dụng phụ phát hiện khi test**: `application-test.yml` (profile `test`, dùng H2 cho `ApplicationContextSmokeTest`) trước giờ "ăn ké" key `spring.mail.host: ""` (rỗng) từ `application.yml` gốc — đủ để Spring Boot's `MailSenderAutoConfiguration` tạo bean `JavaMailSender` dù không dùng thật. Sau khi dọn hết `spring.mail.*` khỏi file gốc, profile `test` không còn key này nữa → bean `JavaMailSender` không được tạo → `EmailService`/`InvoiceReminderService`/`TenantBillReminderService` fail autowire → `ApplicationContextSmokeTest` đỏ. Fix: thêm 1 dòng tối thiểu `spring.mail.host: localhost` vào `application-test.yml` — không test nào thật sự gửi mail (toàn bộ mock `EmailService`), chỉ cần đủ để Spring nạp bean.

**Verify thật, không chỉ compile**:
- `mvn test`: vẫn 194/194 sau khi tách file.
- Khởi động backend thật với profile `dev` (mặc định) — log xác nhận Hikari connect MySQL thật, Flyway validate đúng 9 migration, Tomcat start port 8080 — toàn bộ config datasource/redis/jwt đọc đúng từ `application-dev.yml`.
- **Gửi thử 1 email SMTP thật qua Gmail** để xác nhận App Password hoạt động — dùng 1 script Java độc lập (`JavaMailSenderImpl` y hệt cách `EmailService` dùng, biên dịch bằng classpath từ `mvn dependency:build-classpath`) gửi **thẳng về email của chính người dùng**, không gọi qua `/api/admin/*-reminders/run` — vì kiểm tra dữ liệu seed (`dev-seed/02_gen_tenants_rooms_contracts.js`) phát hiện tenant demo dùng domain `@gmail.com` thật (không phải `example.com`), gọi thẳng API rủi ro gửi nhầm email thật cho người lạ. Kết quả: gửi thành công, người dùng xác nhận nhận được email trong hộp thư.

**Nơi dùng `spring.mail.*` trong code** (đã giải thích cho người dùng, ghi lại vì không hiển nhiên): không có `MailConfig.java`/`@Bean` thủ công nào — `spring-boot-starter-mail` (`pom.xml`) tự cấu hình sẵn 1 bean `JavaMailSender` từ đúng các key `spring.mail.*` này lúc khởi động. `EmailService` (constructor-inject `JavaMailSender`) là **nơi duy nhất trong toàn bộ app chạm vào bean này trực tiếp** (đã ghi rõ trong Javadoc class từ phiên 13); `InvoiceReminderService` và `TenantBillReminderService` chỉ gọi `emailService.sendHtml(...)`, không biết gì về SMTP.

## 5. Việc còn lại

- [ ] Các mục tồn đọng từ phiên 14 vẫn còn nguyên (login-page CSS bug, VietQR Giai đoạn 2, đối soát thanh toán tự động, Dashboard/Angular test tự động, Zalo integration).

---

*File này bổ sung cho `tong-hop-phien-14-fix-button-css-va-vietqr-giai-doan-1.md` và các file trước đó — đọc từ file mới nhất lùi dần nếu cần đầy đủ ngữ cảnh.*

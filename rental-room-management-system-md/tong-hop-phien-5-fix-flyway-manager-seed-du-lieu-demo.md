# TỔNG HỢP PHIÊN LÀM VIỆC 5: FIX FLYWAY CHECKSUM + NGƯỜI QUẢN LÝ CHI NHÁNH + SEED DỮ LIỆU DEMO
*(Nối tiếp `tong-hop-phien-4-phi-phong-vat-dung-checkout.md`. Phiên này không thay đổi kiến trúc
gì lớn — chủ yếu là fix vận hành (Flyway) + 1 fix nhỏ frontend + tạo bộ dữ liệu demo đầy đủ để
test giao diện. Đọc kèm file phiên 4 để hiểu ngữ cảnh Item/RoomType/checklist trước khi đọc phiên
này.)*

---

## 0. Bối cảnh đầu phiên

Người dùng đã xóa schema DB và chạy lại toàn bộ Flyway (V1→V3) — DB sạch, không còn tài khoản
nào để đăng nhập.

---

## 1. Seed tài khoản admin mặc định vào V1

- Thêm 1 dòng `INSERT INTO account` vào cuối phần `CREATE TABLE account` trong
  `V1__init_schema.sql` (cả bản gốc ở `rental-room-management-system-db/` lẫn bản copy ở
  `backend/src/main/resources/db/migration/`, đúng quy ước 2 nơi phải giống hệt nhau).
- `username: admin`, `password: admin`, `role: ADMIN_TONG`.
- Hash BCrypt của `password_hash` được sinh **đúng bằng `BCryptPasswordEncoder`** mà
  `SecurityConfig.java` dùng thật (không tự chế hash) — compile 1 class Java nhỏ với classpath
  trỏ thẳng vào jar `spring-security-crypto` + `commons-logging` sẵn có trong `~/.m2`, gọi
  `encode("admin")` rồi `matches("admin", hash)` để xác nhận lại trước khi ghi vào file.

## 2. Lỗi Flyway "Migration checksum mismatch" sau khi sửa V1

Vì V1 đã được áp dụng vào DB (từ lần chạy trước) rồi mới bị sửa nội dung, Flyway phát hiện
checksum trong `flyway_schema_history` (`616635583`) khác với checksum tính từ file mới
(`857077972`) → backend không start được.

**Cách xử lý (chỉ áp dụng đúng lần này, vì đã lỡ sửa migration cũ)**:
1. `UPDATE flyway_schema_history SET checksum = 857077972 WHERE version = '1'` — đồng bộ lại
   checksum (tương đương `flyway repair`, làm trực tiếp bằng SQL cho nhanh).
2. **Chèn thủ công** dòng account admin bằng `INSERT` trực tiếp — vì bước 1 chỉ sửa checksum,
   **không** tự chạy lại nội dung SQL đã đổi.
3. Xác nhận `SELECT id, username, role FROM account` ra đúng 1 dòng admin/ADMIN_TONG.

**Quy tắc để tránh lặp lại lỗi này về sau** (đã giải thích cho người dùng):
- **Trong giai đoạn dev hiện tại** (schema chưa ổn định, DB local chưa có data thật quan trọng):
  mỗi khi sửa lại 1 file version *đã* chạy rồi, **xóa và tạo lại schema** rồi để Flyway chạy lại
  từ đầu — không sửa checksum thủ công. An toàn, nhanh, không cần biết checksum.
- **Khi đã có dữ liệu thật / nhiều môi trường dùng chung DB** (giai đoạn sau này): chuyển hẳn
  sang quy tắc chuẩn của Flyway — **không bao giờ sửa lại 1 file version đã từng chạy**, mọi
  thay đổi dù nhỏ phải là 1 file version mới (`V4__...`, `V5__...`, ...).

## 3. Fix: chọn người quản lý chi nhánh không cho chọn ADMIN_TONG

- Bug: `AccountService.listManagerOptions()` (frontend, `account.service.ts`) trước đó lọc cứng
  `role: ADMIN_CAP_1`, nên dropdown "Người quản lý" ở màn Chi nhánh không có tài khoản ADMIN_TONG
  nào để chọn — dù màn Chi nhánh vốn ADMIN_TONG-only nên chọn chính ADMIN_TONG làm quản lý là hợp
  lý.
- Fix: gọi song song 2 request lọc theo từng role (`ADMIN_TONG` + `ADMIN_CAP_1`) bằng
  `forkJoin`, gộp kết quả — không cần đổi gì ở backend (`GET /api/accounts?role=...` đã hỗ trợ
  sẵn) hay `branch-detail-page.ts`.

## 4. Seed dữ liệu demo đầy đủ (chạy trực tiếp vào DB dev, KHÔNG phải Flyway migration)

Người dùng yêu cầu tạo sẵn dữ liệu để test giao diện. Vì đây là dữ liệu demo/dev thuần túy
(khác với account admin ở mục 1 — đó là dữ liệu **bootstrap bắt buộc** để có thể đăng nhập), nó
**không** được đưa vào file Flyway migration — chạy thẳng qua `mysql` client vào DB dev hiện tại.
Script được giữ lại ở `rental-room-management-system-db/dev-seed/` (xem README trong đó) để chạy
lại được khi cần, thay vì mất đi sau phiên chat.

Dữ liệu đã tạo:
- **2 chi nhánh**: Quận 9, Bình Dương — cả 2 đều do tài khoản `admin` quản lý.
- **16 vật dụng** (8 loại × 2 chi nhánh): Tivi, Tủ lạnh, Máy giặt, Bồn rửa tay(bếp), Bồn rửa
  tay(nhà vệ sinh), Vòi sen, Bồn cầu, Đèn điện — đúng giá/tổng tồn kho/số lượng mặc định người
  dùng cho trước (Bình Dương có tổng tồn kho lớn hơn Quận 9).
- **4 loại phòng** (2 loại × 2 chi nhánh): "Có gác(TV&MG)" (đủ 8 vật dụng bàn giao, gồm cả Tivi +
  Máy giặt) và "Có gác(Thường)" (6 vật dụng, không có Tivi/Máy giặt).
- **200 người thuê ngẫu nhiên**: sinh bằng script Node (`dev-seed/02_gen_tenants_rooms_contracts.js`).
  - Tuổi 18–60 (thỏa ràng buộc "phải từ 18 tuổi trở lên").
  - CCCD 12 số, SĐT 10 số theo đầu số thật của nhà mạng VN — cả 2 đảm bảo không trùng nhau
    trong tập 200 người.
  - Email dạng `ten-hotenlot@gmail.com` (bỏ dấu, viết liền — vd "Nguyễn Trung Anh" →
    `anh-nguyentrung@gmail.com`), ~70% người có email/30% không (nullable), nếu trùng thì tự
    thêm số ngẫu nhiên vào cuối phần local-part trước khi thêm lại `@gmail.com`.
- **85 phòng** có hợp đồng đang `ACTIVE`:
  - Quận 9: 15 phòng "Có gác(TV&MG)" (3.500.000đ, wifi 50.000đ) + 20 phòng "Có gác(Thường)"
    (2.800.000đ, wifi 50.000đ).
  - Bình Dương: 20 phòng "Có gác(TV&MG)" (3.300.000đ) + 30 phòng "Có gác(Thường)" (2.600.000đ).
  - Mỗi hợp đồng: `start_date` trong quá khứ (1-6 tháng trước), `end_date` = start + 12-24 tháng
    (đảm bảo luôn ở tương lai, thỏa yêu cầu "kết thúc tương lai, cách start ít nhất 1 năm").
    Tiền cọc mặc định = 1 tháng tiền thuê (không có trong yêu cầu gốc, tự chọn quy ước phổ biến).
- **Số người thuê / phòng** (sau 2 vòng chỉnh theo yêu cầu bổ sung — xem mục 4.1): 25 phòng
  1 người, 26 phòng 2 người, 18 phòng 3 người, 12 phòng 4 người, 3 phòng 5 người, 1 phòng 6
  người — dùng hết toàn bộ 200/200 người thuê, mỗi hợp đồng có đúng 1 người đại diện
  (`is_representative = 1`), không ai đứng tên 2 phòng cùng lúc.

### 4.1. 2 lượt chỉnh thêm người ở ghép (theo yêu cầu sau khi xem kết quả ban đầu)

Ban đầu mỗi phòng chỉ có 1 người thuê đại diện. Người dùng yêu cầu bổ sung dần:
1. *"Thêm ngẫu nhiên vài phòng có 2,3 người thuê"* → chọn ngẫu nhiên 15/85 phòng, lấy người thuê
   từ nhóm chưa gắn phòng nào, chia 10 phòng +1 người (thành 2), 5 phòng +2 người (thành 3).
2. *"Chỉ chừa lại 25 phòng có 1 người thuê, còn lại người thuê chưa có phòng thì thêm vào các
   phòng"* → trong số 70 phòng vẫn còn 1 người lúc đó, chọn ngẫu nhiên 45 phòng để nhận thêm
   người (giữ nguyên 25 phòng còn lại), rải hết 95 người thuê chưa có phòng vào 45 phòng này
   (đảm bảo mỗi phòng được chọn có ít nhất +1 người, phần dư rải ngẫu nhiên nên có phòng nhận
   nhiều hơn) → ra kết quả phân bố ở mục 4 trên.

Cả 2 bước đều dùng SQL với `TEMPORARY TABLE` + `ROW_NUMBER() OVER (ORDER BY RAND())` để chọn
ngẫu nhiên không trùng, chạy trực tiếp qua `mysql` client (xem
`dev-seed/03_add_roommates_round1.sql` và `dev-seed/04_fill_remaining_tenants_into_rooms.sql`).

## 5. Commit

Toàn bộ thay đổi code của phiên 4 (đã có từ trước phiên 5, chưa từng commit) + 2 fix nhỏ của
phiên 5 (seed admin, chọn ADMIN_TONG làm quản lý) được tách thành 2 commit riêng để lịch sử rõ
ràng, không dồn chung:
1. `Add room wifi/parking fees, branch-scoped room types, item inventory, and split-quantity checkout checklist`
2. `Seed default admin account and allow ADMIN_TONG as branch manager`

Dữ liệu demo (mục 4) **không nằm trong commit nào** — nó chỉ tồn tại trong DB dev hiện tại của
người dùng, không phải trong Flyway migration hay Git. Script sinh dữ liệu đã lưu lại ở
`rental-room-management-system-db/dev-seed/` để tái tạo khi cần (vd sau khi xóa schema).

---

## 6. Việc còn lại / lưu ý cho phiên sau

- [ ] Vẫn chưa build backend bằng JDK 21 thật kỹ ngoài việc khởi động ứng dụng (chỉ xác nhận
  Spring Boot start được và Flyway V1-V3 áp dụng thành công trong phiên này) — chưa chạy unit
  test `ItemServiceTest`/`CheckoutServiceTest` mới của phiên 4.
- [ ] Chưa test qua giao diện các luồng nghiệp vụ với dữ liệu demo mới (tạo hóa đơn tháng, thanh
  toán, trả phòng/checklist với vật dụng hư hỏng/mất, xem danh sách phòng có nhiều người thuê...).
- [ ] Dashboard vẫn dùng dữ liệu mẫu, chưa nối API thật (tồn đọng nhiều phiên, chưa ai đụng tới).
- [ ] Nếu DB dev bị xóa/tạo lại, chạy lại theo đúng thứ tự trong
  `rental-room-management-system-db/dev-seed/README.md` để có lại dữ liệu demo (lưu ý bước 2
  sinh dữ liệu **ngẫu nhiên khác** mỗi lần chạy, không phải bản sao y hệt phiên này).

---

*File này bổ sung cho `tong-hop-phien-4-phi-phong-vat-dung-checkout.md` và các file trước đó —
đọc từ file mới nhất lùi dần nếu cần đầy đủ ngữ cảnh.*

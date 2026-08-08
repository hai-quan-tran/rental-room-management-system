# TỔNG HỢP PHIÊN LÀM VIỆC 7: NỐI DASHBOARD VỚI API THẬT + FIX BUG REDIS CACHE

*(Nối tiếp `tong-hop-phien-6-dashboard-mockup-va-util.md`. Phiên này hoàn thành việc nối Dashboard
với backend thật (tồn đọng từ phiên 1), phát hiện và fix 2 lớp bug Redis cache khác nhau, tìm ra
phương pháp compile-verify backend mà không cần JDK 21, và fix vài bug UI nhỏ phát hiện khi test
trực tiếp trên trình duyệt với backend live.)*

---

## 1. Dashboard: nối `GET /api/dashboard` thật, xoá toàn bộ mock data

Phát hiện quan trọng: không chỉ 3 bảng mới ở phiên 6 dùng mock — **toàn bộ Dashboard** (biểu đồ
trạng thái phòng, trả phòng/vào ở, doanh thu, dropdown chi nhánh) vẫn dùng data giả từ phiên 1, dù
backend `GET /api/dashboard` đã có sẵn và hoạt động cho 3 phần đó.

### 1.1. Backend
- `DashboardService.getDashboard` đổi signature thành nhận thêm `fromYear/fromMonth/toYear/toMonth`
  — 2 biểu đồ trả phòng/vào ở và doanh thu trước đây cố định 12 tháng gần nhất, giờ nhận khoảng
  tháng tuỳ chọn từ client (mặc định 6 tháng gần nhất khi không truyền), validate `from <= to`.
- Thêm 3 query/DTO mới cho 3 bảng của phiên 6:
  - **Occupants by branch**: `COUNT(DISTINCT tenant_id)` trên toàn bộ `contract_tenant` của hợp đồng
    active (ban đầu chỉ đếm `is_representative=1`, phải sửa lại — xem mục 3.2).
  - **Missing invoice rooms**: tính gap tháng thiếu hóa đơn **trong Java** (không dùng recursive CTE
    SQL) — an toàn hơn để review bằng mắt khi không compile được. Cửa sổ nhìn lại 6 tháng gần nhất,
    không audit toàn bộ lịch sử hợp đồng.
  - **Unpaid invoice rooms**: ban đầu gộp `SUM()` theo phòng, sau đó đổi lại thành **1 dòng/1 hóa
    đơn** kèm `billYear`/`billMonth` theo yêu cầu người dùng muốn biết hóa đơn nợ thuộc tháng nào
    (xem mục 4).
- Cache Redis (`@Cacheable`) của `getDashboard` mở rộng key để bao gồm cả khoảng tháng.

### 1.2. Frontend
- `core/models/dashboard.model.ts` + `core/services/dashboard.service.ts` mới, theo đúng convention
  `ApiService` dùng chung.
- `dashboard-page.ts` xoá sạch code sinh data giả (`pseudoRandom`, `moveInsFor`, mảng branch cứng...),
  chuyển toàn bộ chart/bảng sang `computed()` từ 1 signal response duy nhất; dropdown chi nhánh dùng
  lại `RoomService.branchOptions()` có sẵn (chỉ gọi khi `isSuperAdmin()`).

---

## 2. Phát hiện + fix bug Redis cache — 2 lớp, phải đào sâu mới ra

### 2.1. Lớp 1: `LinkedHashMap cannot be cast to DashboardResponse`

Khi backend live thật chạy lần đầu (JDK 21, ngoài môi trường agent), Dashboard báo lỗi hệ thống
ngay khi load. Nguyên nhân: `RedisConfig` dùng `new GenericJacksonJsonRedisSerializer(objectMapper)`
trần — cách này **không nhúng type info (`@class`)** vào JSON lưu Redis, nên đọc lại chỉ ra được
`LinkedHashMap` thay vì kiểu thật, `@Cacheable` cast lỗi. Đây là bug có sẵn từ trước, ảnh hưởng MỌI
method `@Cacheable` trong app (không riêng Dashboard) — chỉ là lần đầu bị exercise thật.

Fix: `GenericJacksonJsonRedisSerializer.builder(objectMapper::rebuild).enableUnsafeDefaultTyping().build()`
thay vì constructor trần. Xác nhận đúng API bằng cách `javap` giải nén trực tiếp
`spring-data-redis-4.1.0.jar` (Jackson 3 mới, API khác Jackson 2 nên không đoán mò được).

### 2.2. Lớp 2: `Unexpected token START_OBJECT, expected VALUE_STRING... type id`

Sau khi fix lớp 1 và deploy, màn Phòng trọ / Chi tiết phòng lại báo lỗi hệ thống mới — khác lỗi cũ.
Root-cause bằng cách viết chương trình Java độc lập dùng đúng class thật, chạy trên byte thật lấy từ
Redis (dump qua kết nối TCP thô tới `localhost:6379` vì máy không có `redis-cli`):

- Jackson ghi (serialize) một `List<T>` ở **cấp gốc** của giá trị cache thì **không** gắn type-id
  cho chính List đó (chỉ gắn cho từng phần tử bên trong).
- Nhưng khi đọc lại (deserialize) về kiểu `Object` (Spring Cache luôn làm vậy), Jackson lại **luôn
  đòi hỏi** phải có type-id ở gốc.
- → Bất đối xứng ghi/đọc, chỉ xảy ra với method `@Cacheable` trả về `List<T>` trực tiếp
  (`RoomTypeService.listAll`, `BranchService.getRoomTypeSummaries`) — không xảy ra với method trả về
  1 record đơn (`DashboardService.getDashboard`).

Fix: bọc mọi giá trị `List` vào 1 record wrapper nội bộ (`ListEnvelope`) trước khi giao cho Jackson
ghi, bóc ra lại sau khi đọc — nhờ vậy giá trị gốc luôn là 1 record cụ thể, né hoàn toàn bất đối xứng
trên. Đã test round-trip thật (ghi rồi đọc lại) bằng chương trình Java độc lập trước khi áp dụng vào
code chính — thành công cho cả List lẫn record đơn.

Sau cả 2 fix, đã xoá key Redis cũ `roomTypes::1` (ghi bằng format lỗi trước fix) để không lặp lại lỗi
sau khi backend restart — TTL của `roomTypes`/`branches` là 6 giờ nên nếu không xoá sẽ còn lỗi tiếp
cho tới khi hết hạn.

---

## 3. Bug tìm được khi test trực tiếp bằng trình duyệt (không chỉ review code)

Người dùng báo 2 lỗi UI sau khi backend live chạy thật. Thay vì đoán từ code, đã tự chạy `ng serve` +
Playwright (không có `chromium-cli` trong môi trường này nên cài `playwright` qua npm vào thư mục
scratch) để quan sát trực tiếp, cộng với query thẳng vào DB dev qua `mysql` CLI để xác minh số liệu.

### 3.1. Biểu đồ "Phòng trống và phòng đang cho thuê" không hiện gì

Lỗi ở template: khi chuyển `roomStatusData` từ object tĩnh (mock) sang `computed()` signal, quên sửa
binding `[data]="roomStatusData"` thành `[data]="roomStatusData()"` trong `dashboard-page.html` (2
chart kia đã đúng sẵn từ phiên trước vì vốn đã là computed). Thiếu `()` khiến PrimeNG chart nhận
chính cái signal-function thay vì giá trị thật, Chart.js không dựng nổi trục — khác hẳn biểu quan
"Doanh thu" (trục 0-1 vẫn hiện đúng vì đó là data thật nhưng bằng 0, do DB dev chưa có thanh toán
nào). **Bài học: sau khi chuyển 1 property sang `computed()`, phải grep lại template tìm mọi chỗ gọi
nó** — `ng build` không bắt được lỗi "truyền signal thay vì giá trị" vào `@Input() data: any` lỏng
kiểu của PrimeNG.

### 3.2. Bảng "Số người đang ở theo chi nhánh" hiện số phòng chứ không phải số người thật

Query gốc chỉ đếm `is_representative=1` (người đại diện ký hợp đồng). Kiểm tra thẳng DB thấy nhiều
hợp đồng có 2-5 người ở chung (vd hợp đồng #1 có 4 người, #10 có 5 người) nhưng chỉ 1 người được
tính — nên số hiện ra (35+50=85) trùng khớp chính xác với tổng số phòng đang thuê, một sự trùng hợp
lộ ra bug. Quy tắc dedup ở phiên 6 ("1 người đại diện ký nhiều hợp đồng chỉ tính 1 lần") chỉ nhằm
tránh đếm trùng người đại diện qua nhiều hợp đồng, không có ý loại bỏ người ở chung không phải đại
diện. Fix: bỏ điều kiện `is_representative=1`, đếm tất cả tenant trên hợp đồng active (vẫn dedup qua
`COUNT(DISTINCT tenant_id)`). Số đúng theo DB hiện tại: 85 và 115 (xác minh bằng SQL trực tiếp).

---

## 4. Bảng "Phòng chưa thanh toán đủ hóa đơn": thêm cột tháng/năm

Theo yêu cầu người dùng, đổi từ gộp `SUM(...)` theo phòng (1 dòng/phòng, không rõ nợ tháng nào) sang
**1 dòng/1 hóa đơn chưa thanh toán đủ**, có cột tháng/năm — nếu 1 phòng nợ 2 tháng thì hiện 2 dòng,
giống cách bảng "Phòng chưa có hóa đơn" đã làm từ phiên 6.

---

## 5. Fix UI: overlay của PrimeNG bị dialog che mất

Người dùng báo select chọn danh mục chi phí phát sinh (trong dialog xem chi tiết hóa đơn, mở từ dialog
kết quả tạo hàng loạt) bị dialog che, không bấm chọn được. Rà lại toàn bộ `p-dialog` trong app (dùng
Explore agent quét hết `src/app`) và thêm `appendTo="body"` cho:

- `monthly-bills-page.html`: select danh mục chi phí + datepicker ngày thanh toán (cùng dialog chi
  tiết hóa đơn).
- `room-detail-page.html`: datepicker ngày sinh (trong dialog tạo nhanh người thuê).

Quy ước mới ghi vào memory: **mọi component overlay của PrimeNG (select, multiselect, datepicker...)
đặt trong dialog phải có `appendTo="body"` ngay từ đầu**, không đợi báo bug mới sửa.

---

## 6. Phát hiện phương pháp: compile-verify backend mà không cần JDK 21

Hạn chế "agent chỉ có JDK 17, backend cần JDK 21" lặp lại suốt từ phiên 4 — mọi thay đổi backend
trước giờ chỉ được "review bằng mắt", chưa từng compile thật. Phiên này tìm ra cách giải quyết:

```bash
mvn -o dependency:build-classpath -Dmdep.outputFile=cp.txt   # không cần JDK 21, không trigger compiler plugin
javac --release 17 -cp "$(cat cp.txt)" -d <outdir> @sources.txt   # sources.txt = list mọi .java file
```

`dependency:build-classpath` chỉ resolve dependency từ `~/.m2`, không invoke compiler plugin nên
không bị chặn bởi `release=21` khai báo trong `pom.xml`. Kết quả: **toàn bộ 157 file backend compile
sạch (0 lỗi)** bằng javac 17 — nghĩa là code hiện tại không dùng cú pháp riêng của JDK 21, nên cách
này đáng tin cậy để dùng lại các phiên sau. Lưu ý môi trường Git Bash: classpath phải giữ nguyên dạng
Windows (`C:\...;C:\...` do chính `mvn` xuất ra), không được chạy qua `cygpath` (làm hỏng chuỗi đã có
dấu `;`), cũng không dùng path kiểu POSIX trực tiếp (javac báo "package does not exist" cho mọi
import — trông như lỗi thật nhưng thực ra là lỗi parse classpath).

Kỹthuật này còn được dùng để **root-cause chính xác 2 bug Redis** ở mục 2 — viết chương trình Java
nhỏ dùng đúng class thật (`GenericJacksonJsonRedisSerializer`) chạy trên byte thật lấy từ Redis, thay
vì đoán từ đọc code hoặc từ thông báo lỗi suông.

---

## 7. Việc còn lại / lưu ý cho phiên sau

- [ ] **Cần restart backend (JDK 21)** để áp dụng toàn bộ fix ở phiên này — 2 fix Redis (mục 2),
  fix occupants-by-branch (mục 3.2), đổi shape unpaid-invoice-rooms (mục 4). Tất cả mới chỉ
  compile-verify bằng javac 17, chưa chạy live được.
- [ ] Sau khi restart, nếu vẫn thấy lỗi cast Redis cũ xuất hiện **một lần** — đó là cache stale còn
  sót (đã xoá `roomTypes::1` nhưng `branches`/`dashboard` cache có thể còn entry cũ khác nếu đã bị
  hit trước khi fix); đợi hết TTL hoặc xoá thủ công, không phải fix chưa hoạt động.
- [ ] Chưa chạy `mvn clean test` thật (chỉ compile-verify, chưa chạy test suite/Spring context thật).
- [ ] Dashboard chart doanh thu/lượt ra vào vẫn chưa có data thật để xem trực quan (DB dev chưa có
  payment nào) — cần tạo dữ liệu thanh toán thật qua UI nếu muốn xác minh 100% các cột liên quan
  payment status trên toàn app (tồn đọng từ phiên 6).

---

*File này bổ sung cho `tong-hop-phien-6-dashboard-mockup-va-util.md` và các file trước đó — đọc từ
file mới nhất lùi dần nếu cần đầy đủ ngữ cảnh.*

# TỔNG HỢP PHIÊN LÀM VIỆC: TRIỂN KHAI FRONTEND ANGULAR
*(File này gộp toàn bộ nội dung đã làm trong phiên triển khai frontend — dùng để tra cứu nhanh hoặc làm ngữ cảnh cho các phiên tiếp theo, không cần đọc lại toàn bộ lịch sử chat. Đọc kèm `tong-hop-du-an-quan-ly-phong-tro.md` và `tong-hop-trien-khai-backend.md` để có bối cảnh spec và API đầy đủ.)*

---

## 0. Phạm vi phiên này

Dựng khung Angular + PrimeNG từ đầu (thư mục trống) → cấu hình theme/i18n → xây hạ tầng core (auth, refresh token tự động, gọi API + loading dùng chung, confirm dialog dùng chung, toast thông báo dùng chung) → xây 5 màn hình quản lý dạng **List** (chưa làm Detail/Create/Edit theo yêu cầu) → thêm tài khoản đăng nhập vào DB local.

Vị trí project: `D:\Project\rental-room-management-system\rental-room-management-system-frontend`

---

## 1. Môi trường & khung project

- **Node.js trên máy ban đầu là v18.20.4 (EOL)** — không đủ điều kiện cho PrimeNG v21 (cần Angular 21 → cần Node `^20.19.0 || ^22.12.0 || >=24.0.0`). Đã nâng cấp qua `winget install --id OpenJS.NodeJS.LTS` → Node v24.18.0.
- **Stack**: Angular 21.2 (standalone components, Signals, không dùng NgModule), PrimeNG 21 + `@primeuix/themes` (preset Aura, primary color chỉnh về **blue** qua `definePreset` tại `src/app/core/theme/app-preset.ts`), `@angular/animations` + `provideAnimationsAsync` (PrimeNG vẫn cần dù Angular 21 đã deprecate), `chart.js` (cho `primeng/chart`), `@ngx-translate/core` + `@ngx-translate/http-loader` v18.
- **i18n**: file dịch tại `public/i18n/{vi,en}.json`, mặc định tiếng Việt, lưu lựa chọn ngôn ngữ vào `localStorage` (key `rrms_lang`), có nút chuyển VI/EN trên topbar và trang login.
- **ngx-translate v18 là bản viết lại phá vỡ tương thích** — không còn export `TranslateModule`, phải import trực tiếp `TranslatePipe` vào mảng `imports` của từng standalone component. Root wiring: `provideTranslateService({ lang, fallbackLang })` + `provideTranslateHttpLoader({ prefix, suffix })` là 2 entry riêng trong `providers` của `app.config.ts`.
- **Lỗi field-initializer của Angular 21**: `readonly foo = this.someInjectedThing.bar;` sẽ lỗi `TS2729` nếu `someInjectedThing` được inject qua constructor (thứ tự emit của TS chạy field initializer trước khi gán constructor param). Cách khắc phục dùng xuyên suốt dự án: khai báo field bằng `private readonly someInjectedThing = inject(Thing);` thay vì constructor injection, khi cần field khác đọc ngay giá trị đó.

---

## 2. Hạ tầng core đã xây dựng

### Auth & refresh token tự động
- `AuthService`: login/logout/refresh, giải mã JWT để lấy `CurrentUser`. **Lưu ý quan trọng**: `userId` nằm ở claim chuẩn `sub` (không phải claim tùy biến `userId`) — đã từng đọc sai `payload.userId` (luôn `undefined`), đã sửa thành `Number(payload.sub)`.
- **Tự động refresh khi gặp 401** (`token-refresh.service.ts` + `core/interceptors/error.interceptor.ts`): khi có request bất kỳ (trừ các endpoint auth) trả 401, tự gọi `POST /auth/refresh`, gắn token mới rồi **retry lại request gốc trong âm thầm** — người dùng không bị văng ra ngoài. Nhiều request 401 cùng lúc chỉ gộp thành **1 lần gọi refresh** (dùng `shareReplay(1)`). Nếu refresh cũng thất bại → xóa session, chuyển về `/login`.
- Đã kiểm tra bằng Playwright mock: request đầu tiên 401 → tự refresh → retry thành công, không cần người dùng làm gì.

### Gọi API + loading dùng chung (theo yêu cầu người dùng)
- `LoadingService`: đếm số request đang chạy (không phải boolean đơn giản, để tránh N request chạy song song tắt loading sớm khi chỉ 1 cái xong), expose `isLoading` (signal).
- `ApiService`: **điểm gọi HTTP duy nhất** trong toàn app — có `get/post/put/patch/delete`, tự động unwrap `ApiResponse<T>` về `T`, tự bật/tắt `LoadingService` — không nơi nào phải gọi `loading.set(true/false)` thủ công.
- Toàn bộ service nghiệp vụ (account, tenant, branch, room, room-type, debt-record, auth) đều đi qua `ApiService`.
- Thanh loading toàn cục (progress bar mảnh, xanh, cố định đầu trang) hiển thị tự động mỗi khi có API đang chạy — mount 1 lần ở component gốc `App`, cộng thêm spinner có sẵn của từng bảng `p-table` (cùng bind vào 1 signal chia sẻ).

### Confirm dialog dùng chung (theo yêu cầu người dùng)
- Dùng `ConfirmationService` có sẵn của PrimeNG (đúng pattern message + accept/reject) thay vì tự viết lại.
- `ConfirmService`: wrapper đơn giản `confirm(message, onAccept, onReject?)`, tự điền tiêu đề/nút theo ngôn ngữ hiện tại.
- Chỉ mount `<p-confirmDialog>` **một lần** ở component gốc `App`.
- Áp dụng thực tế vào nút "Đăng xuất" ở topbar (hỏi xác nhận trước khi logout) — đây là hành động thật duy nhất phù hợp để minh họa lúc này (chưa có màn hình xóa/sửa nào khác).

### Toast thông báo lỗi/thành công dùng chung (theo yêu cầu người dùng)
- `NotificationService`: wrapper quanh `MessageService` của PrimeNG, có `success(message)`/`error(message)`, dùng chung 1 `<p-toast>` mount 1 lần ở gốc.
- **Logic hiện lỗi tập trung hoàn toàn trong `error.interceptor.ts`**, áp dụng cho mọi API call:
  - Có `message` trong response lỗi (đúng theo format `ApiResponse.error(message)` của backend) → hiện chính xác message đó.
  - Không có/không đọc được (network error, response không phải JSON...) → hiện **"Lỗi hệ thống! Vui lòng liên hệ quản trị viên."**
- Toast thành công **không** tự động bắn cho mọi API mutating (vì login/refresh trả message chung "OK", toast sẽ vô nghĩa) — chỉ gọi tường minh ở nơi có ý nghĩa thật, ví dụ sau khi "Thu công nợ" thành công.
- **Lỗi circular dependency đã gặp và sửa**: file dịch (`/i18n/*.json`) cũng được tải qua `HttpClient` nên đi qua interceptor; nếu interceptor cố inject `TranslateService` ngay trong lúc `TranslateService` đang được khởi tạo (chính là lúc tải file dịch) sẽ bị lỗi `NG0200: Circular dependency`. Đã sửa bằng cách bỏ qua toàn bộ logic interceptor (return `next(req)` ngay) cho riêng request có URL chứa `/i18n/`, trước khi gọi bất kỳ `inject()` nào.
- Mỗi `load()`/subscribe ở các trang list đều có thêm `error: () => {}` (no-op) — không phải để lặp lại logic hiển thị (vẫn tập trung ở interceptor), chỉ để tránh RxJS log lỗi console khi không ai đọc lỗi nữa.

---

## 3. Cấu trúc & màn hình đã xây dựng

- **Layout**: `app-topbar` (brand, chuyển ngôn ngữ, menu user + đăng xuất có confirm), `app-sidebar` (menu lọc theo role), `app-layout` (khung tổng).
- **RBAC**: `authGuard` (yêu cầu đăng nhập), `roleGuard(...roles)` (factory theo role) — đã test `ADMIN_CAP_1` bị chặn khỏi `/accounts`, tự chuyển về `/dashboard`.
- **Trang login**: form thật gọi `/api/auth/login`, có chuyển ngôn ngữ, không còn thông báo lỗi riêng (đã chuyển sang dùng toast dùng chung).
- **Dashboard**: vẫn dùng dữ liệu mẫu cho 3 biểu đồ (`primeng/chart`), **chưa** nối vào `GET /api/dashboard` thật.
- **5 màn hình List đã có dữ liệu thật** (đọc đúng theo response backend, có phân trang/lọc/tìm kiếm server-side qua `p-table` lazy load):
  - **Tài khoản**: lọc vai trò, trạng thái, tìm theo tên/username.
  - **Người thuê**: tìm theo tên/CCCD.
  - **Chi nhánh**: tìm theo tên/địa chỉ (ADMIN_CAP_1 tự lọc phía backend, không cần lọc phía client).
  - **Phòng trọ**: chọn chi nhánh (mặc định chi nhánh đầu tiên), lọc trạng thái/loại phòng, tìm theo mã phòng.
  - **Công nợ**: lọc trạng thái, có dialog "Thu tiền" cho khoản chưa thu (kèm toast thành công).
- **Chưa làm màn hình Detail/Create/Edit cho bất kỳ entity nào** — theo yêu cầu rõ ràng của người dùng là làm List trước.
- CSS dùng chung `.list-page`/`.list-page__filters` trong `styles.scss` cho cả 5 màn hình list (thay vì mỗi trang 1 file style riêng, vì cấu trúc giống hệt nhau).

---

## 4. Xác thực API — đọc trực tiếp từ source code backend, không đoán

Toàn bộ model/service frontend (`AccountResponse`, `TenantResponse`, `BranchResponse`, `RoomResponse`, `DebtRecordResponse`, `PageResponse`, `ApiResponse`...) được đối chiếu trực tiếp với các file `dto/response/*.java` và `common/{ApiResponse,PageResponse}.java` thật của backend, không đoán field. Một số điểm dễ nhầm:
- `PageResponse<T>` có thêm field `last: boolean` (dễ bỏ sót).
- `ApiResponse<T>` có thêm `timestamp`.
- Endpoint `/api/room-types/all` trả `List<RoomTypeResponse>` (không phân trang) — dùng cho dropdown lọc loại phòng.
- Query param phân trang theo chuẩn Spring `Pageable`: `page`, `size`, `sort=field,asc|desc`.

---

## 5. Kiểm thử

- **Không có Redis trên máy** (chỉ có MySQL80 đang chạy) — `JwtAuthenticationFilter` của backend kiểm tra blacklist JWT qua Redis trên mọi request đã xác thực, nên **chưa test được end-to-end với backend thật đang chạy**. Toàn bộ kiểm thử trình duyệt trong phiên này dùng Playwright với `page.route()` mock đúng theo response shape thật của backend (không phải theo request tự đoán).
- Đã build + chạy `ng serve` + chụp màn hình/kiểm tra console error qua Playwright cho: khung app, đăng nhập, 5 màn hình list, refresh token tự động, loading bar, confirm dialog, toast lỗi (có message/không có message)/thành công.

---

## 6. Việc khác đã làm

- Đã thêm 1 tài khoản đăng nhập trực tiếp vào MySQL local (database `rental_room_management`, bảng `account` đang trống hoàn toàn trước đó — đây là lý do không đăng nhập được):
  - username: `admin`, password: `admin`, role: `ADMIN_TONG`, is_active = 1.
  - Mật khẩu hash bằng BCrypt thật (không phải plain text), tạo qua thư viện `bcryptjs` tạm thời, khớp với `BCryptPasswordEncoder` mà backend đang dùng.

---

## 7. Việc còn lại (chưa làm)

- [ ] Màn hình Detail/Create/Edit cho Account, Tenant, Branch, Room + toàn bộ luồng Contract/Billing/Checkout — chưa có gì, theo yêu cầu người dùng ưu tiên List trước. Khi làm, dùng `ConfirmService` cho xác nhận xóa, `NotificationService.success(...)` cho thông báo lưu/xóa thành công — không tạo dialog/toast riêng cho từng màn hình.
- [ ] Dashboard vẫn dùng dữ liệu mẫu, chưa nối `GET /api/dashboard` thật.
- [ ] Cài Redis trên máy để có thể test end-to-end với backend thật đang chạy (hiện chỉ test qua API giả lập đúng cấu trúc thật).
- [ ] Quản lý Loại phòng (RoomType) — kể cả API create/update/delete phía backend lẫn màn hình phía frontend — chưa làm.

---

*File này bổ sung cho `tong-hop-du-an-quan-ly-phong-tro.md` và `tong-hop-trien-khai-backend.md` — đọc cả 3 file để có ngữ cảnh đầy đủ trước khi tiếp tục phát triển.*

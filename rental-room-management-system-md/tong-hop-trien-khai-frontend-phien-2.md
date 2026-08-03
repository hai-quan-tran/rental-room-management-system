# TỔNG HỢP PHIÊN LÀM VIỆC 2: SỬA LỖI AUTH + MÀN HÌNH DETAIL/CREATE/EDIT
*(File này gộp toàn bộ nội dung đã làm trong phiên làm việc thứ 2 — nối tiếp `tong-hop-trien-khai-frontend.md`. Đọc kèm `tong-hop-du-an-quan-ly-phong-tro.md` và `tong-hop-trien-khai-backend.md` để có bối cảnh spec và API đầy đủ.)*

---

## 0. Phạm vi phiên này

Phiên trước đã xây xong khung frontend + 5 màn hình **List**. Phiên này:
1. Chẩn đoán và sửa 2 lỗi auth nghiêm trọng (403 khi gọi API, phải đăng nhập lại mỗi 30 phút).
2. Xây **Detail/Create/Edit** cho 5 entity: Account, Tenant, Branch, RoomType, và **Room** (màn hình lớn nhất, gồm toàn bộ luồng Hợp đồng/Hóa đơn/Trả phòng).
3. Điều chỉnh RBAC theo yêu cầu mới: Chi nhánh chỉ ADMIN_TONG được xem/sửa; nút Hủy/Đóng và nút phản hồi trong confirm dialog đổi sang màu đỏ.

---

## 1. Lỗi Auth đã sửa (backend)

### 1.1. CORS preflight bị chặn 403
`SecurityConfig.java` trước đó chỉ `permitAll()` cho `/api/auth/**`, còn lại `anyRequest().authenticated()`. Vì frontend (`:4200`) và backend (`:8080`) khác origin, browser gửi **CORS preflight OPTIONS** trước mọi request có header `Authorization` — preflight này không mang token nên bị chặn 403, khiến mọi API sau khi đăng nhập đều lỗi (trừ chính API login vì nó nằm trong path permitAll).

**Fix**: thêm `.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()` trước rule `anyRequest().authenticated()`.

### 1.2. Access token hết hạn trả 401 → 403, phá vỡ auto-refresh
Do không cấu hình `AuthenticationEntryPoint` tùy chỉnh, Spring Security dùng mặc định `Http403ForbiddenEntryPoint` cho **mọi** request chưa xác thực hợp lệ (token thiếu/sai/hết hạn/bị blacklist) → luôn trả **403 rỗng**, không phân biệt được với lỗi phân quyền `@PreAuthorize` thật. Frontend (`error.interceptor.ts`) chỉ tự động gọi `/auth/refresh` khi gặp **401**, nên access token hết hạn (mặc định 30 phút) buộc người dùng phải đăng nhập lại thủ công.

**Fix**: thêm `RestAuthenticationEntryPoint` (`security/RestAuthenticationEntryPoint.java`, viết JSON tay bằng text block — không cần `ObjectMapper` vì chạy trước tầng Spring MVC) trả đúng **401** theo format `ApiResponse` chuẩn, wire vào `SecurityConfig` qua `.exceptionHandling(ex -> ex.authenticationEntryPoint(...))`. Giờ token hết hạn → 401 → frontend tự refresh âm thầm, không còn phải đăng nhập lại mỗi 30 phút. Lỗi `@PreAuthorize` thật (đủ xác thực nhưng sai role) vẫn trả 403 qua `GlobalExceptionHandler` như cũ.

**Cách phân biệt 2 loại lỗi khi debug**: response 403 có JSON body (`{"message": "Bạn không có quyền..."}`) → lỗi role thật, đi qua `GlobalExceptionHandler`. Response rỗng (Content-Length: 0) → lỗi tầng filter (trước fix) hoặc giờ sẽ luôn là 401 thay vì 403.

---

## 2. Thay đổi RBAC theo yêu cầu người dùng

### 2.1. Chi nhánh: chỉ ADMIN_TONG được xem/sửa
- `BranchController`: class-level `@PreAuthorize` đổi từ `hasAnyRole('ADMIN_TONG','ADMIN_CAP_1')` sang `hasRole('ADMIN_TONG')`; bỏ override thừa ở `create()`.
- `BranchService`: dọn sạch logic branch-scoping cho ADMIN_CAP_1 trong `list()`/`get()`/`update()` (giờ là code chết vì ADMIN_CAP_1 không còn gọi được các API này).
- Frontend: sidebar ẩn "Chi nhánh" với ADMIN_CAP_1; route `/branches`, `/branches/:id` gắn `roleGuard(Role.ADMIN_TONG)`.
- **Hệ quả cần sửa kèm**: `RoomsPage` trước đó dùng `BranchService.listOptions()` (`GET /api/branches`) để đổ dropdown chọn chi nhánh — bị 403 với ADMIN_CAP_1 sau thay đổi trên. Đã thêm endpoint riêng nhẹ hơn `GET /api/rooms/branch-options` (cả 2 role, lọc theo `branchIds` trong JWT với ADMIN_CAP_1) để tách bạch "xem quản lý chi nhánh" (ADMIN_TONG only) khỏi "chọn chi nhánh để thao tác phòng" (cả 2 role).

### 2.2. Room: tách quyền tạo/sửa
- **Tạo phòng**: chỉ ADMIN_TONG (`RoomController.create()` thêm `@PreAuthorize("hasRole('ADMIN_TONG')")` override).
- **Sửa phòng**: cả 2 role sửa được `roomCode`/`monthlyRent`; riêng đổi `roomTypeId` chỉ ADMIN_TONG (`RoomService.update()` thêm field-diff guard, ném `BusinessException(ACCESS_DENIED)` nếu ADMIN_CAP_1 cố đổi loại phòng). **Chi nhánh của phòng không bao giờ đổi được, kể cả ADMIN_TONG** — xác nhận rõ với người dùng, không thêm field này vào `RoomRequest`.
- Frontend: nút "Thêm mới" ở Rooms List chỉ hiện với ADMIN_TONG; route `/rooms/new` gắn `roleGuard(Role.ADMIN_TONG)` riêng, `/rooms/:id` vẫn cho cả 2 role nhưng dropdown loại phòng trong form `[disabled]` với ADMIN_CAP_1 (defense in depth, enforcement thật ở backend).

### 2.3. Nút Hủy/Đóng và confirm dialog → màu đỏ
- Toàn bộ nút "Hủy" ở các form Detail (Account, Tenant, Branch, RoomType, Room) và dialog (Debt collect) thêm `severity="danger"`.
- `ConfirmService.confirm()` thêm `rejectButtonProps: { severity: 'danger' }` — áp dụng cho mọi nơi dùng chung `<p-confirmDialog>` (logout, xóa tenant/loại phòng/phòng...).

---

## 3. Màn hình Detail/Create/Edit đã xây (4 màn đơn giản)

Tất cả theo cùng 1 pattern: route `/{entity}/new` và `/{entity}/:id` dùng chung 1 component (kiểm tra `id === 'new'` để biết create hay edit), form bằng `FormsModule` + Angular Signals (không dùng ReactiveFormsModule, nhất quán với style cũ), validate thủ công đơn giản (`submitted` signal + `@if` hiện lỗi), style dùng chung `.detail-page`/`.field`/`.field-error` (thêm mới vào `styles.scss`), click-to-detail trên các dòng List (`.clickable-row`).

- **Account** (`features/accounts/account-detail/`): form tạo/sửa + dialog đổi mật khẩu + toggle kích hoạt/ngừng hoạt động (có confirm).
- **Tenant** (`features/tenants/tenant-detail/`): form tạo/sửa + bảng lịch sử thuê phòng (`GET /tenants/{id}/rental-history`) + xóa (có confirm).
- **Branch** (`features/branches/branch-detail/`): form tạo/sửa + dropdown chọn quản lý (từ tài khoản ADMIN_CAP_1) + bảng tổng hợp phòng theo loại (read-only, từ `BranchDetailResponse`).
- **RoomType** (`features/room-types/`): **màn hình hoàn toàn mới kể cả List** (trước đó chưa có gì) — List + Detail/Create/Edit + editor thêm/xóa vật dụng bàn giao (replace-all khi lưu) + xóa loại phòng. Thêm nav "Loại phòng" (ADMIN_TONG only).

---

## 4. Room Detail — màn hình lớn nhất (Contract + Billing + Checkout)

### 4.1. Bổ sung backend (3 việc nhỏ)
1. `GET /api/rooms/branch-options` — xem mục 2.1.
2. `GET /api/contracts/{id}/checkout` — đọc lại checklist trả phòng đã lưu của hợp đồng cũ (spec 4.4.3 yêu cầu xem lại được, nhưng trước đó `CheckoutController` chỉ có `POST`). Tái dùng `CheckoutResponse` có sẵn + `CheckoutChecklistRepository.findByContractId` có sẵn; thêm `DebtRecordRepository.findByChecklistId` mới.
3. Tách quyền tạo/sửa Room — xem mục 2.2.

### 4.2. Frontend: models/services mới
`contract.model/service.ts`, `billing.model/service.ts`, `checkout.model/service.ts`, `extra-fee-category.model/service.ts` + mở rộng `room.model/service.ts` (thêm `RoomRequest`, `RoomDetailResponse`, `BranchOption`, `get/create/update/delete/branchOptions`). Enum mới: `payment-status.enum.ts`, `checklist-item-status.enum.ts`, `contract-status.enum.ts`.

### 4.3. `room-detail-page` — 4 tab (dùng `primeng/tabs`, lần đầu dùng trong dự án, API composable `<p-tabs>/<p-tablist>/<p-tab>/<p-tabpanels>/<p-tabpanel>`)
1. **Thông tin cơ bản**: mã phòng, loại phòng (disable với ADMIN_CAP_1), giá thuê, trạng thái (tag read-only), vật dụng bàn giao (read-only, lấy kèm theo `RoomDetailResponse`). Lưu/Xóa.
2. **Hợp đồng & Người thuê**: nếu có hợp đồng ACTIVE → hiển thị info + bảng người thuê (đại diện lên đầu + tag, xóa người thuê) + thêm người thuê (autocomplete tra CCCD qua `TenantService.list` hoặc "+ Tạo mới" mở dialog tạo Tenant ngay tại chỗ). Nếu chưa có hợp đồng → form tạo hợp đồng mới (ngày bắt đầu, cọc, giá thuê tùy chỉnh, chọn người thuê + đặt đại diện). Bên dưới: lịch sử hợp đồng cũ (ENDED) + nút "Xem checklist" mở dialog đọc lại từ endpoint mới ở mục 4.1.
3. **Hóa đơn & Công nợ** (disable nếu chưa có hợp đồng active): tạo hóa đơn tháng, danh sách hóa đơn (click chọn), chi tiết hóa đơn: chi phí phát sinh (thêm/xóa) + thanh toán (thêm, phương thức tự do).
4. **Trả phòng** (disable nếu chưa có hợp đồng active): checklist theo từng vật dụng bàn giao (tình trạng Còn nguyên/Hư hỏng/Mất + tiền trừ tự động reset về 0 khi chọn lại Còn nguyên), ngày trả phòng, ghi chú. Nút xác nhận có confirm dialog (hành động không thể hoàn tác) → gọi API → hiện kết quả (tiền cọc hoàn lại + công nợ phát sinh nếu vượt cọc) trong cùng dialog checklist ở tab 2.

**Lưu ý quan trọng khi sửa code liên quan sau này**: `ContractService.addTenant` với `representative: true` khi hợp đồng đã có đại diện sẽ ném lỗi (không có API "đổi đại diện" riêng) — do đó UI "Thêm người thuê" vào hợp đồng đang hiệu lực luôn gửi `representative: false`, không có checkbox chọn đại diện ở luồng này.

---

## 5. Kiểm thử phiên này

- Không có khả năng build/chạy backend từ môi trường agent (máy chỉ có JDK 17, backend cần JDK 21) — mọi thay đổi backend chỉ được review lại bằng mắt, **chưa compile thật**. Cần build lại bằng JDK 21 và restart trước khi dùng.
- Frontend: `ng build` sạch sau mỗi bước. Dùng Playwright (cài tạm qua `npm install playwright` trong thư mục scratchpad, browser Chromium đã có sẵn cache ở máy) điều khiển trình duyệt thật:
  - Với 4 màn Account/Tenant/Branch/RoomType: **có lúc backend thật đang chạy** → test được full round-trip thật (tạo tài khoản ADMIN_CAP_1 thật, xác nhận bị chặn đúng ở cả frontend lẫn backend, dọn dẹp bằng cách deactivate).
  - Với Room Detail (phần cuối phiên): backend đã tắt lúc test → chỉ verify được bằng session giả lập (JWT tự ký, không cần chữ ký hợp lệ vì frontend chỉ decode payload không verify signature) để xác nhận template render đúng, chuyển tab được, thêm/xóa dòng checklist hoạt động, không có lỗi console/runtime ngoài lỗi mạng do backend tắt.

---

## 6. Việc còn lại (chưa làm / chưa test được)

- [ ] **Rebuild + restart backend bằng JDK 21** — bắt buộc trước khi dùng, để áp dụng 2 fix auth (mục 1) + 3 thay đổi RBAC/endpoint (mục 2, 4.1).
- [ ] Test round-trip thật cho toàn bộ luồng Room Detail (tạo phòng → hợp đồng → hóa đơn/chi phí/thanh toán → trả phòng → xem lại checklist lịch sử) — chưa test được vì backend tắt lúc cuối phiên.
- [ ] Dashboard vẫn dùng dữ liệu mẫu, chưa nối `GET /api/dashboard` thật (từ phiên trước, vẫn còn treo).
- [ ] Cài Redis thật trên máy dev nếu muốn test hoàn toàn không cần workaround (hiện tại Redis đã được xác nhận chạy, nhưng cần kiểm tra lại mỗi lần môi trường thay đổi).

---

*File này bổ sung cho `tong-hop-trien-khai-frontend.md`, `tong-hop-trien-khai-backend.md`, `tong-hop-du-an-quan-ly-phong-tro.md` — đọc cả 4 file để có ngữ cảnh đầy đủ trước khi tiếp tục phát triển.*

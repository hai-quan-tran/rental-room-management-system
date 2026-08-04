# TỔNG HỢP PHIÊN LÀM VIỆC 4: PHÍ WIFI/GỬI XE + QUẢN LÝ VẬT DỤNG (ITEM) + CHECKLIST TRẢ PHÒNG CHIA SỐ LƯỢNG
*(File này gộp toàn bộ nội dung đã làm trong phiên làm việc thứ 4 — nối tiếp `tong-hop-trien-khai-frontend-phien-3.md`. Khác các phiên trước, phiên này sửa CẢ backend lẫn frontend cho cùng 1 nhóm tính năng. Đọc kèm `tong-hop-du-an-quan-ly-phong-tro.md`, `tong-hop-trien-khai-backend.md`, và 3 file frontend trước để có bối cảnh đầy đủ.)*

---

## 0. Phạm vi phiên này

Người dùng yêu cầu 3 tính năng liên quan đến nhau:
1. Phòng có thêm phí wifi + phí gửi xe cố định hàng tháng, cộng vào hóa đơn.
2. Bảng quản lý **vật dụng (Item)** theo từng chi nhánh (tên, giá, số lượng tồn kho, số lượng mặc định/phòng) — vật dụng bàn giao của loại phòng phải **chọn** từ bảng này (không nhập tay), có kiểm tra tồn kho khi cấu hình và khi tạo/sửa phòng.
3. Checklist trả phòng phải chia rõ số lượng **còn nguyên / hư hỏng / mất** cho từng vật dụng (không còn 1 trạng thái duy nhất/dòng), tiền đền bù mặc định = số lượng × đơn giá vật dụng.

Trong quá trình làm rõ yêu cầu #2, phát hiện một mâu thuẫn kiến trúc: Item thuộc về 1 chi nhánh cụ thể, nhưng **Loại phòng (RoomType) lúc đó lại dùng chung cho mọi chi nhánh** — nên không thể kiểm tra tồn kho chính xác theo từng chi nhánh nếu giữ nguyên thiết kế cũ. Đã hỏi lại người dùng và **thống nhất chuyển Loại phòng thành dữ liệu riêng của từng chi nhánh** (thay đổi kiến trúc lớn hơn dự kiến ban đầu).

---

## 1. Các quyết định đã chốt (hỏi lại người dùng trước khi code)

- **Phí wifi/gửi xe**: tính đủ 100%, **không** chia theo tỷ lệ ngày ở như tiền thuê (khác `RentCalculator`).
- **Loại phòng (RoomType)**: chuyển thành thuộc về **đúng 1 chi nhánh** (mỗi chi nhánh có danh sách loại phòng riêng, tên chỉ cần unique trong phạm vi chi nhánh). Đây là thay đổi bắt buộc để ví dụ kiểm tra tồn kho của người dùng hoạt động đúng.
- **Quyền tạo/sửa/xóa Loại phòng** (kể cả cấu hình vật dụng bàn giao): đổi từ "chỉ ADMIN_TONG" (chốt ở phiên 2) sang **cả 2 role**, mỗi người chỉ thao tác trong chi nhánh mình quản lý — nhất quán với quyền của Room/Item.
- **Khi vật dụng bị "Mất" lúc trả phòng**: **tự động trừ** vào `quantity_available` của Item đó tại chi nhánh (giảm tồn kho vĩnh viễn). Vật dụng "Hư hỏng" thì **không** trừ tồn kho (đồ vẫn còn trong phòng, chỉ hỏng).

---

## 2. Database — migration mới `V3__room_fees_items_branch_room_types_checklist_quantities.sql`

Đặt ở cả `rental-room-management-system-db/` (nguồn gốc) và copy vào `backend/src/main/resources/db/migration/` (đúng quy ước từ phiên backend).

- `room`: thêm `wifi_fee`, `parking_fee` (DECIMAL(15,0) DEFAULT 0).
- `monthly_bill`: thêm `wifi_fee`, `parking_fee` (snapshot tại thời điểm tạo hóa đơn, giống `rent_amount`), mở rộng công thức 2 generated column `total_amount`/`remaining_amount` để cộng thêm 2 cột này.
- `room_type`: thêm `branch_id NOT NULL` — **backfill bằng chi nhánh của phòng đầu tiên (MIN branch_id) đang dùng loại phòng đó**, xóa loại phòng "mồ côi" không có phòng nào dùng. Đổi `uq_room_type_name` (unique toàn hệ thống) thành `uq_room_type_branch_name` (unique theo từng chi nhánh).
  - ⚠️ **Lưu ý**: nếu trước đây có loại phòng dùng chung bởi nhiều chi nhánh khác nhau, migration này gán nó về 1 chi nhánh tùy ý (MIN) — cần kiểm tra dữ liệu hiện có trước khi chạy nếu việc này quan trọng.
- Bảng mới `item`: `branch_id`, `name`, `price`, `quantity_available`, `default_quantity_per_room`, unique `(branch_id, name)`, CHECK `default_quantity_per_room <= quantity_available`.
- `room_type_handover_item`: đổi cột `item_name` (text tự do) → `item_id` (FK tới `item`), unique `(room_type_id, item_id)`. **Xóa sạch dữ liệu cũ** của bảng này (tên tự do không thể tự map sang Item cụ thể) — cần cấu hình lại vật dụng bàn giao sau khi chạy migration.
- `checkout_checklist_item`: bỏ cột `status` (và CHECK `chk_cci_status`), thêm `total_quantity`, `damaged_quantity`, `lost_quantity` + CHECK `damaged_quantity + lost_quantity <= total_quantity`. "Còn nguyên" không lưu cột riêng, tính bằng `total - damaged - lost` khi hiển thị.

---

## 3. Backend

### 3.1. Phí wifi/gửi xe
`Room`/`RoomRequest`/`RoomResponse` thêm `wifiFee`/`parkingFee`. `MonthlyBill`/`MonthlyBillResponse`/`MonthlyBillListResponse` thêm 2 field tương ứng. `BillingService` set 2 field này **chỉ tại thời điểm tạo hóa đơn** (`createBill`, `bulkCreate`, nhánh tạo-mới trong `upsertRentForCheckoutMonth`) từ giá trị hiện tại của `contract.getRoom()` — không tính lại nếu phòng đổi phí sau đó (snapshot, giống cách `rentAmount` đã hoạt động).

### 3.2. Loại phòng (RoomType) branch-scoped
`RoomType` thêm quan hệ `branch` (bắt buộc, không đổi được sau khi tạo — giống quy tắc branch của `Room`). `RoomTypeRepository`/`RoomTypeService`/`RoomTypeController` viết lại toàn bộ theo đúng pattern của `RoomService`/`RoomController` (`SecurityUtils.assertCanAccessBranch(...)` ở đầu mọi method, route dạng `/api/branches/{branchId}/room-types`, `/api/branches/{branchId}/room-types/all`). Bỏ các `@PreAuthorize("hasRole('ADMIN_TONG')")` override trên create/update/delete/handover-items — giờ dùng chung quyền class-level (cả 2 role).

### 3.3. Vật dụng (Item) — entity/CRUD mới hoàn toàn
`Item` (branch, name, price, quantityAvailable, defaultQuantityPerRoom) + `ItemRepository` + `ItemRequest`/`ItemResponse`/`ItemOptionResponse` (bản nhẹ cho dropdown) + `ItemService` + `ItemController` — theo đúng pattern `RoomService`/`RoomController` (route `/api/branches/{branchId}/items`, cả 2 role thao tác trong chi nhánh mình).

### 3.4. Vật dụng bàn giao chọn từ Item + kiểm tra tồn kho
`RoomTypeHandoverItem.itemName` (String) → `item` (quan hệ tới `Item`). `HandoverItemRequest`/`HandoverItemResponse` đổi tương ứng (response có thêm `itemPrice` để frontend tự tính tiền đền bù mặc định). `RoomTypeService.replaceHandoverItems()` kiểm tra: vật dụng phải cùng chi nhánh với loại phòng, và `quantity <= item.quantityAvailable` (kiểm tra đơn giản 1 dòng, đúng ví dụ "tổng có 5 nhưng sửa thành 10 → báo lỗi").

### 3.5. Kiểm tra tồn kho tổng hợp khi tạo/sửa phòng
`ItemService.assertSufficientStock(branchId, roomTypeCountDeltas)` — logic mới, tái hiện đúng ví dụ người dùng đưa ra: lấy số phòng hiện có theo từng loại phòng trong chi nhánh, mô phỏng thay đổi (`+1` khi tạo phòng, `{loại cũ: -1, loại mới: +1}` khi đổi loại phòng), rồi với mỗi vật dụng, cộng dồn (số phòng loại X) × (số lượng vật dụng cấu hình cho loại X) trên toàn bộ loại phòng của chi nhánh, so với `quantityAvailable`. `RoomService.create()`/`update()` gọi hàm này (chỉ gọi khi tạo mới hoặc khi `roomTypeId` thực sự thay đổi — sửa các field khác không cần kiểm tra vì không ảnh hưởng nhu cầu vật dụng).

### 3.6. Checklist trả phòng chia số lượng
`CheckoutChecklistItem` bỏ `status` (enum `ChecklistItemStatus` — **đã xóa hẳn**, không còn dùng ở đâu), thêm `totalQuantity`/`damagedQuantity`/`lostQuantity` (`intactQuantity` chỉ là getter tính toán, không lưu DB). `CheckoutItemRequest`/`CheckoutChecklistItemResponse` đổi tương ứng — server tự lấy `totalQuantity` từ `RoomTypeHandoverItem.quantity` hiện tại (client không gửi, tránh sai lệch). `CheckoutService.checkout()`: validate `damaged + lost <= total`; với mỗi dòng có `lostQuantity > 0`, gọi `ItemService.decrementStock(itemId, lostQuantity)` để trừ tồn kho (giới hạn không âm).

### 3.7. Unit test mới
`ItemServiceTest` — tái hiện chính xác ví dụ tồn kho của người dùng (3 phòng loại A + 2 phòng loại B = đủ 5 tivi/5 tủ lạnh → thêm 1 phòng B nữa → lỗi; sửa 1 phòng A thành B → vẫn hợp lệ). `CheckoutServiceTest` — validate tổng số lượng hư hỏng+mất không vượt tổng, và xác nhận `decrementStock` chỉ được gọi khi có `lostQuantity > 0`.

---

## 4. Frontend

- **Room Detail (Tab 1)**: thêm 2 field Phí wifi / Phí gửi xe, gửi kèm trong request tạo/sửa phòng.
- **Loại phòng (List + Detail)**: thêm bộ lọc chi nhánh giống màn Phòng trọ (dùng lại `RoomService.branchOptions()`); tạo mới cần truyền `branchId` qua query param (giống luồng tạo Phòng); chi nhánh hiển thị read-only trên form chi tiết (không đổi được sau khi tạo).
- **Màn hình mới "Vật dụng" (`/items`)**: List (bộ lọc chi nhánh + tìm theo tên) + Detail (tạo/sửa/xóa, validate `defaultQuantityPerRoom <= quantityAvailable` ngay trên client) — theo đúng khuôn mẫu List+Detail đã dùng cho các màn khác trong dự án. Thêm mục menu sidebar "Vật dụng" cho cả 2 role.
- **Trình soạn vật dụng bàn giao (trong màn Loại phòng)**: đổi ô nhập tên tự do thành dropdown chọn từ danh sách Item của chi nhánh (loại trừ item đã chọn ở dòng khác); chọn xong tự điền số lượng mặc định (`defaultQuantityPerRoom`), vẫn sửa tay được; báo lỗi ngay nếu số lượng vượt tồn kho.
- **Tab "Trả phòng" (Room Detail) + dialog xem lại checklist**: bảng đổi từ 1 cột "Tình trạng" (dropdown) sang 4 cột Tổng số lượng / Hư hỏng / Mất / Còn nguyên (tính tự động); nhập số hư hỏng/mất sẽ tự tính lại Tiền trừ = (hư hỏng + mất) × đơn giá vật dụng (vẫn sửa tay được sau đó); chặn submit nếu hư hỏng + mất > tổng số lượng.
- Xóa enum `ChecklistItemStatus` (frontend) — không còn dùng ở đâu sau thay đổi trên.
- Thêm đầy đủ key i18n (vi/en) cho các field/màn hình mới, xóa các key `CHECKOUT.STATUS_*`/`ITEM_STATUS` không còn dùng.

---

## 5. Ghi chú kỹ thuật quan trọng khác

- **Không build được backend trong phiên này**: máy chỉ có JDK 17, backend cần JDK 21 (giống hạn chế đã ghi nhận ở phiên backend/phiên 2/phiên 3) — toàn bộ thay đổi backend **chỉ được review kỹ bằng mắt, chưa từng compile**. `ng build` phía frontend thì chạy sạch.
- Danh mục chi phí phát sinh "Wifi" (seed từ `V2__seed_extra_fee_categories.sql`, dùng để nhập tay chi phí wifi hàng tháng) vẫn giữ nguyên, không xóa — giờ có phần trùng lặp về mặt ý nghĩa với `room.wifi_fee` mới, nhưng việc này không được yêu cầu xử lý nên để nguyên, chỉ ghi chú lại.
- Quan hệ Item ↔ RoomTypeHandoverItem ↔ Room bây giờ nhất quán chặt: Item thuộc 1 chi nhánh → RoomType thuộc 1 chi nhánh → RoomTypeHandoverItem chỉ chọn Item cùng chi nhánh với RoomType của nó → Room chỉ chọn RoomType cùng chi nhánh với Room. Toàn bộ chuỗi này được validate ở tầng service (không chỉ tin dữ liệu client gửi lên).

---

## 6. Việc còn lại (chưa làm / chưa test được)

- [ ] **Backend cần build + chạy thử bằng JDK 21** — bắt buộc trước khi dùng, để xác nhận toàn bộ thay đổi phiên này (migration V3, entity/service/controller mới, 2 unit test mới) thực sự compile và chạy đúng với MySQL thật.
- [ ] Chạy migration V3 trên dữ liệu dev hiện có cần kiểm tra trước: loại phòng nào đang được dùng bởi phòng ở nhiều chi nhánh khác nhau sẽ bị gán về 1 chi nhánh tùy ý; toàn bộ vật dụng bàn giao đã cấu hình trước đó sẽ mất, cần cấu hình lại từ đầu (tạo Item → gán vào từng Loại phòng).
- [ ] Chưa test round-trip thật với backend chạy thật cho toàn bộ luồng mới: tạo Item → cấu hình vật dụng bàn giao → tạo/sửa phòng đúng ví dụ kiểm tra tồn kho → trả phòng có hư hỏng/mất → xác nhận tồn kho bị trừ đúng.
- [ ] Dashboard vẫn dùng dữ liệu mẫu, chưa nối `GET /api/dashboard` thật (tồn đọng từ các phiên trước, không thuộc phạm vi phiên này).

---

*File này bổ sung cho `tong-hop-trien-khai-frontend-phien-3.md`, `tong-hop-trien-khai-frontend-phien-2.md`, `tong-hop-trien-khai-frontend.md`, `tong-hop-trien-khai-backend.md`, `tong-hop-du-an-quan-ly-phong-tro.md` — đọc cả 6 file để có ngữ cảnh đầy đủ trước khi tiếp tục phát triển.*

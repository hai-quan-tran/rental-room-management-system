# Dev seed scripts

Dữ liệu demo cho môi trường **dev cá nhân**, không phải Flyway migration (không chạy tự động,
không dùng cho production). Sinh 2 chi nhánh, vật dụng, loại phòng, 200 người thuê ngẫu nhiên,
85 phòng có hợp đồng đang hiệu lực, một số phòng ở ghép 2-6 người.

Yêu cầu: đã chạy xong V1-V3 migration và có sẵn tài khoản `admin` (xem `V1__init_schema.sql`).

## Thứ tự chạy

1. `01_branches_items_room_types.sql` — 2 chi nhánh (Quận 9, Bình Dương), 16 vật dụng, 4 loại
   phòng + vật dụng bàn giao. Chạy thẳng bằng mysql client:
   ```
   mysql -h localhost -u root -p --default-character-set=utf8mb4 rental_room_management < 01_branches_items_room_types.sql
   ```
2. `02_gen_tenants_rooms_contracts.js` — script Node (không cần cài thêm package), sinh ngẫu
   nhiên 200 người thuê (tuổi 18-60, CCCD/SĐT đúng định dạng và không trùng, email dạng
   `ten-hotenlot@gmail.com` ~70% có/30% NULL, tự thêm số nếu trùng email) + 85 phòng (15/20 Q9,
   20/30 BD theo đúng giá thuê/wifi đã chốt) + hợp đồng (start quá khứ, end tương lai ≥ 1 năm)
   + 1 người đại diện/phòng. Chạy `node 02_gen_tenants_rooms_contracts.js` trong chính thư mục
   này, ra 2 file `seed_02_tenants.sql` / `seed_03_rooms_contracts.sql` (gitignore, không commit
   vì chứa data ngẫu nhiên hàng lần chạy) rồi import theo thứ tự đó.
3. `03_add_roommates_round1.sql` — chọn ngẫu nhiên 15 phòng đang 1 người, thêm người ở ghép
   (10 phòng thành 2 người, 5 phòng thành 3 người), lấy từ nhóm người thuê chưa có phòng.
4. `04_fill_remaining_tenants_into_rooms.sql` — chọn tiếp 45 phòng (trong số phòng còn 1 người)
   để rải hết toàn bộ người thuê chưa có phòng vào (chừa lại đúng 25 phòng 1 người).

Chạy lại từ đầu (DB dev mới toanh) chỉ cần 1 → 2, bỏ qua 3-4 nếu không cần cảnh huống ở ghép.
Mỗi lần `node 02_...js` chạy sẽ ra dữ liệu ngẫu nhiên khác (tên, tuổi, ngày hợp đồng...).

5. **Hóa đơn hằng tháng (điện/nước tính theo chỉ số + thanh toán)** — yêu cầu đã chạy xong migration
   V4 (`utility_rate`/`meter_reading`). Xóa sạch hóa đơn cũ rồi sinh lại đầy đủ cho từng phòng, từng
   tháng từ lúc nhận phòng đến hết tháng 7/2026, có điện/nước tính theo chỉ số công tơ ngẫu nhiên hợp
   lý theo số người ở; toàn bộ đã thanh toán đủ trừ mỗi chi nhánh chừa 3 phòng chưa thanh toán hóa đơn
   tháng 7 (2/3 phòng đó cũng chỉ thanh toán 1 phần hóa đơn tháng 6):
   ```
   mysql -h localhost -u root -p --default-character-set=utf8mb4 rental_room_management < 05a_clear_bills.sql
   mysql -h localhost -u root -p --default-character-set=utf8mb4 rental_room_management < 05b_utility_rates_baseline.sql
   node 05_generate_monthly_bills.js   # đọc trực tiếp hợp đồng ACTIVE hiện có trong DB, ra seed_05_monthly_bills.sql
   mysql -h localhost -u root -p --default-character-set=utf8mb4 rental_room_management < seed_05_monthly_bills.sql
   ```
   Chạy lại được: lặp lại đúng 4 lệnh trên (mỗi lần `node 05_...js` chạy random khác, giống các script
   khác trong thư mục này). `05b_utility_rates_baseline.sql` chỉ cần chạy 1 lần trừ khi đã xóa
   `utility_rate` — chạy lại sẽ lỗi trùng khóa nếu 4 dòng đó vẫn còn.

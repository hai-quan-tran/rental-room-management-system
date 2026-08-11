// Sinh SQL cho hóa đơn hằng tháng (monthly_bill + meter_reading + extra_fee_item + payment) của
// toàn bộ hợp đồng ACTIVE hiện có trong DB, từ tháng nhận phòng đến hết tháng 7/2026.
// Đọc dữ liệu nguồn trực tiếp từ DB qua `mysql` CLI (không cần cài driver) — chạy sau khi đã chạy
// 05a_clear_bills.sql (xóa hóa đơn cũ) và 05b_utility_rates_baseline.sql (đơn giá điện/nước lịch sử).
//
// Chạy: node 05_generate_monthly_bills.js  →  ra file seed_05_monthly_bills.sql (gitignore, import
// bằng: mysql -h 127.0.0.1 -u root -padmin --default-character-set=utf8mb4 rental_room_management
// < seed_05_monthly_bills.sql

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const DB_HOST = process.env.DB_HOST || '127.0.0.1';
const DB_PORT = process.env.DB_PORT || '3306';
const DB_NAME = process.env.DB_NAME || 'rental_room_management';
const DB_USER = process.env.DB_USERNAME || 'root';
const DB_PASS = process.env.DB_PASSWORD || 'admin';

const END_YEAR = 2026;
const END_MONTH = 7; // sinh hóa đơn đến hết tháng 7/2026, bao gồm

const ELECTRICITY_CATEGORY_ID = 1;
const WATER_CATEGORY_ID = 2;
const ELECTRICITY_UNIT_PRICE = 3500;
const WATER_UNIT_PRICE = 20000;
const ADMIN_ACCOUNT_ID = 1;

function rand(min, max) { return Math.random() * (max - min) + min; }
function randInt(min, max) { return Math.floor(rand(min, max + 1)); }
function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function sqlEscape(str) {
  if (str === null || str === undefined) return 'NULL';
  return "'" + String(str).replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'";
}

function runQuery(sql) {
  // execSync goes through cmd.exe on Windows, which mishandles literal newlines inside a quoted
  // argument (silently produces empty output) — collapse to a single line before invoking mysql.
  const oneLine = sql.replace(/\s+/g, ' ').trim();
  const cmd = `mysql -h ${DB_HOST} -P ${DB_PORT} -u ${DB_USER} -p${DB_PASS} -N -B --default-character-set=utf8mb4 ${DB_NAME} -e "${oneLine.replace(/"/g, '\\"')}"`;
  const out = execSync(cmd, { encoding: 'utf8' });
  return out.split('\n').map((l) => l.trim()).filter((l) => l.length > 0).map((l) => l.split('\t'));
}

function daysInMonth(year, month) {
  return new Date(year, month, 0).getDate();
}

function addMonth(year, month) {
  return month === 12 ? { year: year + 1, month: 1 } : { year, month: month + 1 };
}

function isoDate(year, month, day) {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

// Tái hiện đúng RentCalculator.calculateProratedRent (backend), HALF_UP về số nguyên VNĐ.
function proratedRent(monthlyRent, year, month, startDate) {
  const dim = daysInMonth(year, month);
  const startsThisMonth = startDate.getFullYear() === year && startDate.getMonth() + 1 === month;
  const occupiedFrom = startsThisMonth ? startDate.getDate() : 1;
  const daysOccupied = dim - occupiedFrom + 1;
  if (daysOccupied >= dim) return Math.round(monthlyRent);
  return Math.round((monthlyRent * daysOccupied) / dim);
}

// ---------------------------------------------------------------------------
// 1. Đọc danh sách hợp đồng ACTIVE + số người thuê từ DB thật
// ---------------------------------------------------------------------------
const rows = runQuery(`
  SELECT c.id, c.room_id, r.branch_id, c.start_date, c.monthly_rent, r.wifi_fee, r.parking_fee,
         (SELECT COUNT(*) FROM contract_tenant ct WHERE ct.contract_id = c.id) AS tenant_count
  FROM contract c
  JOIN room r ON r.id = c.room_id
  WHERE c.status = 'ACTIVE'
  ORDER BY r.branch_id, c.id;
`);

const contracts = rows.map((r) => ({
  contractId: Number(r[0]),
  roomId: Number(r[1]),
  branchId: Number(r[2]),
  startDate: new Date(r[3] + 'T00:00:00'),
  monthlyRent: Number(r[4]),
  wifiFee: Number(r[5]),
  parkingFee: Number(r[6]),
  tenantCount: Math.max(1, Number(r[7])),
}));

console.log(`Đọc được ${contracts.length} hợp đồng ACTIVE.`);

// ---------------------------------------------------------------------------
// 2. Chọn phòng ngoại lệ: mỗi chi nhánh 3 phòng chưa thanh toán tháng 7, 2/3 trong đó chỉ
//    thanh toán 1 phần tháng 6 — chỉ chọn từ hợp đồng đã bắt đầu trước/trong tháng 6/2026
//    (đảm bảo có hóa đơn tháng 6 để mà "1 phần").
// ---------------------------------------------------------------------------
const unpaidJulyRoomIds = new Set();
const partialJuneRoomIds = new Set();

const branchIds = [...new Set(contracts.map((c) => c.branchId))];
for (const branchId of branchIds) {
  const eligible = contracts.filter((c) => c.branchId === branchId && c.startDate <= new Date('2026-06-30T23:59:59'));
  const picked = shuffle(eligible).slice(0, 3);
  picked.forEach((c) => unpaidJulyRoomIds.add(c.roomId));
  shuffle(picked).slice(0, 2).forEach((c) => partialJuneRoomIds.add(c.roomId));
}

console.log(`Phòng chưa thanh toán tháng 7 (2 chi nhánh): ${[...unpaidJulyRoomIds].join(', ')}`);
console.log(`Phòng thanh toán 1 phần tháng 6 (subset ở trên): ${[...partialJuneRoomIds].join(', ')}`);

// ---------------------------------------------------------------------------
// 3. Sinh SQL cho từng hợp đồng, từng tháng
// ---------------------------------------------------------------------------
const sql = [];
sql.push('-- File sinh tự động bởi 05_generate_monthly_bills.js — KHÔNG commit (xem .gitignore).');
sql.push('SET NAMES utf8mb4;');
sql.push('START TRANSACTION;');
sql.push('');

for (const c of contracts) {
  let month = c.startDate.getMonth() + 1;
  let year = c.startDate.getFullYear();
  let oldElectric = randInt(100, 400);
  let oldWater = randInt(10, 40);

  while (year < END_YEAR || (year === END_YEAR && month <= END_MONTH)) {
    const rentAmount = proratedRent(c.monthlyRent, year, month, c.startDate);

    const electricConsumption = Math.max(15, Math.round(20 + c.tenantCount * randInt(25, 50) + randInt(-10, 10)));
    const newElectric = oldElectric + electricConsumption;
    const electricAmount = Math.round(electricConsumption * ELECTRICITY_UNIT_PRICE);

    const waterConsumption = Math.round((1 + c.tenantCount * rand(2.5, 4.5)) * 10) / 10;
    const newWater = Math.round((oldWater + waterConsumption) * 10) / 10;
    const waterAmount = Math.round(waterConsumption * WATER_UNIT_PRICE);

    const totalExtraFee = electricAmount + waterAmount;
    const totalAmount = rentAmount + totalExtraFee + c.wifiFee + c.parkingFee;

    const next = addMonth(year, month);
    const createdAt = `${isoDate(next.year, next.month, 5)} 09:00:00`;

    const isJuly2026 = year === END_YEAR && month === END_MONTH;
    const isJune2026 = year === END_YEAR && month === END_MONTH - 1;
    const skipPayment = isJuly2026 && unpaidJulyRoomIds.has(c.roomId);
    const isPartialJune = isJune2026 && partialJuneRoomIds.has(c.roomId);

    // Ngày thanh toán: sau khi hóa đơn được tạo (đầu tháng kế tiếp); chặn không vượt quá "hôm nay"
    // (11/8/2026) cho riêng hóa đơn tháng 7 (tạo 5/8/2026).
    const isBillCreatedInCurrentMonth = next.year === 2026 && next.month === 8;
    const paymentDayMax = isBillCreatedInCurrentMonth ? 11 : 25;
    const paymentDay = randInt(6, paymentDayMax);
    const paymentDate = isoDate(next.year, next.month, paymentDay);

    sql.push(`-- Contract ${c.contractId} (room ${c.roomId}, branch ${c.branchId}) — ${year}-${String(month).padStart(2, '0')}`);

    sql.push(`INSERT INTO meter_reading (room_id, extra_fee_category_id, bill_month, bill_year, old_reading, new_reading, recorded_by, created_at, updated_at) VALUES (${c.roomId}, ${ELECTRICITY_CATEGORY_ID}, ${month}, ${year}, ${oldElectric.toFixed(2)}, ${newElectric.toFixed(2)}, ${ADMIN_ACCOUNT_ID}, ${sqlEscape(createdAt)}, ${sqlEscape(createdAt)});`);
    sql.push(`INSERT INTO meter_reading (room_id, extra_fee_category_id, bill_month, bill_year, old_reading, new_reading, recorded_by, created_at, updated_at) VALUES (${c.roomId}, ${WATER_CATEGORY_ID}, ${month}, ${year}, ${oldWater.toFixed(2)}, ${newWater.toFixed(2)}, ${ADMIN_ACCOUNT_ID}, ${sqlEscape(createdAt)}, ${sqlEscape(createdAt)});`);

    sql.push(`INSERT INTO monthly_bill (contract_id, bill_month, bill_year, rent_amount, total_extra_fee, wifi_fee, parking_fee, paid_amount, created_at, updated_at) VALUES (${c.contractId}, ${month}, ${year}, ${rentAmount}, ${totalExtraFee}, ${c.wifiFee}, ${c.parkingFee}, 0, ${sqlEscape(createdAt)}, ${sqlEscape(createdAt)});`);
    sql.push('SET @bid = LAST_INSERT_ID();');

    const electricNote = `Tự động: chỉ số ${oldElectric.toFixed(2)}→${newElectric.toFixed(2)} × ${ELECTRICITY_UNIT_PRICE}đ`;
    const waterNote = `Tự động: chỉ số ${oldWater.toFixed(2)}→${newWater.toFixed(2)} × ${WATER_UNIT_PRICE}đ`;
    sql.push(`INSERT INTO extra_fee_item (monthly_bill_id, extra_fee_category_id, amount, note, created_at) VALUES (@bid, ${ELECTRICITY_CATEGORY_ID}, ${electricAmount}, ${sqlEscape(electricNote)}, ${sqlEscape(createdAt)});`);
    sql.push(`INSERT INTO extra_fee_item (monthly_bill_id, extra_fee_category_id, amount, note, created_at) VALUES (@bid, ${WATER_CATEGORY_ID}, ${waterAmount}, ${sqlEscape(waterNote)}, ${sqlEscape(createdAt)});`);

    if (!skipPayment) {
      const paidAmount = isPartialJune
        ? Math.round((totalAmount * rand(0.4, 0.7)) / 1000) * 1000
        : totalAmount;
      const method = rand(0, 1) < 0.5 ? 'Tiền mặt' : 'Chuyển khoản';
      sql.push(`INSERT INTO payment (monthly_bill_id, amount, payment_date, method, note, created_by) VALUES (@bid, ${paidAmount}, ${sqlEscape(paymentDate)}, ${sqlEscape(method)}, NULL, ${ADMIN_ACCOUNT_ID});`);
    }

    sql.push('');

    oldElectric = newElectric;
    oldWater = newWater;
    const nxt = addMonth(year, month);
    year = nxt.year;
    month = nxt.month;
  }
}

sql.push('COMMIT;');

const outPath = path.join(__dirname, 'seed_05_monthly_bills.sql');
fs.writeFileSync(outPath, sql.join('\n'), 'utf8');
console.log(`Đã ghi ${outPath}`);

// Sinh SQL seed cho 200 người thuê + 85 phòng/hợp đồng (dữ liệu dev, chạy 1 lần).
const fs = require('fs');
const path = require('path');

function rand(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
function randInt(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min; }

function stripAccents(str) {
  return str
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();
}

function sqlEscape(str) {
  if (str === null || str === undefined) return 'NULL';
  return "'" + String(str).replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'";
}

// ---------------------------------------------------------------------------
// 1. SINH 200 NGƯỜI THUÊ
// ---------------------------------------------------------------------------
const HO = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Võ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương', 'Lý'];
const DEM = ['Văn', 'Thị', 'Hữu', 'Đức', 'Minh', 'Ngọc', 'Thanh', 'Xuân', 'Quang', 'Thành', 'Công', 'Kim', 'Đình', 'Trung', 'Gia', 'Bảo', 'Anh', 'Duy', 'Việt', 'Hồng'];
const TEN = [
  'Anh', 'Bình', 'Cường', 'Dũng', 'Giang', 'Hà', 'Hải', 'Hùng', 'Khang', 'Lan', 'Linh', 'Mai', 'Nam', 'Oanh',
  'Phong', 'Quân', 'Sơn', 'Tâm', 'Thảo', 'Tuấn', 'Uyên', 'Việt', 'Yến', 'Trang', 'Hương', 'Huy', 'Khoa', 'Long',
  'Minh', 'Ngọc', 'Phúc', 'Quý', 'Thắng', 'Trung', 'Vinh', 'Đạt', 'Hạnh', 'Hiếu', 'Hoa', 'Kiên', 'Loan', 'My',
  'Nga', 'Nhung', 'Phương', 'Quyên', 'Thi', 'Thúy', 'Tú', 'Vy', 'Yên', 'Chi', 'Đăng', 'Hằng', 'Hiền', 'Huyền',
  'Khánh', 'Lộc', 'Nhân', 'Nhi', 'Phát', 'Quốc', 'Thịnh', 'Trâm', 'Tuyết', 'Vân',
];

const PHONE_PREFIXES = [
  '090', '091', '092', '093', '094', '096', '097', '098', '099',
  '032', '033', '034', '035', '036', '037', '038', '039',
  '070', '076', '077', '078', '079', '081', '082', '083', '084', '085', '088', '089',
];

const PROVINCE_CODES = ['079', '074', '080', '048', '051', '056', '060', '064', '068', '072', '092', '096'];

function randomDobIso() {
  // Tuổi từ 18 đến 60, "today" = 2026-08-04 (currentDate của môi trường).
  const today = new Date('2026-08-04T00:00:00+07:00');
  const minAge = 18;
  const maxAge = 60;
  const ageYears = randInt(minAge, maxAge);
  const dob = new Date(today);
  dob.setFullYear(dob.getFullYear() - ageYears);
  // Lùi thêm 0-364 ngày để rải rác trong năm, nhưng vẫn đảm bảo >= minAge (không tiến ngày, chỉ lùi).
  dob.setDate(dob.getDate() - randInt(0, 300));
  return dob.toISOString().slice(0, 10);
}

function randomCccd(usedCccd) {
  let cccd;
  do {
    const province = rand(PROVINCE_CODES);
    const genderCentury = randInt(0, 9);
    const yy = String(randInt(0, 99)).padStart(2, '0');
    const rest = String(randInt(0, 999999)).padStart(6, '0');
    cccd = `${province}${genderCentury}${yy}${rest}`;
  } while (usedCccd.has(cccd));
  usedCccd.add(cccd);
  return cccd;
}

function randomPhone(usedPhones) {
  let phone;
  do {
    phone = rand(PHONE_PREFIXES) + String(randInt(0, 9999999)).padStart(7, '0');
  } while (usedPhones.has(phone));
  usedPhones.add(phone);
  return phone;
}

function buildEmail(fullNameParts, usedEmails) {
  // fullNameParts: mảng các từ trong họ tên, ví dụ ['Nguyễn','Trung','Anh']
  // tên = từ cuối cùng, "họ tên lót" = các từ còn lại ghép liền không dấu.
  const ten = stripAccents(fullNameParts[fullNameParts.length - 1]);
  const hoTenLot = fullNameParts
    .slice(0, -1)
    .map(stripAccents)
    .join('');
  let local = `${ten}-${hoTenLot}`;
  let email = `${local}@gmail.com`;
  if (usedEmails.has(email)) {
    do {
      email = `${local}${randInt(1, 9999)}@gmail.com`;
    } while (usedEmails.has(email));
  }
  usedEmails.add(email);
  return email;
}

const usedCccd = new Set();
const usedPhones = new Set();
const usedEmails = new Set();

const tenants = [];
for (let i = 0; i < 200; i++) {
  const ho = rand(HO);
  const dem = rand(DEM);
  const ten = rand(TEN);
  const fullName = `${ho} ${dem} ${ten}`;
  const dob = randomDobIso();
  const cccd = randomCccd(usedCccd);
  const phone = randomPhone(usedPhones);
  // ~70% có email, 30% không (email nullable).
  const email = Math.random() < 0.7 ? buildEmail([ho, dem, ten], usedEmails) : null;
  tenants.push({ fullName, dob, cccd, phone, email });
}

// ---------------------------------------------------------------------------
// 2. SINH SQL: TENANT
// ---------------------------------------------------------------------------
const tenantValues = tenants
  .map(
    (t) =>
      `(${sqlEscape(t.fullName)}, '${t.dob}', ${sqlEscape(t.cccd)}, ${sqlEscape(t.phone)}, ${sqlEscape(t.email)}, (SELECT id FROM account WHERE username = 'admin'), (SELECT id FROM account WHERE username = 'admin'))`,
  )
  .join(',\n');

const tenantSql = `SET NAMES utf8mb4;

-- =====================================================================================
-- 200 NGƯỜI THUÊ NGẪU NHIÊN (dev seed)
-- =====================================================================================
INSERT INTO tenant (full_name, date_of_birth, id_card_number, phone_number, email, created_by, updated_by) VALUES
${tenantValues};
`;

// ---------------------------------------------------------------------------
// 3. SINH PHÒNG + HỢP ĐỒNG + CONTRACT_TENANT
// ---------------------------------------------------------------------------
// Mỗi phòng dùng 1 người thuê đại diện, lấy tuần tự từ danh sách 200 tenant vừa sinh
// (đối chiếu qua id_card_number vì lúc insert chưa biết id auto-increment).
let tenantCursor = 0;
function nextTenantCccd() {
  const t = tenants[tenantCursor % tenants.length];
  tenantCursor++;
  return t.cccd;
}

function randomContractDates() {
  const today = new Date('2026-08-04T00:00:00+07:00');
  // start: 1-6 tháng trước hôm nay.
  const monthsAgo = randInt(1, 6);
  const start = new Date(today);
  start.setMonth(start.getMonth() - monthsAgo);
  start.setDate(randInt(1, 28));
  // duration: 12-24 tháng kể từ start -> end luôn ở tương lai so với hôm nay.
  const durationMonths = randInt(12, 24);
  const end = new Date(start);
  end.setMonth(end.getMonth() + durationMonths);
  return {
    start: start.toISOString().slice(0, 10),
    end: end.toISOString().slice(0, 10),
  };
}

const roomPlans = [
  { branch: 'Chi nhánh Quận 9', roomType: 'Có gác(TV&MG)', count: 15, rent: 3500000, wifi: 50000, parking: 0, codePrefix: 'Q9-TVMG' },
  { branch: 'Chi nhánh Quận 9', roomType: 'Có gác(Thường)', count: 20, rent: 2800000, wifi: 50000, parking: 0, codePrefix: 'Q9-TH' },
  { branch: 'Chi nhánh Bình Dương', roomType: 'Có gác(TV&MG)', count: 20, rent: 3300000, wifi: 50000, parking: 0, codePrefix: 'BD-TVMG' },
  { branch: 'Chi nhánh Bình Dương', roomType: 'Có gác(Thường)', count: 30, rent: 2600000, wifi: 50000, parking: 0, codePrefix: 'BD-TH' },
];

const roomRows = [];
const contractRows = [];
const contractTenantRows = [];

for (const plan of roomPlans) {
  for (let i = 1; i <= plan.count; i++) {
    const roomCode = `${plan.codePrefix}-${String(i).padStart(2, '0')}`;
    const dates = randomContractDates();
    const deposit = plan.rent; // cọc mặc định = 1 tháng tiền thuê
    const cccd = nextTenantCccd();

    // Dùng room_code làm khóa tương quan tạm (unique trong batch) để nối room -> contract -> contract_tenant
    // bằng subquery, không cần biết id auto-increment trước.
    roomRows.push({ branch: plan.branch, roomType: plan.roomType, roomCode, rent: plan.rent, wifi: plan.wifi, parking: plan.parking });
    contractRows.push({ roomCode, branch: plan.branch, start: dates.start, end: dates.end, rent: plan.rent, deposit });
    contractTenantRows.push({ roomCode, branch: plan.branch, cccd, joinedAt: dates.start });
  }
}

const roomValues = roomRows
  .map(
    (r) =>
      `((SELECT id FROM branch WHERE name = ${sqlEscape(r.branch)}), ${sqlEscape(r.roomCode)}, (SELECT id FROM room_type WHERE name = ${sqlEscape(r.roomType)} AND branch_id = (SELECT id FROM branch WHERE name = ${sqlEscape(r.branch)})), ${r.rent}, ${r.wifi}, ${r.parking}, 'DANG_THUE')`,
  )
  .join(',\n');

const roomSql = `
-- =====================================================================================
-- PHÒNG (85 phòng, status = DANG_THUE vì mỗi phòng có sẵn hợp đồng đang hiệu lực)
-- =====================================================================================
INSERT INTO room (branch_id, room_code, room_type_id, monthly_rent, wifi_fee, parking_fee, status) VALUES
${roomValues};
`;

const contractValues = contractRows
  .map(
    (c) =>
      `((SELECT id FROM room WHERE room_code = ${sqlEscape(c.roomCode)} AND branch_id = (SELECT id FROM branch WHERE name = ${sqlEscape(c.branch)})), '${c.start}', '${c.end}', ${c.rent}, ${c.deposit}, 'ACTIVE', (SELECT id FROM account WHERE username = 'admin'), (SELECT id FROM account WHERE username = 'admin'))`,
  )
  .join(',\n');

const contractSql = `
-- =====================================================================================
-- HỢP ĐỒNG (1 hợp đồng ACTIVE / phòng — start quá khứ, end tương lai >= start + 1 năm)
-- =====================================================================================
INSERT INTO contract (room_id, start_date, end_date, monthly_rent, deposit_amount, status, created_by, updated_by) VALUES
${contractValues};
`;

const contractTenantValues = contractTenantRows
  .map(
    (ct) =>
      `((SELECT c.id FROM contract c JOIN room r ON r.id = c.room_id WHERE r.room_code = ${sqlEscape(ct.roomCode)} AND r.branch_id = (SELECT id FROM branch WHERE name = ${sqlEscape(ct.branch)})), (SELECT id FROM tenant WHERE id_card_number = ${sqlEscape(ct.cccd)}), 1, '${ct.joinedAt}')`,
  )
  .join(',\n');

const contractTenantSql = `
-- =====================================================================================
-- CONTRACT_TENANT — người thuê đại diện cho mỗi hợp đồng (mỗi phòng >= 1 người thuê)
-- =====================================================================================
INSERT INTO contract_tenant (contract_id, tenant_id, is_representative, joined_at) VALUES
${contractTenantValues};
`;

// ---------------------------------------------------------------------------
// GHI FILE
// ---------------------------------------------------------------------------
const outDir = __dirname;
fs.writeFileSync(path.join(outDir, 'seed_02_tenants.sql'), tenantSql, 'utf8');
fs.writeFileSync(path.join(outDir, 'seed_03_rooms_contracts.sql'), 'SET NAMES utf8mb4;\n' + roomSql + contractSql + contractTenantSql, 'utf8');

console.log(`Generated ${tenants.length} tenants, ${roomRows.length} rooms, ${contractRows.length} contracts, ${contractTenantRows.length} contract_tenant rows.`);
console.log('Sample tenant:', tenants[0]);
console.log('Sample tenant with null email count:', tenants.filter((t) => t.email === null).length);

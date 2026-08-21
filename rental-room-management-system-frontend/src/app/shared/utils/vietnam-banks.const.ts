export interface VietnamBank {
  bin: string;
  shortName: string;
  fullName: string;
}

/**
 * Static snapshot of banks VietQR (Napas) supports interbank transfer for, fetched once from
 * the public `https://api.vietqr.io/v2/banks` (filtered to `isTransfer === 1`) rather than
 * called at runtime — the list barely changes and this avoids a network dependency just to
 * populate a dropdown. Sorted by `shortName`.
 */
export const VIETNAM_BANKS: VietnamBank[] = [
  { bin: '970425', shortName: 'ABBANK', fullName: 'Ngân hàng TMCP An Bình' },
  { bin: '970416', shortName: 'ACB', fullName: 'Ngân hàng TMCP Á Châu' },
  { bin: '970405', shortName: 'Agribank', fullName: 'Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam' },
  { bin: '970409', shortName: 'BacABank', fullName: 'Ngân hàng TMCP Bắc Á' },
  { bin: '970438', shortName: 'BaoVietBank', fullName: 'Ngân hàng TMCP Bảo Việt' },
  { bin: '970418', shortName: 'BIDV', fullName: 'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam' },
  { bin: '546034', shortName: 'CAKE', fullName: 'TMCP Việt Nam Thịnh Vượng - Ngân hàng số CAKE by VPBank' },
  { bin: '422589', shortName: 'CIMB', fullName: 'Ngân hàng TNHH MTV CIMB Việt Nam' },
  { bin: '970446', shortName: 'COOPBANK', fullName: 'Ngân hàng Hợp tác xã Việt Nam' },
  { bin: '970431', shortName: 'Eximbank', fullName: 'Ngân hàng TMCP Xuất Nhập khẩu Việt Nam' },
  { bin: '970437', shortName: 'HDBank', fullName: 'Ngân hàng TMCP Phát triển Thành phố Hồ Chí Minh' },
  { bin: '668888', shortName: 'KBank', fullName: 'Ngân hàng Đại chúng TNHH Kasikornbank' },
  { bin: '970452', shortName: 'KienLongBank', fullName: 'Ngân hàng TMCP Kiên Long' },
  { bin: '970449', shortName: 'LPBank', fullName: 'Ngân hàng TMCP Lộc Phát Việt Nam' },
  { bin: '970422', shortName: 'MBBank', fullName: 'Ngân hàng TMCP Quân đội' },
  { bin: '970414', shortName: 'MBV', fullName: 'Ngân hàng TNHH MTV Việt Nam Hiện Đại' },
  { bin: '971025', shortName: 'MoMo', fullName: 'CTCP Dịch Vụ Di Động Trực Tuyến' },
  { bin: '970426', shortName: 'MSB', fullName: 'Ngân hàng TMCP Hàng Hải Việt Nam' },
  { bin: '970428', shortName: 'NamABank', fullName: 'Ngân hàng TMCP Nam Á' },
  { bin: '970419', shortName: 'NCB', fullName: 'Ngân hàng TMCP Quốc Dân' },
  { bin: '970448', shortName: 'OCB', fullName: 'Ngân hàng TMCP Phương Đông' },
  { bin: '970430', shortName: 'PGBank', fullName: 'Ngân hàng TMCP Thịnh vượng và Phát triển' },
  { bin: '970412', shortName: 'PVcomBank', fullName: 'Ngân hàng TMCP Đại Chúng Việt Nam' },
  { bin: '971133', shortName: 'PVcomBank Pay', fullName: 'Ngân hàng TMCP Đại Chúng Việt Nam Ngân hàng số' },
  { bin: '970403', shortName: 'Sacombank', fullName: 'Ngân hàng TMCP Sài Gòn Thương Tín' },
  { bin: '970400', shortName: 'SaigonBank', fullName: 'Ngân hàng TMCP Sài Gòn Công Thương' },
  { bin: '970429', shortName: 'SCB', fullName: 'Ngân hàng TMCP Sài Gòn' },
  { bin: '970440', shortName: 'SeABank', fullName: 'Ngân hàng TMCP Đông Nam Á' },
  { bin: '970443', shortName: 'SHB', fullName: 'Ngân hàng TMCP Sài Gòn - Hà Nội' },
  { bin: '970424', shortName: 'ShinhanBank', fullName: 'Ngân hàng TNHH MTV Shinhan Việt Nam' },
  { bin: '970407', shortName: 'Techcombank', fullName: 'Ngân hàng TMCP Kỹ thương Việt Nam' },
  { bin: '963388', shortName: 'Timo', fullName: 'Ngân hàng số Timo by Ban Viet Bank' },
  { bin: '970423', shortName: 'TPBank', fullName: 'Ngân hàng TMCP Tiên Phong' },
  { bin: '546035', shortName: 'Ubank', fullName: 'TMCP Việt Nam Thịnh Vượng - Ngân hàng số Ubank by VPBank' },
  { bin: '970441', shortName: 'VIB', fullName: 'Ngân hàng TMCP Quốc tế Việt Nam' },
  { bin: '970427', shortName: 'VietABank', fullName: 'Ngân hàng TMCP Việt Á' },
  { bin: '970433', shortName: 'VietBank', fullName: 'Ngân hàng TMCP Việt Nam Thương Tín' },
  { bin: '970454', shortName: 'VietCapitalBank', fullName: 'Ngân hàng TMCP Bản Việt' },
  { bin: '970436', shortName: 'Vietcombank', fullName: 'Ngân hàng TMCP Ngoại Thương Việt Nam' },
  { bin: '970415', shortName: 'VietinBank', fullName: 'Ngân hàng TMCP Công thương Việt Nam' },
  { bin: '970432', shortName: 'VPBank', fullName: 'Ngân hàng TMCP Việt Nam Thịnh Vượng' },
  { bin: '970457', shortName: 'Woori', fullName: 'Ngân hàng TNHH MTV Woori Việt Nam' },
];

/** Looks up a bank's display name by BIN — returns `null` if not found or `bin` is null. */
export function vietnamBankShortName(bin: string | null | undefined): string | null {
  if (!bin) {
    return null;
  }
  return VIETNAM_BANKS.find((b) => b.bin === bin)?.shortName ?? null;
}

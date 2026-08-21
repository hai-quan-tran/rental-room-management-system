const COMBINING_DIACRITICS_REGEX = /[̀-ͯ]/g;

/** Strips Vietnamese diacritics — VietQR's `addInfo` (transfer note) must stay plain ASCII or some bank apps fail to parse it. */
export function stripDiacritics(text: string): string {
  return text
    .normalize('NFD')
    .replace(COMBINING_DIACRITICS_REGEX, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D');
}

/**
 * Builds a VietQR Quick Link image URL (https://www.vietqr.io/en/danh-sach-api/link-tao-ma-nhanh/)
 * — no API key needed, the bank/account/amount/note are all embedded in the QR itself so a
 * scanning banking app can auto-fill the transfer. Returns `null` when the branch has no bank
 * info configured yet, so callers can `@if` around it cleanly.
 */
export function buildVietQrImageUrl(
  bankBin: string | null,
  accountNumber: string | null,
  amount: number,
  addInfo: string,
  accountName: string | null,
): string | null {
  if (!bankBin || !accountNumber) {
    return null;
  }
  const params = new URLSearchParams({
    amount: String(Math.max(0, Math.round(amount))),
    addInfo: stripDiacritics(addInfo),
  });
  if (accountName) {
    params.set('accountName', stripDiacritics(accountName));
  }
  return `https://img.vietqr.io/image/${bankBin}-${accountNumber}-compact2.png?${params.toString()}`;
}

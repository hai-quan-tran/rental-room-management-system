/** Local Date -> ISO "yyyy-MM-dd", avoiding the UTC-midnight shift `date.toISOString()` causes. */
export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** ISO "yyyy-MM-dd" -> local Date, avoiding the UTC-midnight shift `new Date(iso)` causes. */
export function fromIsoDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** Extracts the calendar (year, 1-12 month) pair a `Date` falls in. */
export function dateToYearMonth(date: Date): { year: number; month: number } {
  return { year: date.getFullYear(), month: date.getMonth() + 1 };
}

/** Shifts a (year, month) pair by `delta` calendar months, wrapping the year as needed. */
export function addMonths(
  year: number,
  month: number,
  delta: number,
): { year: number; month: number } {
  const d = new Date(year, month - 1 + delta, 1);
  return dateToYearMonth(d);
}

/** Shifts a Date by `delta` calendar months, wrapping the year as needed. */
export function shiftMonthDate(date: Date, delta: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + delta, 1);
}

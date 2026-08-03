import { Signal, computed } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export interface OptionEntry<T> {
  labelKey: string;
  value: T;
}

/**
 * PrimeNG select-like components render `optionLabel` as plain text, so a
 * translation key can't be piped in the template. This resolves labels
 * up front into a signal that recomputes whenever the active language changes.
 */
export function translatedOptions<T>(
  translate: TranslateService,
  entries: OptionEntry<T>[],
): Signal<{ label: string; value: T }[]> {
  return computed(() => entries.map((entry) => ({ label: translate.instant(entry.labelKey), value: entry.value })));
}

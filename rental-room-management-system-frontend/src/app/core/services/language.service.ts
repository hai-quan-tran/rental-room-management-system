import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export type AppLang = 'vi' | 'en';

const STORAGE_KEY = 'rrms_lang';
const SUPPORTED: AppLang[] = ['vi', 'en'];
const DEFAULT_LANG: AppLang = 'vi';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);

  readonly currentLang = this.translate.currentLang;
  readonly supportedLangs = SUPPORTED;

  setLanguage(lang: AppLang): void {
    this.translate.use(lang).subscribe();
    localStorage.setItem(STORAGE_KEY, lang);
  }

  /** Read synchronously at app bootstrap so the initial `use()` needs no extra language switch. */
  static resolveInitialLang(): AppLang {
    if (typeof localStorage === 'undefined') {
      return DEFAULT_LANG;
    }
    const stored = localStorage.getItem(STORAGE_KEY) as AppLang | null;
    return stored && SUPPORTED.includes(stored) ? stored : DEFAULT_LANG;
  }
}

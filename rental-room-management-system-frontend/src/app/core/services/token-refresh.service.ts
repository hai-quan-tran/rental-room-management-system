import { Injectable, inject } from '@angular/core';
import { Observable, finalize, shareReplay } from 'rxjs';

import { AuthTokens } from '../models/auth.model';
import { AuthService } from './auth.service';

/**
 * De-duplicates concurrent refresh calls: if several requests 401 at once,
 * only one POST /auth/refresh goes out and every caller shares its result.
 */
@Injectable({ providedIn: 'root' })
export class TokenRefreshService {
  private readonly authService = inject(AuthService);

  private refreshInFlight$: Observable<AuthTokens> | null = null;

  refresh(): Observable<AuthTokens> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.authService.refresh().pipe(
        shareReplay(1),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
      );
    }
    return this.refreshInFlight$;
  }
}

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { ApiResponse } from '../models/api-response.model';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';
import { TokenRefreshService } from '../services/token-refresh.service';

const AUTH_ENDPOINTS = ['/auth/login', '/auth/refresh', '/auth/logout'];

function extractMessage(error: unknown): string | undefined {
  if (!(error instanceof HttpErrorResponse)) {
    return undefined;
  }
  const body = error.error as ApiResponse<unknown> | null | undefined;
  return typeof body?.message === 'string' ? body.message : undefined;
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  // Skip entirely for the translation JSON files themselves: they load as part of
  // TranslateService's own construction, so injecting TranslateService here for
  // that specific request would be a circular dependency (NG0200).
  if (req.url.includes('/i18n/')) {
    return next(req);
  }

  const authService = inject(AuthService);
  const tokenRefresh = inject(TokenRefreshService);
  const notification = inject(NotificationService);
  const translate = inject(TranslateService);
  const router = inject(Router);

  const isAuthEndpoint = AUTH_ENDPOINTS.some((path) => req.url.includes(path));

  const showErrorToast = (error: unknown) => {
    notification.error(extractMessage(error) ?? translate.instant('COMMON.SYSTEM_ERROR'));
  };

  const redirectToLogin = () => {
    authService.clearSession();
    router.navigate(['/login']);
  };

  return next(req).pipe(
    catchError((error: unknown) => {
      const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;

      // Non-401s, and a 401 from the auth endpoints themselves (bad login credentials,
      // an already-invalid refresh/logout token) — just surface the error, no session dance.
      if (!isUnauthorized || isAuthEndpoint) {
        showErrorToast(error);
        return throwError(() => error);
      }

      // A real authenticated-request 401 (expired access token). No refresh token to try -> done.
      if (!authService.getRefreshToken()) {
        redirectToLogin();
        showErrorToast(error);
        return throwError(() => error);
      }

      return tokenRefresh.refresh().pipe(
        catchError((refreshError: unknown) => {
          redirectToLogin();
          showErrorToast(refreshError);
          return throwError(() => refreshError);
        }),
        switchMap((tokens) =>
          next(req.clone({ setHeaders: { Authorization: `Bearer ${tokens.accessToken}` } })).pipe(
            catchError((retryError: unknown) => {
              showErrorToast(retryError);
              return throwError(() => retryError);
            }),
          ),
        ),
      );
    }),
  );
};

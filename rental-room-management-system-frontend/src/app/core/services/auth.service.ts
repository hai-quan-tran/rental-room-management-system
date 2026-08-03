import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Role } from '../enums/role.enum';
import { AuthTokens, CurrentUser, JwtPayload, LoginRequest } from '../models/auth.model';
import { ApiService } from './api.service';

const ACCESS_TOKEN_KEY = 'rrms_access_token';
const REFRESH_TOKEN_KEY = 'rrms_refresh_token';

function decodeJwt(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(normalized));
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);

  private readonly currentUserSignal = signal<CurrentUser | null>(this.readUserFromStorage());

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);

  login(request: LoginRequest): Observable<AuthTokens> {
    return this.api
      .post<AuthTokens>(`${environment.apiUrl}/auth/login`, request)
      .pipe(tap((tokens) => this.storeSession(tokens)));
  }

  logout(): Observable<void> {
    return this.api
      .post<void>(`${environment.apiUrl}/auth/logout`, { refreshToken: this.getRefreshToken() })
      .pipe(tap(() => this.clearSession()));
  }

  refresh(): Observable<AuthTokens> {
    return this.api
      .post<AuthTokens>(`${environment.apiUrl}/auth/refresh`, { refreshToken: this.getRefreshToken() })
      .pipe(tap((tokens) => this.storeSession(tokens)));
  }

  hasRole(...roles: Role[]): boolean {
    const user = this.currentUserSignal();
    return !!user && roles.includes(user.role);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  clearSession(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.currentUserSignal.set(null);
  }

  private storeSession(tokens: AuthTokens): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    this.currentUserSignal.set(this.toCurrentUser(tokens.accessToken));
  }

  private readUserFromStorage(): CurrentUser | null {
    const token = this.getAccessToken();
    return token ? this.toCurrentUser(token) : null;
  }

  private toCurrentUser(accessToken: string): CurrentUser | null {
    const payload = decodeJwt(accessToken);
    if (!payload) {
      return null;
    }
    return {
      userId: Number(payload.sub),
      username: payload.username,
      fullName: payload.username,
      role: payload.role,
      branchIds: payload.branchIds ?? [],
    };
  }
}

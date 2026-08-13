import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Role } from '../enums/role.enum';
import { ListQuery, PageResponse } from '../models/api-response.model';
import { AccountCreateRequest, AccountResponse, AccountUpdateRequest } from '../models/account.model';
import { buildListParams } from '../utils/http-params.util';
import { ApiService } from './api.service';

export interface AccountListFilter {
  role?: Role | null;
  active?: boolean | null;
  search?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly api = inject(ApiService);

  list(query: ListQuery, filter: AccountListFilter = {}): Observable<PageResponse<AccountResponse>> {
    const params = buildListParams(query, filter);
    return this.api.get<PageResponse<AccountResponse>>(`${environment.apiUrl}/accounts`, { params });
  }

  get(id: number): Observable<AccountResponse> {
    return this.api.get<AccountResponse>(`${environment.apiUrl}/accounts/${id}`);
  }

  /** Accounts not yet linked to any Employee — for the "link existing account" flow on the Employee screen. */
  listUnassigned(): Observable<AccountResponse[]> {
    return this.api.get<AccountResponse[]>(`${environment.apiUrl}/accounts/unassigned`);
  }

  create(request: AccountCreateRequest): Observable<AccountResponse> {
    return this.api.post<AccountResponse>(`${environment.apiUrl}/accounts`, request);
  }

  update(id: number, request: AccountUpdateRequest): Observable<AccountResponse> {
    return this.api.put<AccountResponse>(`${environment.apiUrl}/accounts/${id}`, request);
  }

  changePassword(id: number, newPassword: string): Observable<void> {
    return this.api.patch<void>(`${environment.apiUrl}/accounts/${id}/password`, { newPassword });
  }

  setActive(id: number, active: boolean): Observable<AccountResponse> {
    return this.api.patch<AccountResponse>(`${environment.apiUrl}/accounts/${id}/active`, { active });
  }

  /** Options list for dropdowns (e.g. the branch-manager picker on the Branch screen). Both admin roles are eligible. */
  listManagerOptions(): Observable<AccountResponse[]> {
    return forkJoin([
      this.list({ page: 0, size: 100 }, { role: Role.ADMIN_TONG }),
      this.list({ page: 0, size: 100 }, { role: Role.ADMIN_CAP_1 }),
    ]).pipe(map(([tong, cap1]) => [...tong.content, ...cap1.content]));
  }
}

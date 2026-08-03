import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  ContractCreateRequest,
  ContractDetailResponse,
  ContractEndDateRequest,
  ContractResponse,
  ContractTenantRequest,
} from '../models/contract.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class ContractService {
  private readonly api = inject(ApiService);

  listByRoom(roomId: number): Observable<ContractResponse[]> {
    return this.api.get<ContractResponse[]>(`${environment.apiUrl}/rooms/${roomId}/contracts`);
  }

  get(id: number): Observable<ContractDetailResponse> {
    return this.api.get<ContractDetailResponse>(`${environment.apiUrl}/contracts/${id}`);
  }

  create(roomId: number, request: ContractCreateRequest): Observable<ContractDetailResponse> {
    return this.api.post<ContractDetailResponse>(`${environment.apiUrl}/rooms/${roomId}/contracts`, request);
  }

  updateEndDate(id: number, request: ContractEndDateRequest): Observable<ContractDetailResponse> {
    return this.api.put<ContractDetailResponse>(`${environment.apiUrl}/contracts/${id}/end-date`, request);
  }

  addTenant(id: number, request: ContractTenantRequest): Observable<ContractDetailResponse> {
    return this.api.post<ContractDetailResponse>(`${environment.apiUrl}/contracts/${id}/tenants`, request);
  }

  removeTenant(id: number, tenantId: number): Observable<ContractDetailResponse> {
    return this.api.delete<ContractDetailResponse>(`${environment.apiUrl}/contracts/${id}/tenants/${tenantId}`);
  }
}

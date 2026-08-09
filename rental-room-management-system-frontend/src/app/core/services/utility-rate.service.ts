import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { UtilityRateRequest, UtilityRateResponse } from '../models/utility-rate.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class UtilityRateService {
  private readonly api = inject(ApiService);

  list(branchId: number): Observable<UtilityRateResponse[]> {
    return this.api.get<UtilityRateResponse[]>(`${environment.apiUrl}/branches/${branchId}/utility-rates`);
  }

  create(branchId: number, request: UtilityRateRequest): Observable<UtilityRateResponse> {
    return this.api.post<UtilityRateResponse>(`${environment.apiUrl}/branches/${branchId}/utility-rates`, request);
  }
}

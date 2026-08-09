import { HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MeterReadingGridRowResponse, MeterReadingRequest, MeterReadingResponse } from '../models/meter-reading.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class MeterReadingService {
  private readonly api = inject(ApiService);

  listGrid(branchId: number, billYear: number, billMonth: number): Observable<MeterReadingGridRowResponse[]> {
    const params = new HttpParams().set('billYear', billYear).set('billMonth', billMonth);
    return this.api.get<MeterReadingGridRowResponse[]>(`${environment.apiUrl}/branches/${branchId}/meter-readings`, {
      params,
    });
  }

  upsert(roomId: number, request: MeterReadingRequest): Observable<MeterReadingResponse> {
    return this.api.put<MeterReadingResponse>(`${environment.apiUrl}/rooms/${roomId}/meter-readings`, request);
  }
}

import { HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DashboardQuery, DashboardResponse } from '../models/dashboard.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly api = inject(ApiService);

  get(query: DashboardQuery): Observable<DashboardResponse> {
    let params = new HttpParams()
      .set('fromYear', query.fromYear)
      .set('fromMonth', query.fromMonth)
      .set('toYear', query.toYear)
      .set('toMonth', query.toMonth);
    if (query.branchId != null) {
      params = params.set('branchId', query.branchId);
    }
    return this.api.get<DashboardResponse>(`${environment.apiUrl}/dashboard`, { params });
  }
}

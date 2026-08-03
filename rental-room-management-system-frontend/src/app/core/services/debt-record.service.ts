import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DebtStatus } from '../enums/debt-status.enum';
import { ListQuery, PageResponse } from '../models/api-response.model';
import { DebtRecordResponse } from '../models/debt-record.model';
import { buildListParams } from '../utils/http-params.util';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class DebtRecordService {
  private readonly api = inject(ApiService);

  list(query: ListQuery, status?: DebtStatus | null): Observable<PageResponse<DebtRecordResponse>> {
    const params = buildListParams(query, { status });
    return this.api.get<PageResponse<DebtRecordResponse>>(`${environment.apiUrl}/debt-records`, { params });
  }

  collect(id: number, collectedAmount: number): Observable<DebtRecordResponse> {
    return this.api.patch<DebtRecordResponse>(`${environment.apiUrl}/debt-records/${id}/collect`, { collectedAmount });
  }
}

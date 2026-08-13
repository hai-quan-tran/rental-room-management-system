import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ListQuery, PageResponse } from '../models/api-response.model';
import { EmployeeCreateRequest, EmployeeResponse, EmployeeUpdateRequest } from '../models/employee.model';
import { buildListParams } from '../utils/http-params.util';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly api = inject(ApiService);

  list(query: ListQuery, search?: string | null): Observable<PageResponse<EmployeeResponse>> {
    const params = buildListParams(query, { search });
    return this.api.get<PageResponse<EmployeeResponse>>(`${environment.apiUrl}/employees`, { params });
  }

  get(id: number): Observable<EmployeeResponse> {
    return this.api.get<EmployeeResponse>(`${environment.apiUrl}/employees/${id}`);
  }

  create(request: EmployeeCreateRequest): Observable<EmployeeResponse> {
    return this.api.post<EmployeeResponse>(`${environment.apiUrl}/employees`, request);
  }

  update(id: number, request: EmployeeUpdateRequest): Observable<EmployeeResponse> {
    return this.api.put<EmployeeResponse>(`${environment.apiUrl}/employees/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`${environment.apiUrl}/employees/${id}`);
  }
}

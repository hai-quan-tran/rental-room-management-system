import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CheckoutRequest, CheckoutResponse } from '../models/checkout.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly api = inject(ApiService);

  get(contractId: number): Observable<CheckoutResponse> {
    return this.api.get<CheckoutResponse>(`${environment.apiUrl}/contracts/${contractId}/checkout`);
  }

  checkout(contractId: number, request: CheckoutRequest): Observable<CheckoutResponse> {
    return this.api.post<CheckoutResponse>(`${environment.apiUrl}/contracts/${contractId}/checkout`, request);
  }
}

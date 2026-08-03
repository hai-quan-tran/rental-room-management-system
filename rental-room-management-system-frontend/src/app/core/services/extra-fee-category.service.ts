import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ExtraFeeCategoryResponse } from '../models/extra-fee-category.model';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class ExtraFeeCategoryService {
  private readonly api = inject(ApiService);

  listAll(): Observable<ExtraFeeCategoryResponse[]> {
    return this.api.get<ExtraFeeCategoryResponse[]>(`${environment.apiUrl}/extra-fee-categories`);
  }
}

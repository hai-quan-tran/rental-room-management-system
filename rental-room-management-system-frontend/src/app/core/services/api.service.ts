import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, finalize, map } from 'rxjs';

import { ApiResponse } from '../models/api-response.model';
import { LoadingService } from './loading.service';

export interface ApiRequestOptions {
  params?: HttpParams | Record<string, string | number | boolean>;
}

/**
 * Single entry point for every HTTP call in the app: unwraps the backend's
 * `ApiResponse<T>` envelope down to `T`, and tracks the shared `LoadingService`
 * counter automatically — callers never toggle a loading flag themselves.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly loading = inject(LoadingService);

  get<T>(url: string, options?: ApiRequestOptions): Observable<T> {
    return this.track(this.http.get<ApiResponse<T>>(url, options));
  }

  post<T>(url: string, body: unknown, options?: ApiRequestOptions): Observable<T> {
    return this.track(this.http.post<ApiResponse<T>>(url, body, options));
  }

  put<T>(url: string, body: unknown, options?: ApiRequestOptions): Observable<T> {
    return this.track(this.http.put<ApiResponse<T>>(url, body, options));
  }

  patch<T>(url: string, body: unknown, options?: ApiRequestOptions): Observable<T> {
    return this.track(this.http.patch<ApiResponse<T>>(url, body, options));
  }

  delete<T>(url: string, options?: ApiRequestOptions): Observable<T> {
    return this.track(this.http.delete<ApiResponse<T>>(url, options));
  }

  private track<T>(source: Observable<ApiResponse<T>>): Observable<T> {
    this.loading.start();
    return source.pipe(
      map((res) => res.data),
      finalize(() => this.loading.stop()),
    );
  }
}

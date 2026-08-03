import { HttpParams } from '@angular/common/http';

import { ListQuery } from '../models/api-response.model';

/**
 * Builds Spring `Pageable`-compatible query params (`page`, `size`, `sort`)
 * plus any extra filter params, skipping null/undefined/empty-string values.
 */
export function buildListParams(query: ListQuery, extra?: object): HttpParams {
  let params = new HttpParams().set('page', query.page).set('size', query.size);

  if (query.sortField) {
    params = params.set('sort', `${query.sortField},${query.sortOrder ?? 'asc'}`);
  }

  for (const [key, value] of Object.entries(extra ?? {})) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }

  return params;
}

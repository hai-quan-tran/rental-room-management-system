import { TableLazyLoadEvent } from 'primeng/table';

import { ListQuery } from '../models/api-response.model';

/** Converts a PrimeNG `p-table` lazy-load event into Spring `Pageable`-compatible query params. */
export function toListQuery(event: TableLazyLoadEvent | undefined, defaultSize: number): ListQuery {
  const size = event?.rows ?? defaultSize;
  const first = event?.first ?? 0;
  return {
    page: Math.floor(first / size),
    size,
    sortField: (event?.sortField as string) || undefined,
    sortOrder: event?.sortOrder === -1 ? 'desc' : 'asc',
  };
}

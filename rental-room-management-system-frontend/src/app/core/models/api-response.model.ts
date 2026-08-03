export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Query params shared by every server-side-paginated list endpoint (Spring `Pageable`). */
export interface ListQuery {
  page: number;
  size: number;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

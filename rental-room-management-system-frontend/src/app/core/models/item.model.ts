export interface ItemResponse {
  id: number;
  branchId: number;
  branchName: string;
  name: string;
  price: number;
  quantityAvailable: number;
  defaultQuantityPerRoom: number;
  createdAt: string;
  updatedAt: string;
}

export interface ItemRequest {
  name: string;
  price: number;
  quantityAvailable: number;
  defaultQuantityPerRoom: number;
}

/** Lightweight lookup for the Room Type handover-item picker (auto-fills default quantity, validates stock). */
export interface ItemOption {
  id: number;
  name: string;
  price: number;
  quantityAvailable: number;
  defaultQuantityPerRoom: number;
}

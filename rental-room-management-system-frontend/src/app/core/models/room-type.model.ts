export interface HandoverItemResponse {
  id: number;
  itemId: number;
  itemName: string;
  itemPrice: number;
  quantity: number;
  note: string | null;
}

export interface HandoverItemRequest {
  itemId: number;
  quantity: number;
  note: string | null;
}

export interface RoomTypeResponse {
  id: number;
  branchId: number;
  branchName: string;
  name: string;
  area: string | null;
  description: string | null;
  handoverItems: HandoverItemResponse[];
}

export interface RoomTypeRequest {
  name: string;
  area: string | null;
  description: string | null;
}

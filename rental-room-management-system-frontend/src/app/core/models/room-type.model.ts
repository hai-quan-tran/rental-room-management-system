export interface HandoverItemResponse {
  id: number;
  itemName: string;
  quantity: number;
  note: string | null;
}

export interface HandoverItemRequest {
  itemName: string;
  quantity: number;
  note: string | null;
}

export interface RoomTypeResponse {
  id: number;
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

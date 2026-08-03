export interface BranchResponse {
  id: number;
  name: string;
  address: string;
  managerAccountId: number | null;
  managerFullName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BranchRequest {
  name: string;
  address: string;
  managerAccountId: number | null;
}

export interface RoomTypeSummaryResponse {
  roomTypeId: number;
  roomTypeName: string;
  roomCount: number;
  emptyRoomCount: number;
  occupiedRoomCount: number;
}

export interface BranchDetailResponse {
  branch: BranchResponse;
  totalRooms: number;
  roomTypeSummaries: RoomTypeSummaryResponse[];
}

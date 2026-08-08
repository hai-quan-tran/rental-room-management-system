export interface RoomStatusChartResponse {
  emptyCount: number;
  occupiedCount: number;
}

export interface MoveInOutPointResponse {
  year: number;
  month: number;
  moveInCount: number;
  moveOutCount: number;
}

export interface RevenuePointResponse {
  year: number;
  month: number;
  totalAmount: number;
}

export interface OccupantsByBranchResponse {
  branchId: number;
  branchName: string;
  occupantCount: number;
}

export interface MissingInvoiceRoomResponse {
  roomId: number;
  roomCode: string;
  branchId: number;
  branchName: string;
  missingYear: number;
  missingMonth: number;
}

export interface UnpaidInvoiceRoomResponse {
  roomId: number;
  roomCode: string;
  branchId: number;
  branchName: string;
  billYear: number;
  billMonth: number;
  totalAmount: number;
  paidAmount: number;
  remainingAmount: number;
}

export interface DashboardResponse {
  roomStatus: RoomStatusChartResponse;
  moveInOut: MoveInOutPointResponse[];
  revenue: RevenuePointResponse[];
  occupantsByBranch: OccupantsByBranchResponse[];
  missingInvoiceRooms: MissingInvoiceRoomResponse[];
  unpaidInvoiceRooms: UnpaidInvoiceRoomResponse[];
}

export interface DashboardQuery {
  branchId: number | null;
  fromYear: number;
  fromMonth: number;
  toYear: number;
  toMonth: number;
}

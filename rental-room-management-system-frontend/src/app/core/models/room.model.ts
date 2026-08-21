import { RoomStatus } from '../enums/room-status.enum';
import { HandoverItemResponse } from './room-type.model';

export interface RoomResponse {
  id: number;
  branchId: number;
  branchName: string;
  bankBin: string | null;
  bankAccountNumber: string | null;
  bankAccountName: string | null;
  roomCode: string;
  roomTypeId: number;
  roomTypeName: string;
  monthlyRent: number;
  wifiFee: number;
  parkingFee: number;
  status: RoomStatus;
  createdAt: string;
  updatedAt: string;
}

export interface RoomTypeOption {
  id: number;
  name: string;
}

export interface RoomRequest {
  roomCode: string;
  roomTypeId: number;
  monthlyRent: number;
  wifiFee: number;
  parkingFee: number;
}

export interface RoomDetailResponse {
  room: RoomResponse;
  handoverItems: HandoverItemResponse[];
}

export interface BranchOption {
  id: number;
  name: string;
}

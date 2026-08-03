import { DebtStatus } from '../enums/debt-status.enum';

export interface DebtRecordResponse {
  id: number;
  contractId: number;
  checklistId: number | null;
  roomId: number;
  roomCode: string;
  branchName: string;
  amount: number;
  reason: string;
  status: DebtStatus;
  collectedAmount: number;
  note: string | null;
  createdAt: string;
}

import { DebtStatus } from '../../core/enums/debt-status.enum';
import { PaymentStatus } from '../../core/enums/payment-status.enum';
import { RoomStatus } from '../../core/enums/room-status.enum';

/** Maps a `MonthlyBill.paymentStatus` to the `p-tag` severity used to color it. */
export function paymentStatusSeverity(status: PaymentStatus): 'success' | 'warn' | 'danger' {
  if (status === PaymentStatus.DA_THANH_TOAN) {
    return 'success';
  }
  return status === PaymentStatus.THANH_TOAN_MOT_PHAN ? 'warn' : 'danger';
}

/** Maps a `Room.status` to the `p-tag` severity used to color it. */
export function roomStatusSeverity(status: RoomStatus | null | undefined): 'info' | 'success' {
  return status === RoomStatus.TRONG ? 'info' : 'success';
}

/** Maps a `DebtRecord.status` to the `p-tag` severity used to color it. */
export function debtStatusSeverity(status: DebtStatus): 'success' | 'warn' {
  return status === DebtStatus.DA_THU ? 'success' : 'warn';
}

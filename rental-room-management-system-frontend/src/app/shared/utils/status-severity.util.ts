import { DebtStatus } from '../../core/enums/debt-status.enum';
import { PaymentStatus } from '../../core/enums/payment-status.enum';
import { RoomStatus } from '../../core/enums/room-status.enum';

/** Maps a `MonthlyBill.paymentStatus` to the `p-tag` severity used to color it. */
export function paymentStatusSeverity(status: PaymentStatus): 'secondary' | 'success' | 'warn' | 'danger' {
  if (status === PaymentStatus.CHUA_XAC_NHAN) {
    return 'secondary';
  }
  if (status === PaymentStatus.DA_THANH_TOAN) {
    return 'success';
  }
  return status === PaymentStatus.THANH_TOAN_MOT_PHAN ? 'warn' : 'danger';
}

/** A bill's extra-fee items / meter readings may only be edited while it's still unconfirmed. */
export function canEditBillItems(status: PaymentStatus): boolean {
  return status === PaymentStatus.CHUA_XAC_NHAN;
}

/** Payments may only be recorded once a bill has been confirmed and isn't fully paid yet. */
export function canRecordPayment(status: PaymentStatus): boolean {
  return status !== PaymentStatus.CHUA_XAC_NHAN && status !== PaymentStatus.DA_THANH_TOAN;
}

/** Maps a `Room.status` to the `p-tag` severity used to color it. */
export function roomStatusSeverity(status: RoomStatus | null | undefined): 'info' | 'success' {
  return status === RoomStatus.TRONG ? 'info' : 'success';
}

/** Maps a `DebtRecord.status` to the `p-tag` severity used to color it. */
export function debtStatusSeverity(status: DebtStatus): 'success' | 'warn' {
  return status === DebtStatus.DA_THU ? 'success' : 'warn';
}

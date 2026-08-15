import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { PaymentStatus } from '../../core/enums/payment-status.enum';
import { Role } from '../../core/enums/role.enum';
import {
  BulkBillConfirmResult,
  BulkMonthlyBillCreateResult,
  ExtraFeeItemResponse,
  MonthlyBillDetailResponse,
  MonthlyBillListItem,
} from '../../core/models/billing.model';
import { ExtraFeeCategoryResponse } from '../../core/models/extra-fee-category.model';
import { BranchOption } from '../../core/models/room.model';
import { AuthService } from '../../core/services/auth.service';
import { BillingService } from '../../core/services/billing.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ExtraFeeCategoryService } from '../../core/services/extra-fee-category.service';
import { LoadingService } from '../../core/services/loading.service';
import { NotificationService } from '../../core/services/notification.service';
import { RoomService } from '../../core/services/room.service';
import { dateToYearMonth, toIsoDate } from '../../core/utils/date.util';
import { toListQuery } from '../../core/utils/list-query.util';
import { displayOr, roomBranchLabel } from '../../shared/utils/display.util';
import {
  canEditBillItems,
  canRecordPayment,
  paymentStatusSeverity,
} from '../../shared/utils/status-severity.util';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-monthly-bills-page',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    TranslatePipe,
    TableModule,
    TagModule,
    SelectModule,
    ButtonModule,
    DialogModule,
    InputNumberModule,
    InputTextModule,
    DatePickerModule,
  ],
  templateUrl: './monthly-bills-page.html',
})
export class MonthlyBillsPage implements OnInit {
  private readonly billingService = inject(BillingService);
  private readonly roomService = inject(RoomService);
  private readonly extraFeeCategoryService = inject(ExtraFeeCategoryService);
  private readonly authService = inject(AuthService);
  private readonly translate = inject(TranslateService);
  private readonly loadingService = inject(LoadingService);
  private readonly notification = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);

  readonly loading = this.loadingService.isLoading;
  readonly isAdminTong = this.authService.hasRole(Role.ADMIN_TONG);
  readonly paymentStatusSeverity = paymentStatusSeverity;
  readonly canEditBillItems = canEditBillItems;
  readonly canRecordPayment = canRecordPayment;
  readonly roomBranchLabel = roomBranchLabel;
  readonly displayOr = displayOr;

  readonly rows = signal<MonthlyBillListItem[]>([]);
  readonly totalRecords = signal(0);

  readonly billPeriod = signal<Date>(new Date());
  readonly branchFilter = signal<number | null>(null);
  readonly branchOptions = signal<BranchOption[]>([]);
  readonly extraFeeCategories = signal<ExtraFeeCategoryResponse[]>([]);

  readonly bulkResultDialogVisible = signal(false);
  readonly bulkResult = signal<BulkMonthlyBillCreateResult | null>(null);

  readonly selectedBills = signal<MonthlyBillListItem[]>([]);
  readonly confirmBulkResultDialogVisible = signal(false);
  readonly confirmBulkResult = signal<BulkBillConfirmResult | null>(null);

  readonly detailDialogVisible = signal(false);
  readonly detailBill = signal<MonthlyBillListItem | null>(null);
  readonly detailData = signal<MonthlyBillDetailResponse | null>(null);

  readonly newFeeCategoryId = signal<number | null>(null);
  readonly newFeeAmount = signal<number | null>(null);
  readonly newFeeNote = signal('');

  readonly newPaymentAmount = signal<number | null>(null);
  readonly newPaymentDate = signal<Date | null>(new Date());
  readonly newPaymentMethod = signal('');
  readonly newPaymentNote = signal('');

  private lastEvent?: TableLazyLoadEvent;

  ngOnInit(): void {
    if (this.isAdminTong) {
      this.roomService.branchOptions().subscribe({
        next: (options) => this.branchOptions.set(options),
        error: () => {},
      });
    }
    this.extraFeeCategoryService.listAll().subscribe({
      next: (categories) => this.extraFeeCategories.set(categories),
      error: () => {},
    });
    this.load();
  }

  load(event?: TableLazyLoadEvent): void {
    this.lastEvent = event ?? this.lastEvent;
    const { year: billYear, month: billMonth } = dateToYearMonth(this.billPeriod());

    this.billingService
      .listAll(toListQuery(this.lastEvent, PAGE_SIZE), {
        billYear,
        billMonth,
        branchId: this.isAdminTong ? this.branchFilter() : null,
      })
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.totalRecords.set(page.totalElements);
        },
        error: () => {},
      });
  }

  onFilterChange(): void {
    this.selectedBills.set([]);
    this.load({ first: 0, rows: PAGE_SIZE });
  }

  isUnconfirmed(bill: MonthlyBillListItem): boolean {
    return bill.paymentStatus === PaymentStatus.CHUA_XAC_NHAN;
  }

  confirmSelectedBulk(): void {
    const ids = this.selectedBills()
      .filter((bill) => this.isUnconfirmed(bill))
      .map((bill) => bill.id);
    if (ids.length === 0) {
      return;
    }
    this.confirmService.confirm(
      `${this.translate.instant('MONTHLY_BILLS.CONFIRM_BULK_CONFIRM_PREFIX')} ${ids.length} ${this.translate.instant('MONTHLY_BILLS.CONFIRM_BULK_CONFIRM_SUFFIX')}`,
      () => {
        this.billingService.confirmBulk(ids).subscribe({
          next: (result) => {
            this.confirmBulkResult.set(result);
            this.confirmBulkResultDialogVisible.set(true);
            this.selectedBills.set([]);
            this.load();
          },
          error: () => {},
        });
      },
    );
  }

  confirmBulkCreate(): void {
    this.confirmService.confirm(this.translate.instant('MONTHLY_BILLS.BULK_CREATE_CONFIRM'), () =>
      this.bulkCreate(),
    );
  }

  private bulkCreate(): void {
    const { year: billYear, month: billMonth } = dateToYearMonth(this.billPeriod());
    this.billingService
      .bulkCreate({
        billMonth,
        billYear,
        branchId: this.isAdminTong ? this.branchFilter() : null,
      })
      .subscribe({
        next: (result) => {
          this.bulkResult.set(result);
          this.bulkResultDialogVisible.set(true);
          this.load();
        },
        error: () => {},
      });
  }

  openDetail(bill: MonthlyBillListItem): void {
    this.detailBill.set(bill);
    this.newFeeCategoryId.set(null);
    this.newFeeAmount.set(null);
    this.newFeeNote.set('');
    this.newPaymentAmount.set(null);
    this.newPaymentDate.set(new Date());
    this.newPaymentMethod.set('');
    this.newPaymentNote.set('');
    this.billingService.get(bill.id).subscribe({
      next: (detail) => {
        this.detailData.set(detail);
        this.detailDialogVisible.set(true);
      },
      error: () => {},
    });
  }

  addExtraFeeItem(): void {
    const bill = this.detailBill();
    if (!bill || !this.newFeeCategoryId() || !this.newFeeAmount()) {
      return;
    }
    this.billingService
      .addExtraFeeItem(bill.id, {
        extraFeeCategoryId: this.newFeeCategoryId()!,
        amount: this.newFeeAmount()!,
        note: this.newFeeNote() || null,
      })
      .subscribe({
        next: () => {
          this.newFeeCategoryId.set(null);
          this.newFeeAmount.set(null);
          this.newFeeNote.set('');
          this.refreshAfterBillMutation(bill.id);
        },
        error: () => {},
      });
  }

  deleteExtraFeeItem(item: ExtraFeeItemResponse): void {
    const bill = this.detailBill();
    if (!bill) {
      return;
    }
    this.confirmService.confirm(this.translate.instant('BILLING.DELETE_FEE_CONFIRM'), () => {
      this.billingService.deleteExtraFeeItem(bill.id, item.id).subscribe({
        next: () => this.refreshAfterBillMutation(bill.id),
        error: () => {},
      });
    });
  }

  recordPayment(): void {
    const bill = this.detailBill();
    const detail = this.detailData();
    const paymentDate = this.newPaymentDate();
    if (!bill || !detail || !this.newPaymentAmount() || !paymentDate) {
      return;
    }
    if (this.newPaymentAmount()! > detail.bill.remainingAmount) {
      return;
    }
    this.billingService
      .recordPayment(bill.id, {
        amount: this.newPaymentAmount()!,
        paymentDate: toIsoDate(paymentDate),
        method: this.newPaymentMethod() || null,
        note: this.newPaymentNote() || null,
      })
      .subscribe({
        next: () => {
          this.notification.success(this.translate.instant('BILLING.PAYMENT_SUCCESS'));
          this.detailDialogVisible.set(false);
          this.refreshAfterBillMutation(bill.id);
        },
        error: () => {},
      });
  }

  /** Keeps the main table, the open detail dialog (if still open), and the bulk-result dialog's row all consistent after a mutation. */
  private refreshAfterBillMutation(billId: number): void {
    this.load();
    this.billingService.get(billId).subscribe({
      next: (detail) => {
        if (this.detailDialogVisible()) {
          this.detailData.set(detail);
        }
        const result = this.bulkResult();
        if (result) {
          this.bulkResult.set({
            ...result,
            createdBills: result.createdBills.map((row) =>
              row.id === billId
                ? {
                    ...row,
                    totalExtraFee: detail.bill.totalExtraFee,
                    totalAmount: detail.bill.totalAmount,
                    paidAmount: detail.bill.paidAmount,
                    remainingAmount: detail.bill.remainingAmount,
                    paymentStatus: detail.bill.paymentStatus,
                  }
                : row,
            ),
          });
        }
      },
      error: () => {},
    });
  }
}

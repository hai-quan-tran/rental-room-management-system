import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AutoCompleteCompleteEvent, AutoCompleteModule } from 'primeng/autocomplete';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';

import { Role } from '../../../core/enums/role.enum';
import { RoomStatus } from '../../../core/enums/room-status.enum';
import { ContractStatus } from '../../../core/enums/contract-status.enum';
import { PaymentStatus } from '../../../core/enums/payment-status.enum';
import { HandoverItemResponse } from '../../../core/models/room-type.model';
import { RoomTypeOption } from '../../../core/models/room.model';
import {
  ContractDetailResponse,
  ContractResponse,
  TenantInContractResponse,
} from '../../../core/models/contract.model';
import { CheckoutResponse } from '../../../core/models/checkout.model';
import {
  ExtraFeeItemResponse,
  MonthlyBillDetailResponse,
  MonthlyBillResponse,
} from '../../../core/models/billing.model';
import { ExtraFeeCategoryResponse } from '../../../core/models/extra-fee-category.model';
import { TenantResponse } from '../../../core/models/tenant.model';
import { AuthService } from '../../../core/services/auth.service';
import { BillingService } from '../../../core/services/billing.service';
import { CheckoutService } from '../../../core/services/checkout.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ContractService } from '../../../core/services/contract.service';
import { ExtraFeeCategoryService } from '../../../core/services/extra-fee-category.service';
import { LoadingService } from '../../../core/services/loading.service';
import { NotificationService } from '../../../core/services/notification.service';
import { RoomTypeService } from '../../../core/services/room-type.service';
import { RoomService } from '../../../core/services/room.service';
import { TenantService } from '../../../core/services/tenant.service';
import { calculateAge, dateToYearMonth, fromIsoDate, toIsoDate } from '../../../core/utils/date.util';
import { displayOr } from '../../../shared/utils/display.util';
import {
  canEditBillItems,
  canRecordPayment,
  paymentStatusSeverity,
  roomStatusSeverity,
} from '../../../shared/utils/status-severity.util';
import { translatedOptions } from '../../../shared/utils/translated-options';
import { buildVietQrImageUrl, stripDiacritics } from '../../../shared/utils/vietqr.util';

interface NewContractTenantRow {
  tenantId: number;
  fullName: string;
  email: string | null;
  representative: boolean;
}

interface CheckoutItemRow {
  roomTypeHandoverItemId: number;
  itemName: string;
  itemPrice: number;
  totalQuantity: number;
  damagedQuantity: number;
  lostQuantity: number;
  deductionAmount: number;
  note: string | null;
}

@Component({
  selector: 'app-room-detail-page',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    RouterLink,
    TranslatePipe,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
    TagModule,
    TableModule,
    TabsModule,
    DialogModule,
    DatePickerModule,
    AutoCompleteModule,
    TextareaModule,
  ],
  templateUrl: './room-detail-page.html',
})
export class RoomDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly roomService = inject(RoomService);
  private readonly roomTypeService = inject(RoomTypeService);
  private readonly contractService = inject(ContractService);
  private readonly billingService = inject(BillingService);
  private readonly checkoutService = inject(CheckoutService);
  private readonly tenantService = inject(TenantService);
  private readonly extraFeeCategoryService = inject(ExtraFeeCategoryService);
  private readonly translate = inject(TranslateService);
  private readonly notification = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  private readonly loadingService = inject(LoadingService);
  private readonly authService = inject(AuthService);

  readonly loading = this.loadingService.isLoading;
  readonly isAdminTong = this.authService.hasRole(Role.ADMIN_TONG);
  readonly activeTab = signal('0');
  readonly paymentStatusSeverity = paymentStatusSeverity;
  readonly roomStatusSeverity = roomStatusSeverity;
  readonly canEditBillItems = canEditBillItems;
  readonly canRecordPayment = canRecordPayment;
  readonly displayOr = displayOr;

  private roomId: number | null = null;
  readonly isNew = computed(() => this.roomId === null);
  private branchId!: number;
  readonly branchName = signal('');
  readonly bankBin = signal<string | null>(null);
  readonly bankAccountNumber = signal<string | null>(null);
  readonly bankAccountName = signal<string | null>(null);

  // ---- Tab 1: thông tin cơ bản ----
  readonly roomCode = signal('');
  readonly roomTypeId = signal<number | null>(null);
  readonly monthlyRent = signal<number | null>(null);
  readonly wifiFee = signal<number | null>(null);
  readonly parkingFee = signal<number | null>(null);
  readonly status = signal<RoomStatus | null>(null);
  readonly roomTypeOptions = signal<RoomTypeOption[]>([]);
  readonly handoverItems = signal<HandoverItemResponse[]>([]);
  readonly submitted = signal(false);

  // ---- Tab 2: hợp đồng & người thuê ----
  readonly contracts = signal<ContractResponse[]>([]);
  readonly activeContract = computed(
    () => this.contracts().find((c) => c.status === ContractStatus.ACTIVE) ?? null,
  );
  readonly historyContracts = computed(() =>
    this.contracts().filter((c) => c.status === ContractStatus.ENDED),
  );
  readonly activeContractDetail = signal<ContractDetailResponse | null>(null);

  readonly newContractStartDate = signal<Date | null>(null);
  readonly newContractEndDate = signal<Date | null>(null);
  readonly newContractDeposit = signal<number | null>(null);
  readonly newContractRent = signal<number | null>(null);
  readonly newContractTenants = signal<NewContractTenantRow[]>([]);
  readonly newContractSubmitted = signal(false);
  readonly newContractRepresentativeHasEmail = computed(() => {
    const rep = this.newContractTenants().find((t) => t.representative);
    return !rep || !!rep.email;
  });
  readonly newContractTenantQuery = signal('');
  readonly newContractTenantSuggestions = signal<TenantResponse[]>([]);

  readonly addTenantQuery = signal('');
  readonly addTenantSuggestions = signal<TenantResponse[]>([]);

  readonly editEndDate = signal<Date | null>(null);

  readonly historyTenantsDialogVisible = signal(false);
  readonly historyTenants = signal<TenantInContractResponse[]>([]);

  readonly historyBillsDialogVisible = signal(false);
  readonly historyBills = signal<MonthlyBillResponse[]>([]);

  readonly tenantDialogVisible = signal(false);
  readonly tenantDialogTarget = signal<'new-contract' | 'active-contract' | null>(null);
  readonly tenantFullName = signal('');
  readonly tenantDateOfBirth = signal<Date | null>(null);
  readonly tenantIdCard = signal('');
  readonly tenantPhone = signal('');
  readonly tenantEmail = signal('');
  readonly tenantSubmitted = signal(false);
  readonly tenantIsAdult = computed(() => {
    const dob = this.tenantDateOfBirth();
    return dob ? calculateAge(dob) >= 18 : false;
  });

  readonly checklistDialogVisible = signal(false);
  readonly checklistData = signal<CheckoutResponse | null>(null);

  // ---- Tab 3: hóa đơn & công nợ ----
  readonly bills = signal<MonthlyBillResponse[]>([]);
  readonly billDetail = signal<MonthlyBillDetailResponse | null>(null);
  readonly newBillPeriod = signal<Date>(new Date());
  readonly extraFeeCategories = signal<ExtraFeeCategoryResponse[]>([]);
  readonly newFeeCategoryId = signal<number | null>(null);
  readonly newFeeAmount = signal<number | null>(null);
  readonly newFeeNote = signal('');
  readonly newPaymentAmount = signal<number | null>(null);
  readonly newPaymentDate = signal<Date | null>(new Date());
  readonly newPaymentMethod = signal('');
  readonly newPaymentNote = signal('');

  readonly bankQrUrl = computed(() => {
    const detail = this.billDetail();
    if (!detail || detail.bill.remainingAmount <= 0) {
      return null;
    }
    const addInfo = stripDiacritics(`RRMS ${this.roomCode()} T${detail.bill.billMonth}.${detail.bill.billYear}`);
    return buildVietQrImageUrl(
      this.bankBin(),
      this.bankAccountNumber(),
      detail.bill.remainingAmount,
      addInfo,
      this.bankAccountName(),
    );
  });

  readonly paymentStatusOptions = translatedOptions(this.translate, [
    { labelKey: 'BILLING.STATUS_CHUA_XAC_NHAN', value: PaymentStatus.CHUA_XAC_NHAN },
    { labelKey: 'BILLING.STATUS_CHUA_THANH_TOAN', value: PaymentStatus.CHUA_THANH_TOAN },
    { labelKey: 'BILLING.STATUS_THANH_TOAN_MOT_PHAN', value: PaymentStatus.THANH_TOAN_MOT_PHAN },
    { labelKey: 'BILLING.STATUS_DA_THANH_TOAN', value: PaymentStatus.DA_THANH_TOAN },
  ]);

  // ---- Tab 4: trả phòng ----
  readonly checkoutItems = signal<CheckoutItemRow[]>([]);
  readonly checkoutDate = signal<Date | null>(new Date());
  readonly checkoutNote = signal('');
  readonly checkoutSubmitted = signal(false);

  onTabChange(value: string | number | undefined): void {
    this.activeTab.set(value?.toString() ?? '0');
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam || idParam === 'new') {
      const branchIdParam = this.route.snapshot.queryParamMap.get('branchId');
      this.branchId = Number(branchIdParam);
      this.status.set(RoomStatus.TRONG);
      this.loadRoomTypeOptions();
      return;
    }

    this.roomId = Number(idParam);
    this.loadRoom();
    this.loadContracts();
  }

  private loadRoomTypeOptions(): void {
    this.roomTypeService.listAll(this.branchId).subscribe({
      next: (types) => this.roomTypeOptions.set(types),
      error: () => {},
    });
  }

  private loadRoom(): void {
    this.roomService.get(this.roomId!).subscribe({
      next: (detail) => {
        this.roomCode.set(detail.room.roomCode);
        this.roomTypeId.set(detail.room.roomTypeId);
        this.monthlyRent.set(detail.room.monthlyRent);
        this.wifiFee.set(detail.room.wifiFee);
        this.parkingFee.set(detail.room.parkingFee);
        this.status.set(detail.room.status);
        this.branchId = detail.room.branchId;
        this.branchName.set(detail.room.branchName);
        this.bankBin.set(detail.room.bankBin);
        this.bankAccountNumber.set(detail.room.bankAccountNumber);
        this.bankAccountName.set(detail.room.bankAccountName);
        this.handoverItems.set(detail.handoverItems);
        this.buildCheckoutItems();
        this.loadRoomTypeOptions();
        if (this.newContractDeposit() === null) {
          this.newContractDeposit.set(detail.room.monthlyRent);
        }
      },
      error: () => {},
    });
  }

  private loadContracts(): void {
    this.contractService.listByRoom(this.roomId!).subscribe({
      next: (contracts) => {
        this.contracts.set(contracts);
        const active = contracts.find((c) => c.status === ContractStatus.ACTIVE);
        if (active) {
          this.loadActiveContractDetail(active.id);
          this.loadBills(active.id);
        }
      },
      error: () => {},
    });
  }

  private loadActiveContractDetail(contractId: number): void {
    this.contractService.get(contractId).subscribe({
      next: (detail) => {
        this.activeContractDetail.set(detail);
        this.editEndDate.set(detail.contract.endDate ? fromIsoDate(detail.contract.endDate) : null);
      },
      error: () => {},
    });
  }

  saveEndDate(): void {
    const contract = this.activeContract();
    if (!contract) {
      return;
    }
    const endDate = this.editEndDate();
    this.confirmService.confirm(this.translate.instant('COMMON.SAVE_CONFIRM'), () => {
      this.contractService
        .updateEndDate(contract.id, { endDate: endDate ? toIsoDate(endDate) : null })
        .subscribe({
          next: (detail) => {
            this.activeContractDetail.set(detail);
            this.loadContracts();
            this.notification.success(this.translate.instant('CONTRACT.END_DATE_UPDATE_SUCCESS'));
          },
          error: () => {},
        });
    });
  }

  // ---- Tab 1 actions ----
  saveRoom(): void {
    this.submitted.set(true);
    if (!this.roomCode() || !this.roomTypeId() || !this.monthlyRent()) {
      return;
    }

    const request = {
      roomCode: this.roomCode(),
      roomTypeId: this.roomTypeId()!,
      monthlyRent: this.monthlyRent()!,
      wifiFee: this.wifiFee() ?? 0,
      parkingFee: this.parkingFee() ?? 0,
    };
    this.confirmService.confirm(this.translate.instant('COMMON.SAVE_CONFIRM'), () => {
      const save$ = this.isNew()
        ? this.roomService.create(this.branchId, request)
        : this.roomService.update(this.roomId!, request);

      save$.subscribe({
        next: (room) => {
          this.notification.success(
            this.translate.instant(this.isNew() ? 'ROOM.CREATE_SUCCESS' : 'ROOM.UPDATE_SUCCESS'),
          );
          if (this.isNew()) {
            this.router.navigate(['/rooms', room.id]);
          } else {
            this.loadRoom();
          }
        },
        error: () => {},
      });
    });
  }

  deleteRoom(): void {
    if (!this.roomId) {
      return;
    }
    this.confirmService.confirm(this.translate.instant('ROOM.DELETE_CONFIRM'), () => {
      this.roomService.delete(this.roomId!).subscribe({
        next: () => {
          this.notification.success(this.translate.instant('ROOM.DELETE_SUCCESS'));
          this.router.navigate(['/rooms']);
        },
        error: () => {},
      });
    });
  }

  private buildCheckoutItems(): void {
    this.checkoutItems.set(
      this.handoverItems().map((item) => ({
        roomTypeHandoverItemId: item.id,
        itemName: item.itemName,
        itemPrice: item.itemPrice,
        totalQuantity: item.quantity,
        damagedQuantity: 0,
        lostQuantity: 0,
        deductionAmount: 0,
        note: null,
      })),
    );
  }

  intactQuantity(row: CheckoutItemRow): number {
    return row.totalQuantity - row.damagedQuantity - row.lostQuantity;
  }

  checkoutQuantityExceedsTotal(row: CheckoutItemRow): boolean {
    return row.damagedQuantity + row.lostQuantity > row.totalQuantity;
  }

  hasCheckoutQuantityError(): boolean {
    return this.checkoutItems().some((row) => this.checkoutQuantityExceedsTotal(row));
  }

  // ---- Tab 2: hợp đồng & người thuê ----
  searchTenants(
    event: AutoCompleteCompleteEvent,
    target: 'new-contract' | 'active-contract',
  ): void {
    this.tenantService.list({ page: 0, size: 10 }, event.query).subscribe({
      next: (page) => {
        if (target === 'new-contract') {
          this.newContractTenantSuggestions.set(page.content);
        } else {
          this.addTenantSuggestions.set(page.content);
        }
      },
      error: () => {},
    });
  }

  addTenantToNewContract(tenant: TenantResponse): void {
    if (this.newContractTenants().some((t) => t.tenantId === tenant.id)) {
      return;
    }
    const isFirst = this.newContractTenants().length === 0;
    this.newContractTenants.update((rows) => [
      ...rows,
      { tenantId: tenant.id, fullName: tenant.fullName, email: tenant.email, representative: isFirst },
    ]);
    this.newContractTenantQuery.set('');
  }

  removeNewContractTenant(tenantId: number): void {
    this.newContractTenants.update((rows) => rows.filter((t) => t.tenantId !== tenantId));
  }

  setNewContractRepresentative(tenantId: number): void {
    this.newContractTenants.update((rows) =>
      rows.map((t) => ({ ...t, representative: t.tenantId === tenantId })),
    );
  }

  createContract(): void {
    this.newContractSubmitted.set(true);
    const startDate = this.newContractStartDate();
    if (
      !startDate ||
      !this.newContractDeposit() ||
      this.newContractTenants().length === 0 ||
      !this.newContractRepresentativeHasEmail()
    ) {
      return;
    }

    const endDate = this.newContractEndDate();
    this.contractService
      .create(this.roomId!, {
        startDate: toIsoDate(startDate),
        endDate: endDate ? toIsoDate(endDate) : null,
        depositAmount: this.newContractDeposit()!,
        monthlyRent: this.newContractRent(),
        tenants: this.newContractTenants().map((t) => ({
          tenantId: t.tenantId,
          representative: t.representative,
        })),
      })
      .subscribe({
        next: () => {
          this.notification.success(this.translate.instant('CONTRACT.CREATE_SUCCESS'));
          this.newContractStartDate.set(null);
          this.newContractEndDate.set(null);
          this.newContractDeposit.set(this.monthlyRent());
          this.newContractRent.set(null);
          this.newContractTenants.set([]);
          this.newContractSubmitted.set(false);
          this.loadRoom();
          this.loadContracts();
        },
        error: () => {},
      });
  }

  addExistingTenantToActiveContract(tenant: TenantResponse): void {
    const contract = this.activeContract();
    if (!contract) {
      return;
    }
    this.contractService
      .addTenant(contract.id, { tenantId: tenant.id, representative: false })
      .subscribe({
        next: (detail) => {
          this.activeContractDetail.set(detail);
          this.addTenantQuery.set('');
          this.notification.success(this.translate.instant('CONTRACT.ADD_TENANT_SUCCESS'));
        },
        error: () => {},
      });
  }

  removeTenantFromActiveContract(tenantId: number): void {
    const contract = this.activeContract();
    if (!contract) {
      return;
    }
    this.confirmService.confirm(this.translate.instant('CONTRACT.REMOVE_TENANT_CONFIRM'), () => {
      this.contractService.removeTenant(contract.id, tenantId).subscribe({
        next: (detail) => this.activeContractDetail.set(detail),
        error: () => {},
      });
    });
  }

  openCreateTenantDialog(target: 'new-contract' | 'active-contract'): void {
    this.tenantDialogTarget.set(target);
    this.tenantFullName.set('');
    this.tenantDateOfBirth.set(null);
    this.tenantIdCard.set('');
    this.tenantPhone.set('');
    this.tenantEmail.set('');
    this.tenantSubmitted.set(false);
    this.tenantDialogVisible.set(true);
  }

  submitNewTenant(): void {
    this.tenantSubmitted.set(true);
    const dob = this.tenantDateOfBirth();
    if (!this.tenantFullName() || !dob || !this.tenantPhone()) {
      return;
    }
    if (this.tenantIsAdult() && !this.tenantIdCard()) {
      return;
    }

    this.tenantService
      .create({
        fullName: this.tenantFullName(),
        dateOfBirth: toIsoDate(dob),
        idCardNumber: this.tenantIdCard() || null,
        phoneNumber: this.tenantPhone(),
        email: this.tenantEmail() || null,
      })
      .subscribe({
        next: (tenant) => {
          this.tenantDialogVisible.set(false);
          if (this.tenantDialogTarget() === 'new-contract') {
            this.addTenantToNewContract(tenant);
          } else {
            this.addExistingTenantToActiveContract(tenant);
          }
        },
        error: () => {},
      });
  }

  viewChecklist(contract: ContractResponse): void {
    this.checkoutService.get(contract.id).subscribe({
      next: (data) => {
        this.checklistData.set(data);
        this.checklistDialogVisible.set(true);
      },
      error: () => {},
    });
  }

  viewHistoryTenants(contract: ContractResponse): void {
    this.contractService.get(contract.id).subscribe({
      next: (detail) => {
        this.historyTenants.set(detail.tenants);
        this.historyTenantsDialogVisible.set(true);
      },
      error: () => {},
    });
  }

  viewHistoryBills(contract: ContractResponse): void {
    this.billingService.listByContract(contract.id).subscribe({
      next: (bills) => {
        this.historyBills.set(bills);
        this.historyBillsDialogVisible.set(true);
      },
      error: () => {},
    });
  }

  // ---- Tab 3: hóa đơn & công nợ ----
  private loadBills(contractId: number): void {
    this.billingService.listByContract(contractId).subscribe({
      next: (bills) => this.bills.set(bills),
      error: () => {},
    });

    this.extraFeeCategoryService.listAll().subscribe({
      next: (categories) => this.extraFeeCategories.set(categories),
      error: () => {},
    });
  }

  createBill(): void {
    const contract = this.activeContract();
    if (!contract) {
      return;
    }
    const { year: billYear, month: billMonth } = dateToYearMonth(this.newBillPeriod());
    this.billingService.createBill(contract.id, { billMonth, billYear }).subscribe({
      next: () => {
        this.notification.success(this.translate.instant('BILLING.CREATE_BILL_SUCCESS'));
        this.loadBills(contract.id);
      },
      error: () => {},
    });
  }

  selectBill(bill: MonthlyBillResponse): void {
    this.billingService.get(bill.id).subscribe({
      next: (detail) => this.billDetail.set(detail),
      error: () => {},
    });
  }

  private refreshSelectedBill(): void {
    const bill = this.billDetail();
    if (!bill) {
      return;
    }
    this.billingService.get(bill.bill.id).subscribe({
      next: (detail) => this.billDetail.set(detail),
      error: () => {},
    });
    const contract = this.activeContract();
    if (contract) {
      this.loadBills(contract.id);
    }
  }

  addExtraFeeItem(): void {
    const bill = this.billDetail();
    if (!bill || !this.newFeeCategoryId() || !this.newFeeAmount()) {
      return;
    }
    this.billingService
      .addExtraFeeItem(bill.bill.id, {
        extraFeeCategoryId: this.newFeeCategoryId()!,
        amount: this.newFeeAmount()!,
        note: this.newFeeNote() || null,
      })
      .subscribe({
        next: () => {
          this.newFeeCategoryId.set(null);
          this.newFeeAmount.set(null);
          this.newFeeNote.set('');
          this.refreshSelectedBill();
        },
        error: () => {},
      });
  }

  deleteExtraFeeItem(item: ExtraFeeItemResponse): void {
    const bill = this.billDetail();
    if (!bill) {
      return;
    }
    this.confirmService.confirm(this.translate.instant('BILLING.DELETE_FEE_CONFIRM'), () => {
      this.billingService.deleteExtraFeeItem(bill.bill.id, item.id).subscribe({
        next: () => this.refreshSelectedBill(),
        error: () => {},
      });
    });
  }

  confirmBill(): void {
    const bill = this.billDetail();
    if (!bill) {
      return;
    }
    this.confirmService.confirm(this.translate.instant('BILLING.CONFIRM_BILL_CONFIRM'), () => {
      this.billingService.confirmBill(bill.bill.id).subscribe({
        next: () => {
          this.notification.success(this.translate.instant('BILLING.CONFIRM_BILL_SUCCESS'));
          this.refreshSelectedBill();
        },
        error: () => {},
      });
    });
  }

  recordPayment(): void {
    const bill = this.billDetail();
    const paymentDate = this.newPaymentDate();
    if (!bill || !this.newPaymentAmount() || !paymentDate) {
      return;
    }
    if (this.newPaymentAmount()! > bill.bill.remainingAmount) {
      return;
    }
    this.billingService
      .recordPayment(bill.bill.id, {
        amount: this.newPaymentAmount()!,
        paymentDate: toIsoDate(paymentDate),
        method: this.newPaymentMethod() || null,
        note: this.newPaymentNote() || null,
      })
      .subscribe({
        next: () => {
          this.newPaymentAmount.set(null);
          this.newPaymentMethod.set('');
          this.newPaymentNote.set('');
          this.notification.success(this.translate.instant('BILLING.PAYMENT_SUCCESS'));
          this.refreshSelectedBill();
        },
        error: () => {},
      });
  }

  // ---- Tab 4: trả phòng ----
  onCheckoutItemQuantityChange(row: CheckoutItemRow): void {
    row.deductionAmount = (row.damagedQuantity + row.lostQuantity) * row.itemPrice;
  }

  submitCheckout(): void {
    this.checkoutSubmitted.set(true);
    const contract = this.activeContract();
    const checkoutDate = this.checkoutDate();
    if (!contract || !checkoutDate || this.hasCheckoutQuantityError()) {
      return;
    }

    this.confirmService.confirm(this.translate.instant('CHECKOUT.CONFIRM'), () => {
      this.checkoutService
        .checkout(contract.id, {
          checkoutDate: toIsoDate(checkoutDate),
          note: this.checkoutNote() || null,
          items: this.checkoutItems().map((row) => ({
            roomTypeHandoverItemId: row.roomTypeHandoverItemId,
            damagedQuantity: row.damagedQuantity,
            lostQuantity: row.lostQuantity,
            deductionAmount: row.deductionAmount,
            note: row.note || null,
          })),
        })
        .subscribe({
          next: (result) => {
            this.notification.success(this.translate.instant('CHECKOUT.SUCCESS'));
            this.checklistData.set(result);
            this.checklistDialogVisible.set(true);
            this.checkoutNote.set('');
            this.checkoutSubmitted.set(false);
            this.activeContractDetail.set(null);
            this.bills.set([]);
            this.billDetail.set(null);
            this.loadRoom();
            this.loadContracts();
          },
          error: () => {},
        });
    });
  }
}

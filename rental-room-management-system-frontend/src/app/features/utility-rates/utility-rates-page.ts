import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { ExtraFeeCategoryResponse } from '../../core/models/extra-fee-category.model';
import { BranchOption } from '../../core/models/room.model';
import { UtilityRateResponse } from '../../core/models/utility-rate.model';
import { ExtraFeeCategoryService } from '../../core/services/extra-fee-category.service';
import { LoadingService } from '../../core/services/loading.service';
import { NotificationService } from '../../core/services/notification.service';
import { RoomService } from '../../core/services/room.service';
import { UtilityRateService } from '../../core/services/utility-rate.service';
import { toIsoDate } from '../../core/utils/date.util';

@Component({
  selector: 'app-utility-rates-page',
  standalone: true,
  imports: [DecimalPipe, FormsModule, TranslatePipe, TableModule, SelectModule, InputNumberModule, DatePickerModule, ButtonModule],
  templateUrl: './utility-rates-page.html',
})
export class UtilityRatesPage {
  private readonly utilityRateService = inject(UtilityRateService);
  private readonly roomService = inject(RoomService);
  private readonly extraFeeCategoryService = inject(ExtraFeeCategoryService);
  private readonly loadingService = inject(LoadingService);
  private readonly notification = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  readonly loading = this.loadingService.isLoading;

  readonly branches = signal<BranchOption[]>([]);
  readonly selectedBranchId = signal<number | null>(null);
  readonly rows = signal<UtilityRateResponse[]>([]);

  readonly meteredCategories = signal<ExtraFeeCategoryResponse[]>([]);

  readonly newCategoryId = signal<number | null>(null);
  readonly newUnitPrice = signal<number | null>(null);
  readonly newEffectiveFrom = signal<Date>(new Date());
  readonly submitted = signal(false);

  readonly canSubmit = computed(
    () => this.newCategoryId() !== null && this.newUnitPrice() !== null && this.newUnitPrice()! >= 0,
  );

  constructor() {
    this.extraFeeCategoryService.listAll().subscribe({
      next: (categories) => this.meteredCategories.set(categories.filter((c) => c.metered)),
      error: () => {},
    });
    this.roomService.branchOptions().subscribe({
      next: (branches) => {
        this.branches.set(branches);
        if (branches.length > 0) {
          this.selectedBranchId.set(branches[0].id);
          this.load();
        }
      },
      error: () => {},
    });
  }

  onBranchChange(): void {
    this.load();
  }

  load(): void {
    const branchId = this.selectedBranchId();
    if (!branchId) {
      return;
    }
    this.utilityRateService.list(branchId).subscribe({
      next: (rows) => this.rows.set(rows),
      error: () => {},
    });
  }

  addRate(): void {
    this.submitted.set(true);
    const branchId = this.selectedBranchId();
    if (!branchId || !this.canSubmit()) {
      return;
    }
    this.utilityRateService
      .create(branchId, {
        extraFeeCategoryId: this.newCategoryId()!,
        unitPrice: this.newUnitPrice()!,
        effectiveFrom: toIsoDate(this.newEffectiveFrom()),
      })
      .subscribe({
        next: () => {
          this.notification.success(this.translate.instant('UTILITY_RATE.CREATE_SUCCESS'));
          this.submitted.set(false);
          this.newCategoryId.set(null);
          this.newUnitPrice.set(null);
          this.newEffectiveFrom.set(new Date());
          this.load();
        },
        error: () => {},
      });
  }
}

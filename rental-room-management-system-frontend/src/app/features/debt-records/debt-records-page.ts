import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { DebtStatus } from '../../core/enums/debt-status.enum';
import { DebtRecordResponse } from '../../core/models/debt-record.model';
import { DebtRecordService } from '../../core/services/debt-record.service';
import { LoadingService } from '../../core/services/loading.service';
import { NotificationService } from '../../core/services/notification.service';
import { translatedOptions } from '../../shared/utils/translated-options';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-debt-records-page',
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
  ],
  templateUrl: './debt-records-page.html',
})
export class DebtRecordsPage {
  private readonly debtRecordService = inject(DebtRecordService);
  private readonly translate = inject(TranslateService);
  private readonly loadingService = inject(LoadingService);
  private readonly notification = inject(NotificationService);

  readonly rows = signal<DebtRecordResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = this.loadingService.isLoading;
  readonly statusFilter = signal<DebtStatus | null>(null);

  readonly statusOptions = translatedOptions(this.translate, [
    { labelKey: 'COMMON.ALL', value: null },
    { labelKey: 'DEBT.STATUS_CHUA_THU', value: DebtStatus.CHUA_THU },
    { labelKey: 'DEBT.STATUS_DA_THU', value: DebtStatus.DA_THU },
  ]);

  readonly collectDialogVisible = signal(false);
  readonly collectTarget = signal<DebtRecordResponse | null>(null);
  readonly collectAmount = signal<number | null>(null);

  private lastEvent?: TableLazyLoadEvent;

  load(event?: TableLazyLoadEvent): void {
    this.lastEvent = event ?? this.lastEvent;
    const rows = this.lastEvent?.rows ?? PAGE_SIZE;
    const first = this.lastEvent?.first ?? 0;
    const page = Math.floor(first / rows);

    this.debtRecordService
      .list(
        {
          page,
          size: rows,
          sortField: (this.lastEvent?.sortField as string) || undefined,
          sortOrder: this.lastEvent?.sortOrder === -1 ? 'desc' : 'asc',
        },
        this.statusFilter(),
      )
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.totalRecords.set(page.totalElements);
        },
        // Failure is already surfaced globally by the error interceptor's toast.
        error: () => {},
      });
  }

  onFilterChange(): void {
    this.load({ first: 0, rows: PAGE_SIZE });
  }

  openCollectDialog(record: DebtRecordResponse): void {
    this.collectTarget.set(record);
    this.collectAmount.set(record.amount - record.collectedAmount);
    this.collectDialogVisible.set(true);
  }

  confirmCollect(): void {
    const target = this.collectTarget();
    const amount = this.collectAmount();
    if (!target || !amount || amount <= 0) {
      return;
    }

    this.debtRecordService.collect(target.id, amount).subscribe({
      next: () => {
        this.collectDialogVisible.set(false);
        this.notification.success(this.translate.instant('DEBT.COLLECT_SUCCESS'));
        this.load();
      },
      // Failure is already surfaced globally by the error interceptor's toast.
      error: () => {},
    });
  }
}

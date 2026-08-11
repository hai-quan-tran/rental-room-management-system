import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { BillSyncStatus, MeterReadingGridRowResponse } from '../../core/models/meter-reading.model';
import { BranchOption } from '../../core/models/room.model';
import { LoadingService } from '../../core/services/loading.service';
import { MeterReadingService } from '../../core/services/meter-reading.service';
import { NotificationService } from '../../core/services/notification.service';
import { RoomService } from '../../core/services/room.service';
import { dateToYearMonth } from '../../core/utils/date.util';

interface EditableCell {
  extraFeeCategoryId: number;
  categoryName: string;
  unit: string | null;
  /** Null means this room+category has never been read before — the admin must type it once. */
  previousReading: number | null;
  oldReadingDraft: number | null;
  newReadingDraft: number | null;
  unitPrice: number | null;
  billFullyPaid: boolean;
}

interface EditableRow {
  roomId: number;
  roomCode: string;
  cells: EditableCell[];
}

interface ColumnDef {
  id: number;
  name: string;
  unit: string | null;
}

const BILL_SYNC_MESSAGE_KEY: Record<BillSyncStatus, string> = {
  UPDATED: 'METER_READING.SYNC_UPDATED',
  NO_BILL_YET: 'METER_READING.SYNC_NO_BILL_YET',
  SKIPPED_PAID: 'METER_READING.SYNC_SKIPPED_PAID',
  SKIPPED_AMBIGUOUS_CONTRACT: 'METER_READING.SYNC_SKIPPED_AMBIGUOUS',
};

@Component({
  selector: 'app-meter-readings-page',
  standalone: true,
  imports: [DecimalPipe, FormsModule, TranslatePipe, TableModule, SelectModule, InputNumberModule, DatePickerModule, ButtonModule],
  templateUrl: './meter-readings-page.html',
})
export class MeterReadingsPage {
  private readonly meterReadingService = inject(MeterReadingService);
  private readonly roomService = inject(RoomService);
  private readonly loadingService = inject(LoadingService);
  private readonly notification = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  readonly loading = this.loadingService.isLoading;

  readonly branches = signal<BranchOption[]>([]);
  readonly selectedBranchId = signal<number | null>(null);
  readonly billPeriod = signal<Date>(new Date());

  readonly rows = signal<EditableRow[]>([]);
  readonly columns = computed<ColumnDef[]>(() => {
    const first = this.rows()[0];
    return first ? first.cells.map((cell) => ({ id: cell.extraFeeCategoryId, name: cell.categoryName, unit: cell.unit })) : [];
  });

  constructor() {
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

  onFilterChange(): void {
    this.load();
  }

  load(): void {
    const branchId = this.selectedBranchId();
    if (!branchId) {
      return;
    }
    const { year: billYear, month: billMonth } = dateToYearMonth(this.billPeriod());
    this.meterReadingService.listGrid(branchId, billYear, billMonth).subscribe({
      next: (grid) => this.rows.set(grid.map((row) => this.toEditableRow(row))),
      error: () => {},
    });
  }

  consumption(cell: EditableCell): number | null {
    const oldReading = cell.previousReading ?? cell.oldReadingDraft;
    if (cell.newReadingDraft === null || oldReading === null) {
      return null;
    }
    return cell.newReadingDraft - oldReading;
  }

  amount(cell: EditableCell): number | null {
    const consumption = this.consumption(cell);
    if (consumption === null || cell.unitPrice === null) {
      return null;
    }
    return Math.round(consumption * cell.unitPrice);
  }

  canSave(cell: EditableCell): boolean {
    if (cell.billFullyPaid || cell.newReadingDraft === null) {
      return false;
    }
    return cell.previousReading !== null || cell.oldReadingDraft !== null;
  }

  saveCell(row: EditableRow, cell: EditableCell): void {
    if (!this.canSave(cell)) {
      return;
    }
    const { year: billYear, month: billMonth } = dateToYearMonth(this.billPeriod());
    this.meterReadingService
      .upsert(row.roomId, {
        extraFeeCategoryId: cell.extraFeeCategoryId,
        billMonth,
        billYear,
        oldReading: cell.previousReading === null ? cell.oldReadingDraft : null,
        newReading: cell.newReadingDraft!,
        note: null,
      })
      .subscribe({
        next: (response) => {
          this.notification.success(this.translate.instant(BILL_SYNC_MESSAGE_KEY[response.billSyncStatus]));
          cell.previousReading = response.oldReading;
          cell.oldReadingDraft = null;
          cell.newReadingDraft = response.newReading;
          cell.unitPrice = response.unitPrice;
        },
        error: () => {},
      });
  }

  private toEditableRow(row: MeterReadingGridRowResponse): EditableRow {
    return {
      roomId: row.roomId,
      roomCode: row.roomCode,
      cells: row.readings.map((cell) => ({
        extraFeeCategoryId: cell.extraFeeCategoryId,
        categoryName: cell.categoryName,
        unit: cell.unit,
        previousReading: cell.previousReading,
        oldReadingDraft: null,
        newReadingDraft: cell.currentReading,
        unitPrice: cell.unitPrice,
        billFullyPaid: cell.billFullyPaid,
      })),
    };
  }
}

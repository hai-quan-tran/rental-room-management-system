import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CardModule } from 'primeng/card';
import { ChartModule } from 'primeng/chart';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { Role } from '../../core/enums/role.enum';
import { DashboardResponse } from '../../core/models/dashboard.model';
import { BranchOption } from '../../core/models/room.model';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { RoomService } from '../../core/services/room.service';
import { addMonths, dateToYearMonth, shiftMonthDate } from '../../core/utils/date.util';
import { roomBranchLabel } from '../../shared/utils/display.util';

/** Rows beyond this count switch a table to a fixed-height scrollable body instead of growing the page. */
const TABLE_SCROLL_THRESHOLD = 10;
const TABLE_SCROLL_HEIGHT = '28rem';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    TranslatePipe,
    CardModule,
    ChartModule,
    DatePickerModule,
    SelectModule,
    TableModule,
  ],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage {
  private readonly authService = inject(AuthService);
  private readonly translate = inject(TranslateService);
  private readonly roomService = inject(RoomService);
  private readonly dashboardService = inject(DashboardService);

  readonly isSuperAdmin = computed(() => this.authService.currentUser()?.role === Role.ADMIN_TONG);
  readonly roomBranchLabel = roomBranchLabel;

  private readonly branches = signal<BranchOption[]>([]);
  readonly branchOptions = computed(() => [
    { label: this.translate.translate('DASHBOARD.ALL_BRANCHES')(), value: null },
    ...this.branches().map((b) => ({ label: b.name, value: b.id })),
  ]);

  readonly selectedBranch = signal<number | null>(null);

  /** Default range: from 6 months ago through the current month. */
  readonly fromMonth = signal<Date>(shiftMonthDate(new Date(), -6));
  readonly toMonth = signal<Date>(new Date());

  private readonly data = signal<DashboardResponse | null>(null);

  readonly roomStatusData = computed(() => {
    const roomStatus = this.data()?.roomStatus;
    return {
      labels: ['Trống', 'Đang thuê'],
      datasets: [
        {
          label: 'Số phòng',
          backgroundColor: ['#93c5fd', '#2563eb'],
          data: [roomStatus?.emptyCount ?? 0, roomStatus?.occupiedCount ?? 0],
        },
      ],
    };
  });

  readonly moveInOutData = computed(() => {
    const points = this.data()?.moveInOut ?? [];
    return {
      labels: points.map((p) => `${p.month}/${p.year}`),
      datasets: [
        { label: 'Trả phòng', backgroundColor: '#fca5a5', data: points.map((p) => p.moveOutCount) },
        { label: 'Vào ở mới', backgroundColor: '#93c5fd', data: points.map((p) => p.moveInCount) },
      ],
    };
  });

  readonly revenueData = computed(() => {
    const points = this.data()?.revenue ?? [];
    return {
      labels: points.map((p) => `${p.month}/${p.year}`),
      datasets: [
        {
          label: 'Doanh thu',
          borderColor: '#2563eb',
          backgroundColor: 'rgba(37, 99, 235, 0.2)',
          data: points.map((p) => p.totalAmount),
          fill: true,
          tension: 0.35,
        },
      ],
    };
  });

  readonly chartOptions = {
    plugins: { legend: { position: 'bottom' } },
    responsive: true,
    maintainAspectRatio: false,
  };

  readonly occupantsByBranch = computed(() => this.data()?.occupantsByBranch ?? []);

  private readonly currentPeriod = dateToYearMonth(new Date());

  /**
   * The previous month's bill is expected to be created between day 1 and day 9 of the
   * current month (e.g. on Aug 5, July's bill is the one that should already exist) — mirrors
   * the same rule `DashboardService.missingInvoiceRooms` applies server-side, kept here only
   * for the hint text.
   */
  readonly expectedBillPeriod = addMonths(this.currentPeriod.year, this.currentPeriod.month, -1);

  readonly missingInvoiceRooms = computed(() => this.data()?.missingInvoiceRooms ?? []);
  readonly missingInvoiceScrollHeight = computed(() =>
    this.missingInvoiceRooms().length > TABLE_SCROLL_THRESHOLD ? TABLE_SCROLL_HEIGHT : undefined,
  );

  readonly unpaidInvoiceRooms = computed(() => this.data()?.unpaidInvoiceRooms ?? []);
  readonly unpaidInvoiceScrollHeight = computed(() =>
    this.unpaidInvoiceRooms().length > TABLE_SCROLL_THRESHOLD ? TABLE_SCROLL_HEIGHT : undefined,
  );

  constructor() {
    if (this.isSuperAdmin()) {
      this.roomService.branchOptions().subscribe({
        next: (branches) => this.branches.set(branches),
        error: () => {},
      });
    }
    this.load();
  }

  onBranchChange(value: number | null): void {
    this.selectedBranch.set(value);
    this.load();
  }

  onFromMonthChange(value: Date): void {
    this.fromMonth.set(value);
    this.load();
  }

  onToMonthChange(value: Date): void {
    this.toMonth.set(value);
    this.load();
  }

  private load(): void {
    const from = dateToYearMonth(this.fromMonth());
    const to = dateToYearMonth(this.toMonth());
    this.dashboardService
      .get({
        branchId: this.selectedBranch(),
        fromYear: from.year,
        fromMonth: from.month,
        toYear: to.year,
        toMonth: to.month,
      })
      .subscribe({
        next: (data) => this.data.set(data),
        error: () => {},
      });
  }
}

import { DecimalPipe } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CardModule } from 'primeng/card';
import { ChartModule } from 'primeng/chart';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { Role } from '../../core/enums/role.enum';
import { AuthService } from '../../core/services/auth.service';
import { addMonths, dateToYearMonth, shiftMonthDate } from '../../core/utils/date.util';
import { roomBranchLabel } from '../../shared/utils/display.util';

/** Rows beyond this count switch a table to a fixed-height scrollable body instead of growing the page. */
const TABLE_SCROLL_THRESHOLD = 10;
const TABLE_SCROLL_HEIGHT = '28rem';

/** Deterministic 0..1 value keyed by an integer seed, so the same month always mocks the same number. */
function pseudoRandom(seed: number): number {
  const x = Math.sin(seed * 12.9898) * 43758.5453;
  return x - Math.floor(x);
}

function moveOutsFor(year: number, month: number): number {
  return 1 + Math.floor(pseudoRandom(year * 12 + month) * 5);
}

function moveInsFor(year: number, month: number): number {
  return 2 + Math.floor(pseudoRandom(year * 12 + month + 0.5) * 6);
}

function revenueFor(year: number, month: number): number {
  return 40 + Math.floor(pseudoRandom(year * 12 + month + 0.25) * 20);
}

/**
 * Sample data only — real numbers come from GET /api/dashboard once the
 * dashboard service/API integration is built (see project_backend_status memory).
 */
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
  readonly isSuperAdmin = computed(() => this.authService.currentUser()?.role === Role.ADMIN_TONG);

  readonly branchOptions = computed(() => [
    { label: this.translate.translate('DASHBOARD.ALL_BRANCHES')(), value: null },
    { label: 'Chi nhánh Quận 1', value: 1 },
    { label: 'Chi nhánh Quận 7', value: 2 },
  ]);

  readonly selectedBranch = signal<number | null>(null);
  readonly roomBranchLabel = roomBranchLabel;

  readonly roomStatusData = {
    labels: ['Trống', 'Đang thuê'],
    datasets: [{ label: 'Số phòng', backgroundColor: ['#93c5fd', '#2563eb'], data: [12, 38] }],
  };

  /** Default range: from 6 months ago through the current month. */
  readonly fromMonth = signal<Date>(shiftMonthDate(new Date(), -6));
  readonly toMonth = signal<Date>(new Date());

  private readonly visiblePeriods = computed(() => {
    const from = this.fromMonth();
    const to = this.toMonth();
    const periods: { year: number; month: number }[] = [];
    let year = from.getFullYear();
    let month = from.getMonth() + 1;
    const toYear = to.getFullYear();
    const toMonthNum = to.getMonth() + 1;
    while (year < toYear || (year === toYear && month <= toMonthNum)) {
      periods.push({ year, month });
      month++;
      if (month > 12) {
        month = 1;
        year++;
      }
    }
    return periods;
  });

  readonly moveInOutData = computed(() => {
    const periods = this.visiblePeriods();
    return {
      labels: periods.map((p) => `${p.month}/${p.year}`),
      datasets: [
        {
          label: 'Trả phòng',
          backgroundColor: '#fca5a5',
          data: periods.map((p) => moveOutsFor(p.year, p.month)),
        },
        {
          label: 'Vào ở mới',
          backgroundColor: '#93c5fd',
          data: periods.map((p) => moveInsFor(p.year, p.month)),
        },
      ],
    };
  });

  readonly revenueData = computed(() => {
    const periods = this.visiblePeriods();
    return {
      labels: periods.map((p) => `${p.month}/${p.year}`),
      datasets: [
        {
          label: 'Doanh thu',
          borderColor: '#2563eb',
          backgroundColor: 'rgba(37, 99, 235, 0.2)',
          data: periods.map((p) => revenueFor(p.year, p.month)),
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

  /**
   * Mock rows only. Once wired to the real API, a tenant who is the representative
   * on more than one active contract at the same time must still count as 1 occupant,
   * not once per contract/room.
   */
  readonly occupantsByBranch = [
    { branchName: 'Chi nhánh Quận 1', occupantCount: 42 },
    { branchName: 'Chi nhánh Quận 7', occupantCount: 35 },
  ];

  private readonly currentPeriod = dateToYearMonth(new Date());

  /**
   * The previous month's bill is expected to be created between day 1 and day 9 of the
   * current month (e.g. on Aug 5, July's bill is the one that should already exist).
   */
  readonly expectedBillPeriod = addMonths(this.currentPeriod.year, this.currentPeriod.month, -1);

  private readonly olderBillPeriod = addMonths(
    this.expectedBillPeriod.year,
    this.expectedBillPeriod.month,
    -1,
  );

  /** 13 rows (> the 10-row scroll threshold) to exercise the scrollable table body. */
  readonly missingInvoiceRooms = [
    {
      roomCode: 'P101',
      branchName: 'Chi nhánh Quận 1',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
    {
      roomCode: 'P205',
      branchName: 'Chi nhánh Quận 7',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
    {
      roomCode: 'P108',
      branchName: 'Chi nhánh Quận 1',
      missingMonth: this.olderBillPeriod.month,
      missingYear: this.olderBillPeriod.year,
    },
    {
      roomCode: 'P110',
      branchName: 'Chi nhánh Quận 1',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
    {
      roomCode: 'P112',
      branchName: 'Chi nhánh Quận 1',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
    {
      roomCode: 'P115',
      branchName: 'Chi nhánh Quận 1',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
    {
      roomCode: 'P118',
      branchName: 'Chi nhánh Quận 1',
      missingMonth: this.olderBillPeriod.month,
      missingYear: this.olderBillPeriod.year,
    },
    {
      roomCode: 'P207',
      branchName: 'Chi nhánh Quận 7',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
    {
      roomCode: 'P209',
      branchName: 'Chi nhánh Quận 7',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
    {
      roomCode: 'P212',
      branchName: 'Chi nhánh Quận 7',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
    {
      roomCode: 'P215',
      branchName: 'Chi nhánh Quận 7',
      missingMonth: this.olderBillPeriod.month,
      missingYear: this.olderBillPeriod.year,
    },
    {
      roomCode: 'P218',
      branchName: 'Chi nhánh Quận 7',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
    {
      roomCode: 'P220',
      branchName: 'Chi nhánh Quận 7',
      missingMonth: this.expectedBillPeriod.month,
      missingYear: this.expectedBillPeriod.year,
    },
  ];

  readonly missingInvoiceScrollHeight =
    this.missingInvoiceRooms.length > TABLE_SCROLL_THRESHOLD ? TABLE_SCROLL_HEIGHT : undefined;

  /** 13 rows (> the 10-row scroll threshold) to exercise the scrollable table body. */
  readonly unpaidInvoiceRooms = [
    {
      roomCode: 'P102',
      branchName: 'Chi nhánh Quận 1',
      totalAmount: 3_500_000,
      paidAmount: 0,
      remainingAmount: 3_500_000,
    },
    {
      roomCode: 'P210',
      branchName: 'Chi nhánh Quận 7',
      totalAmount: 2_850_000,
      paidAmount: 1_000_000,
      remainingAmount: 1_850_000,
    },
    {
      roomCode: 'P114',
      branchName: 'Chi nhánh Quận 1',
      totalAmount: 3_300_000,
      paidAmount: 2_800_000,
      remainingAmount: 500_000,
    },
    {
      roomCode: 'P103',
      branchName: 'Chi nhánh Quận 1',
      totalAmount: 2_800_000,
      paidAmount: 0,
      remainingAmount: 2_800_000,
    },
    {
      roomCode: 'P107',
      branchName: 'Chi nhánh Quận 1',
      totalAmount: 3_550_000,
      paidAmount: 1_500_000,
      remainingAmount: 2_050_000,
    },
    {
      roomCode: 'P111',
      branchName: 'Chi nhánh Quận 1',
      totalAmount: 2_850_000,
      paidAmount: 2_000_000,
      remainingAmount: 850_000,
    },
    {
      roomCode: 'P116',
      branchName: 'Chi nhánh Quận 1',
      totalAmount: 3_300_000,
      paidAmount: 0,
      remainingAmount: 3_300_000,
    },
    {
      roomCode: 'P119',
      branchName: 'Chi nhánh Quận 1',
      totalAmount: 2_800_000,
      paidAmount: 1_200_000,
      remainingAmount: 1_600_000,
    },
    {
      roomCode: 'P202',
      branchName: 'Chi nhánh Quận 7',
      totalAmount: 3_300_000,
      paidAmount: 0,
      remainingAmount: 3_300_000,
    },
    {
      roomCode: 'P206',
      branchName: 'Chi nhánh Quận 7',
      totalAmount: 2_600_000,
      paidAmount: 1_800_000,
      remainingAmount: 800_000,
    },
    {
      roomCode: 'P211',
      branchName: 'Chi nhánh Quận 7',
      totalAmount: 3_350_000,
      paidAmount: 0,
      remainingAmount: 3_350_000,
    },
    {
      roomCode: 'P216',
      branchName: 'Chi nhánh Quận 7',
      totalAmount: 2_650_000,
      paidAmount: 1_000_000,
      remainingAmount: 1_650_000,
    },
    {
      roomCode: 'P219',
      branchName: 'Chi nhánh Quận 7',
      totalAmount: 3_300_000,
      paidAmount: 2_500_000,
      remainingAmount: 800_000,
    },
  ];

  readonly unpaidInvoiceScrollHeight =
    this.unpaidInvoiceRooms.length > TABLE_SCROLL_THRESHOLD ? TABLE_SCROLL_HEIGHT : undefined;

  constructor(
    private readonly authService: AuthService,
    private readonly translate: TranslateService,
  ) {}
}

import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CardModule } from 'primeng/card';
import { ChartModule } from 'primeng/chart';
import { SelectModule } from 'primeng/select';

import { Role } from '../../core/enums/role.enum';
import { AuthService } from '../../core/services/auth.service';

/**
 * Sample data only — real numbers come from GET /api/dashboard once the
 * dashboard service/API integration is built (see project_backend_status memory).
 */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [FormsModule, TranslatePipe, CardModule, ChartModule, SelectModule],
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

  readonly roomStatusData = {
    labels: ['Trống', 'Đang thuê'],
    datasets: [{ label: 'Số phòng', backgroundColor: ['#93c5fd', '#2563eb'], data: [12, 38] }],
  };

  readonly moveInOutData = {
    labels: ['T1', 'T2', 'T3', 'T4', 'T5', 'T6'],
    datasets: [
      { label: 'Trả phòng', backgroundColor: '#fca5a5', data: [2, 3, 1, 4, 2, 3] },
      { label: 'Vào ở mới', backgroundColor: '#93c5fd', data: [4, 2, 3, 5, 3, 6] },
    ],
  };

  readonly revenueData = {
    labels: ['T1', 'T2', 'T3', 'T4', 'T5', 'T6'],
    datasets: [
      {
        label: 'Doanh thu',
        borderColor: '#2563eb',
        backgroundColor: 'rgba(37, 99, 235, 0.2)',
        data: [42, 45, 47, 44, 50, 53],
        fill: true,
        tension: 0.35,
      },
    ],
  };

  readonly chartOptions = {
    plugins: { legend: { position: 'bottom' } },
    responsive: true,
    maintainAspectRatio: false,
  };

  constructor(
    private readonly authService: AuthService,
    private readonly translate: TranslateService,
  ) {}
}

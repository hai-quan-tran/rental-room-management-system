import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { RoomTypeSummaryResponse } from '../../../core/models/branch.model';
import { AccountService } from '../../../core/services/account.service';
import { BranchService } from '../../../core/services/branch.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { LoadingService } from '../../../core/services/loading.service';
import { NotificationService } from '../../../core/services/notification.service';

interface ManagerOption {
  label: string;
  value: number;
}

@Component({
  selector: 'app-branch-detail-page',
  standalone: true,
  imports: [FormsModule, RouterLink, TranslatePipe, ButtonModule, InputTextModule, SelectModule, TableModule],
  templateUrl: './branch-detail-page.html',
})
export class BranchDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly branchService = inject(BranchService);
  private readonly accountService = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly notification = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  private readonly loadingService = inject(LoadingService);

  readonly loading = this.loadingService.isLoading;

  private branchId: number | null = null;
  readonly isNew = computed(() => this.branchId === null);

  readonly name = signal('');
  readonly address = signal('');
  readonly managerAccountId = signal<number | null>(null);
  readonly submitted = signal(false);

  readonly managerOptions = signal<ManagerOption[]>([]);
  readonly totalRooms = signal(0);
  readonly roomTypeSummaries = signal<RoomTypeSummaryResponse[]>([]);

  ngOnInit(): void {
    this.accountService.listManagerOptions().subscribe({
      next: (accounts) =>
        this.managerOptions.set(
          accounts.map((a) => ({ label: a.fullName ? `${a.fullName} (${a.username})` : a.username, value: a.id })),
        ),
      error: () => {},
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam || idParam === 'new') {
      return;
    }

    this.branchId = Number(idParam);
    this.branchService.get(this.branchId).subscribe({
      next: (detail) => {
        this.name.set(detail.branch.name);
        this.address.set(detail.branch.address);
        this.managerAccountId.set(detail.branch.managerAccountId);
        this.totalRooms.set(detail.totalRooms);
        this.roomTypeSummaries.set(detail.roomTypeSummaries);
      },
      error: () => {},
    });
  }

  save(): void {
    this.submitted.set(true);
    if (!this.name() || !this.address()) {
      return;
    }

    const request = { name: this.name(), address: this.address(), managerAccountId: this.managerAccountId() };

    this.confirmService.confirm(this.translate.instant('COMMON.SAVE_CONFIRM'), () => {
      const save$ = this.isNew()
        ? this.branchService.create(request)
        : this.branchService.update(this.branchId!, request);

      save$.subscribe({
        next: () => {
          this.notification.success(
            this.translate.instant(this.isNew() ? 'BRANCH.CREATE_SUCCESS' : 'BRANCH.UPDATE_SUCCESS'),
          );
          this.router.navigate(['/branches']);
        },
        error: () => {},
      });
    });
  }
}

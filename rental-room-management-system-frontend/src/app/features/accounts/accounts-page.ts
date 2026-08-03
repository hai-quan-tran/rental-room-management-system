import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { ButtonModule } from 'primeng/button';

import { Role } from '../../core/enums/role.enum';
import { AccountResponse } from '../../core/models/account.model';
import { AccountService } from '../../core/services/account.service';
import { LoadingService } from '../../core/services/loading.service';
import { translatedOptions } from '../../shared/utils/translated-options';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-accounts-page',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    TranslatePipe,
    TableModule,
    TagModule,
    SelectModule,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    ButtonModule,
  ],
  templateUrl: './accounts-page.html',
})
export class AccountsPage {
  private readonly accountService = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly loadingService = inject(LoadingService);
  private readonly router = inject(Router);

  readonly rows = signal<AccountResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = this.loadingService.isLoading;

  readonly roleFilter = signal<Role | null>(null);
  readonly activeFilter = signal<boolean | null>(null);
  readonly searchTerm = signal('');

  readonly roleOptions = translatedOptions(this.translate, [
    { labelKey: 'COMMON.ALL', value: null },
    { labelKey: 'ROLE.ADMIN_TONG', value: Role.ADMIN_TONG },
    { labelKey: 'ROLE.ADMIN_CAP_1', value: Role.ADMIN_CAP_1 },
    { labelKey: 'ROLE.USER', value: Role.USER },
  ]);

  readonly activeOptions = translatedOptions(this.translate, [
    { labelKey: 'COMMON.ALL', value: null },
    { labelKey: 'COMMON.ACTIVE', value: true },
    { labelKey: 'COMMON.INACTIVE', value: false },
  ]);

  load(event?: TableLazyLoadEvent): void {
    const rows = event?.rows ?? PAGE_SIZE;
    const first = event?.first ?? 0;
    const page = Math.floor(first / rows);

    this.accountService
      .list(
        {
          page,
          size: rows,
          sortField: (event?.sortField as string) || undefined,
          sortOrder: event?.sortOrder === -1 ? 'desc' : 'asc',
        },
        {
          role: this.roleFilter(),
          active: this.activeFilter(),
          search: this.searchTerm() || null,
        },
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

  goToDetail(id: number): void {
    this.router.navigate(['/accounts', id]);
  }

  goToCreate(): void {
    this.router.navigate(['/accounts', 'new']);
  }
}

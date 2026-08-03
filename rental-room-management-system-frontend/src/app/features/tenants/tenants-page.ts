import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';

import { TenantResponse } from '../../core/models/tenant.model';
import { LoadingService } from '../../core/services/loading.service';
import { TenantService } from '../../core/services/tenant.service';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-tenants-page',
  standalone: true,
  imports: [FormsModule, TranslatePipe, TableModule, InputTextModule, IconFieldModule, InputIconModule, ButtonModule],
  templateUrl: './tenants-page.html',
})
export class TenantsPage {
  private readonly tenantService = inject(TenantService);
  private readonly loadingService = inject(LoadingService);
  private readonly router = inject(Router);

  readonly rows = signal<TenantResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = this.loadingService.isLoading;
  readonly searchTerm = signal('');

  load(event?: TableLazyLoadEvent): void {
    const rows = event?.rows ?? PAGE_SIZE;
    const first = event?.first ?? 0;
    const page = Math.floor(first / rows);

    this.tenantService
      .list(
        {
          page,
          size: rows,
          sortField: (event?.sortField as string) || undefined,
          sortOrder: event?.sortOrder === -1 ? 'desc' : 'asc',
        },
        this.searchTerm() || null,
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
    this.router.navigate(['/tenants', id]);
  }

  goToCreate(): void {
    this.router.navigate(['/tenants', 'new']);
  }
}

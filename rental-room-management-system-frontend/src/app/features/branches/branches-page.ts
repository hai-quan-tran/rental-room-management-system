import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';

import { BranchResponse } from '../../core/models/branch.model';
import { BranchService } from '../../core/services/branch.service';
import { LoadingService } from '../../core/services/loading.service';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-branches-page',
  standalone: true,
  imports: [FormsModule, TranslatePipe, TableModule, InputTextModule, IconFieldModule, InputIconModule, ButtonModule],
  templateUrl: './branches-page.html',
})
export class BranchesPage {
  private readonly branchService = inject(BranchService);
  private readonly loadingService = inject(LoadingService);
  private readonly router = inject(Router);

  readonly rows = signal<BranchResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = this.loadingService.isLoading;
  readonly searchTerm = signal('');

  load(event?: TableLazyLoadEvent): void {
    const rows = event?.rows ?? PAGE_SIZE;
    const first = event?.first ?? 0;
    const page = Math.floor(first / rows);

    this.branchService
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
    this.router.navigate(['/branches', id]);
  }

  goToCreate(): void {
    this.router.navigate(['/branches', 'new']);
  }
}

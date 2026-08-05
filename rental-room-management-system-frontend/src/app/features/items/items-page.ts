import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';

import { BranchOption } from '../../core/models/room.model';
import { ItemResponse } from '../../core/models/item.model';
import { ItemService } from '../../core/services/item.service';
import { LoadingService } from '../../core/services/loading.service';
import { RoomService } from '../../core/services/room.service';
import { toListQuery } from '../../core/utils/list-query.util';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-items-page',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    TranslatePipe,
    TableModule,
    SelectModule,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    ButtonModule,
  ],
  templateUrl: './items-page.html',
})
export class ItemsPage {
  private readonly itemService = inject(ItemService);
  private readonly roomService = inject(RoomService);
  private readonly loadingService = inject(LoadingService);
  private readonly router = inject(Router);

  readonly branches = signal<BranchOption[]>([]);
  readonly selectedBranchId = signal<number | null>(null);

  readonly rows = signal<ItemResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = this.loadingService.isLoading;
  readonly searchTerm = signal('');

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

  onBranchChange(): void {
    this.load({ first: 0, rows: PAGE_SIZE });
  }

  load(event?: TableLazyLoadEvent): void {
    const branchId = this.selectedBranchId();
    if (!branchId) {
      return;
    }

    this.itemService
      .list(branchId, toListQuery(event, PAGE_SIZE), this.searchTerm() || null)
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
    this.router.navigate(['/items', id]);
  }

  goToCreate(): void {
    const branchId = this.selectedBranchId();
    if (!branchId) {
      return;
    }
    this.router.navigate(['/items', 'new'], { queryParams: { branchId } });
  }
}

import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { Role } from '../../core/enums/role.enum';
import { RoomStatus } from '../../core/enums/room-status.enum';
import { BranchOption, RoomResponse, RoomTypeOption } from '../../core/models/room.model';
import { AuthService } from '../../core/services/auth.service';
import { LoadingService } from '../../core/services/loading.service';
import { RoomTypeService } from '../../core/services/room-type.service';
import { RoomService } from '../../core/services/room.service';
import { translatedOptions } from '../../shared/utils/translated-options';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-rooms-page',
  standalone: true,
  imports: [
    DecimalPipe,
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
  templateUrl: './rooms-page.html',
})
export class RoomsPage {
  private readonly roomService = inject(RoomService);
  private readonly roomTypeService = inject(RoomTypeService);
  private readonly translate = inject(TranslateService);
  private readonly loadingService = inject(LoadingService);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  readonly branches = signal<BranchOption[]>([]);
  readonly roomTypes = signal<RoomTypeOption[]>([]);
  readonly selectedBranchId = signal<number | null>(null);
  readonly canCreate = this.authService.hasRole(Role.ADMIN_TONG);

  readonly rows = signal<RoomResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = this.loadingService.isLoading;

  readonly statusFilter = signal<RoomStatus | null>(null);
  readonly roomTypeFilter = signal<number | null>(null);
  readonly searchTerm = signal('');

  readonly statusOptions = translatedOptions(this.translate, [
    { labelKey: 'COMMON.ALL', value: null },
    { labelKey: 'ROOM.STATUS_TRONG', value: RoomStatus.TRONG },
    { labelKey: 'ROOM.STATUS_DANG_THUE', value: RoomStatus.DANG_THUE },
  ]);

  constructor() {
    this.roomService.branchOptions().subscribe({
      next: (branches) => {
        this.branches.set(branches);
        if (branches.length > 0) {
          this.selectedBranchId.set(branches[0].id);
          this.load();
          this.loadRoomTypes();
        }
      },
      error: () => {},
    });
  }

  private loadRoomTypes(): void {
    const branchId = this.selectedBranchId();
    if (!branchId) {
      return;
    }
    this.roomTypeService.listAll(branchId).subscribe({
      next: (types) => this.roomTypes.set(types),
      error: () => {},
    });
  }

  onBranchChange(): void {
    this.roomTypeFilter.set(null);
    this.load({ first: 0, rows: PAGE_SIZE });
    this.loadRoomTypes();
  }

  load(event?: TableLazyLoadEvent): void {
    const branchId = this.selectedBranchId();
    if (!branchId) {
      return;
    }

    const rows = event?.rows ?? PAGE_SIZE;
    const first = event?.first ?? 0;
    const page = Math.floor(first / rows);

    this.roomService
      .list(
        branchId,
        {
          page,
          size: rows,
          sortField: (event?.sortField as string) || undefined,
          sortOrder: event?.sortOrder === -1 ? 'desc' : 'asc',
        },
        {
          status: this.statusFilter(),
          roomTypeId: this.roomTypeFilter(),
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
    this.router.navigate(['/rooms', id]);
  }

  goToCreate(): void {
    const branchId = this.selectedBranchId();
    if (!branchId) {
      return;
    }
    this.router.navigate(['/rooms', 'new'], { queryParams: { branchId } });
  }
}

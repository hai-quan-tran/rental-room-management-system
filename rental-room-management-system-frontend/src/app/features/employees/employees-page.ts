import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { EmployeeResponse } from '../../core/models/employee.model';
import { EmployeeService } from '../../core/services/employee.service';
import { LoadingService } from '../../core/services/loading.service';
import { toListQuery } from '../../core/utils/list-query.util';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-employees-page',
  standalone: true,
  imports: [
    FormsModule,
    TranslatePipe,
    TableModule,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    ButtonModule,
    TagModule,
  ],
  templateUrl: './employees-page.html',
})
export class EmployeesPage {
  private readonly employeeService = inject(EmployeeService);
  private readonly loadingService = inject(LoadingService);
  private readonly router = inject(Router);

  readonly rows = signal<EmployeeResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = this.loadingService.isLoading;
  readonly searchTerm = signal('');

  load(event?: TableLazyLoadEvent): void {
    this.employeeService.list(toListQuery(event, PAGE_SIZE), this.searchTerm() || null).subscribe({
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
    this.router.navigate(['/employees', id]);
  }

  goToCreate(): void {
    this.router.navigate(['/employees', 'new']);
  }
}

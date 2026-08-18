import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { TenantRentalHistoryResponse } from '../../../core/models/tenant.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { LoadingService } from '../../../core/services/loading.service';
import { NotificationService } from '../../../core/services/notification.service';
import { TenantService } from '../../../core/services/tenant.service';
import { calculateAge, fromIsoDate, toIsoDate } from '../../../core/utils/date.util';
import { displayOr } from '../../../shared/utils/display.util';

@Component({
  selector: 'app-tenant-detail-page',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    TranslatePipe,
    ButtonModule,
    InputTextModule,
    DatePickerModule,
    TableModule,
    TagModule,
  ],
  templateUrl: './tenant-detail-page.html',
})
export class TenantDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly tenantService = inject(TenantService);
  private readonly translate = inject(TranslateService);
  private readonly notification = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  private readonly loadingService = inject(LoadingService);

  readonly loading = this.loadingService.isLoading;
  readonly displayOr = displayOr;

  private tenantId: number | null = null;
  readonly isNew = computed(() => this.tenantId === null);

  readonly fullName = signal('');
  readonly dateOfBirth = signal<Date | null>(null);
  readonly idCardNumber = signal('');
  readonly phoneNumber = signal('');
  readonly email = signal('');
  readonly submitted = signal(false);

  readonly rentalHistory = signal<TenantRentalHistoryResponse[]>([]);

  readonly isAdult = computed(() => {
    const dob = this.dateOfBirth();
    return dob ? calculateAge(dob) >= 18 : false;
  });

  readonly emailRequiredAsRepresentative = computed(
    () => !this.email() && this.rentalHistory().some((h) => h.representative && h.status === 'ACTIVE'),
  );

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam || idParam === 'new') {
      return;
    }

    this.tenantId = Number(idParam);
    this.tenantService.get(this.tenantId).subscribe({
      next: (tenant) => {
        this.fullName.set(tenant.fullName);
        this.dateOfBirth.set(fromIsoDate(tenant.dateOfBirth));
        this.idCardNumber.set(tenant.idCardNumber ?? '');
        this.phoneNumber.set(tenant.phoneNumber);
        this.email.set(tenant.email ?? '');
      },
      error: () => {},
    });

    this.tenantService.rentalHistory(this.tenantId).subscribe({
      next: (history) => this.rentalHistory.set(history),
      error: () => {},
    });
  }

  save(): void {
    this.submitted.set(true);
    const dob = this.dateOfBirth();
    if (!this.fullName() || !dob || !this.phoneNumber()) {
      return;
    }
    if (this.isAdult() && !this.idCardNumber()) {
      return;
    }
    if (this.emailRequiredAsRepresentative()) {
      return;
    }

    const request = {
      fullName: this.fullName(),
      dateOfBirth: toIsoDate(dob),
      idCardNumber: this.idCardNumber() || null,
      phoneNumber: this.phoneNumber(),
      email: this.email() || null,
    };

    this.confirmService.confirm(this.translate.instant('COMMON.SAVE_CONFIRM'), () => {
      const save$ = this.isNew()
        ? this.tenantService.create(request)
        : this.tenantService.update(this.tenantId!, request);

      save$.subscribe({
        next: () => {
          this.notification.success(
            this.translate.instant(this.isNew() ? 'TENANT.CREATE_SUCCESS' : 'TENANT.UPDATE_SUCCESS'),
          );
          this.router.navigate(['/tenants']);
        },
        error: () => {},
      });
    });
  }

  remove(): void {
    if (!this.tenantId) {
      return;
    }
    this.confirmService.confirm(this.translate.instant('TENANT.DELETE_CONFIRM'), () => {
      this.tenantService.delete(this.tenantId!).subscribe({
        next: () => {
          this.notification.success(this.translate.instant('TENANT.DELETE_SUCCESS'));
          this.router.navigate(['/tenants']);
        },
        error: () => {},
      });
    });
  }
}

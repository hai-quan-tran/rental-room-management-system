import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';

import { Role } from '../../../core/enums/role.enum';
import { AccountResponse } from '../../../core/models/account.model';
import { EmployeeResponse } from '../../../core/models/employee.model';
import { AccountService } from '../../../core/services/account.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { EmployeeService } from '../../../core/services/employee.service';
import { LoadingService } from '../../../core/services/loading.service';
import { NotificationService } from '../../../core/services/notification.service';
import { fromIsoDate, toIsoDate } from '../../../core/utils/date.util';
import { translatedOptions } from '../../../shared/utils/translated-options';

type AccountMode = 'new' | 'existing';

@Component({
  selector: 'app-employee-detail-page',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    TranslatePipe,
    ButtonModule,
    InputTextModule,
    DatePickerModule,
    PasswordModule,
    SelectModule,
    TagModule,
    DialogModule,
  ],
  templateUrl: './employee-detail-page.html',
})
export class EmployeeDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly employeeService = inject(EmployeeService);
  private readonly accountService = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly notification = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  private readonly loadingService = inject(LoadingService);

  readonly loading = this.loadingService.isLoading;

  private employeeId: number | null = null;
  readonly isNew = computed(() => this.employeeId === null);
  readonly employee = signal<EmployeeResponse | null>(null);

  readonly fullName = signal('');
  readonly dateOfBirth = signal<Date | null>(null);
  readonly idCardNumber = signal('');
  readonly phoneNumber = signal('');
  readonly email = signal('');
  readonly submitted = signal(false);

  readonly username = signal('');
  readonly password = signal('');
  readonly role = signal<Role>(Role.USER);

  readonly roleOptions = translatedOptions(this.translate, [
    { labelKey: 'ROLE.ADMIN_TONG', value: Role.ADMIN_TONG },
    { labelKey: 'ROLE.ADMIN_CAP_1', value: Role.ADMIN_CAP_1 },
    { labelKey: 'ROLE.USER', value: Role.USER },
  ]);

  readonly accountMode = signal<AccountMode>('new');
  readonly accountModeOptions = translatedOptions(this.translate, [
    { labelKey: 'EMPLOYEE.ACCOUNT_MODE_NEW', value: 'new' as AccountMode },
    { labelKey: 'EMPLOYEE.ACCOUNT_MODE_EXISTING', value: 'existing' as AccountMode },
  ]);
  readonly unassignedAccounts = signal<AccountResponse[]>([]);
  readonly existingAccountId = signal<number | null>(null);
  readonly unassignedAccountOptions = computed(() =>
    this.unassignedAccounts().map((a) => ({
      label: a.fullName ? `${a.fullName} (${a.username})` : a.username,
      value: a.id,
    })),
  );

  readonly passwordDialogVisible = signal(false);
  readonly newPassword = signal('');
  readonly passwordSubmitted = signal(false);

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam || idParam === 'new') {
      this.accountService.listUnassigned().subscribe({
        next: (accounts) => this.unassignedAccounts.set(accounts),
        error: () => {},
      });
      return;
    }

    this.employeeId = Number(idParam);
    this.loadEmployee(this.employeeId);
  }

  private loadEmployee(id: number): void {
    this.employeeService.get(id).subscribe({
      next: (employee) => {
        this.employee.set(employee);
        this.fullName.set(employee.fullName);
        this.dateOfBirth.set(fromIsoDate(employee.dateOfBirth));
        this.idCardNumber.set(employee.idCardNumber);
        this.phoneNumber.set(employee.phoneNumber);
        this.email.set(employee.email);
      },
      error: () => {},
    });
  }

  save(): void {
    this.submitted.set(true);
    const dob = this.dateOfBirth();
    const personalValid = this.fullName() && dob && this.idCardNumber() && this.phoneNumber() && this.email();
    if (!personalValid) {
      return;
    }

    if (this.isNew()) {
      const usesExisting = this.accountMode() === 'existing';
      if (usesExisting ? !this.existingAccountId() : !this.username() || this.password().length < 6) {
        return;
      }
      this.confirmService.confirm(this.translate.instant('COMMON.SAVE_CONFIRM'), () => {
        this.employeeService
          .create({
            fullName: this.fullName(),
            dateOfBirth: toIsoDate(dob!),
            idCardNumber: this.idCardNumber(),
            phoneNumber: this.phoneNumber(),
            email: this.email(),
            existingAccountId: usesExisting ? this.existingAccountId() : null,
            newAccount: usesExisting ? null : { username: this.username(), password: this.password(), role: this.role() },
          })
          .subscribe({
            next: () => {
              this.notification.success(this.translate.instant('EMPLOYEE.CREATE_SUCCESS'));
              this.router.navigate(['/employees']);
            },
            error: () => {},
          });
      });
      return;
    }

    this.confirmService.confirm(this.translate.instant('COMMON.SAVE_CONFIRM'), () => {
      this.employeeService
        .update(this.employeeId!, {
          fullName: this.fullName(),
          dateOfBirth: toIsoDate(dob!),
          idCardNumber: this.idCardNumber(),
          phoneNumber: this.phoneNumber(),
          email: this.email(),
        })
        .subscribe({
          next: () => {
            this.notification.success(this.translate.instant('EMPLOYEE.UPDATE_SUCCESS'));
            this.router.navigate(['/employees']);
          },
          error: () => {},
        });
    });
  }

  remove(): void {
    if (!this.employeeId) {
      return;
    }
    this.confirmService.confirm(this.translate.instant('EMPLOYEE.DELETE_CONFIRM'), () => {
      this.employeeService.delete(this.employeeId!).subscribe({
        next: () => {
          this.notification.success(this.translate.instant('EMPLOYEE.DELETE_SUCCESS'));
          this.router.navigate(['/employees']);
        },
        error: () => {},
      });
    });
  }

  toggleActive(): void {
    const employee = this.employee();
    if (!employee) {
      return;
    }
    const nextActive = !employee.active;
    const messageKey = nextActive ? 'ACCOUNT.ACTIVATE_CONFIRM' : 'ACCOUNT.DEACTIVATE_CONFIRM';

    this.confirmService.confirm(this.translate.instant(messageKey), () => {
      this.accountService.setActive(employee.accountId, nextActive).subscribe({
        next: () => {
          this.loadEmployee(employee.id);
          this.notification.success(this.translate.instant('COMMON.SAVE'));
        },
        error: () => {},
      });
    });
  }

  openPasswordDialog(): void {
    this.newPassword.set('');
    this.passwordSubmitted.set(false);
    this.passwordDialogVisible.set(true);
  }

  confirmChangePassword(): void {
    this.passwordSubmitted.set(true);
    const employee = this.employee();
    if (this.newPassword().length < 6 || !employee) {
      return;
    }

    this.accountService.changePassword(employee.accountId, this.newPassword()).subscribe({
      next: () => {
        this.passwordDialogVisible.set(false);
        this.notification.success(this.translate.instant('ACCOUNT.CHANGE_PASSWORD_SUCCESS'));
      },
      error: () => {},
    });
  }
}

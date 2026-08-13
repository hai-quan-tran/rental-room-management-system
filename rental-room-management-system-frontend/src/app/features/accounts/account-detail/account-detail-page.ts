import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { Role } from '../../../core/enums/role.enum';
import { AccountResponse } from '../../../core/models/account.model';
import { AccountService } from '../../../core/services/account.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { LoadingService } from '../../../core/services/loading.service';
import { NotificationService } from '../../../core/services/notification.service';
import { translatedOptions } from '../../../shared/utils/translated-options';

@Component({
  selector: 'app-account-detail-page',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    TranslatePipe,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    SelectModule,
    ToggleSwitchModule,
    DialogModule,
  ],
  templateUrl: './account-detail-page.html',
})
export class AccountDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly accountService = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly notification = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  private readonly loadingService = inject(LoadingService);

  readonly loading = this.loadingService.isLoading;

  private accountId: number | null = null;
  readonly isNew = computed(() => this.accountId === null);
  readonly account = signal<AccountResponse | null>(null);

  readonly username = signal('');
  readonly password = signal('');
  readonly role = signal<Role>(Role.ADMIN_CAP_1);
  readonly submitted = signal(false);

  readonly roleOptions = translatedOptions(this.translate, [
    { labelKey: 'ROLE.ADMIN_TONG', value: Role.ADMIN_TONG },
    { labelKey: 'ROLE.ADMIN_CAP_1', value: Role.ADMIN_CAP_1 },
    { labelKey: 'ROLE.USER', value: Role.USER },
  ]);

  readonly passwordDialogVisible = signal(false);
  readonly newPassword = signal('');
  readonly passwordSubmitted = signal(false);

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam || idParam === 'new') {
      return;
    }

    this.accountId = Number(idParam);
    this.accountService.get(this.accountId).subscribe({
      next: (account) => {
        this.account.set(account);
        this.username.set(account.username);
        this.role.set(account.role);
      },
      error: () => {},
    });
  }

  save(): void {
    this.submitted.set(true);
    if (!this.username() || (this.isNew() && this.password().length < 6)) {
      return;
    }

    if (this.isNew()) {
      this.accountService
        .create({
          username: this.username(),
          password: this.password(),
          role: this.role(),
        })
        .subscribe({
          next: () => {
            this.notification.success(this.translate.instant('ACCOUNT.CREATE_SUCCESS'));
            this.router.navigate(['/accounts']);
          },
          error: () => {},
        });
      return;
    }

    this.accountService
      .update(this.accountId!, { username: this.username(), role: this.role() })
      .subscribe({
        next: () => {
          this.notification.success(this.translate.instant('ACCOUNT.UPDATE_SUCCESS'));
          this.router.navigate(['/accounts']);
        },
        error: () => {},
      });
  }

  toggleActive(): void {
    const account = this.account();
    if (!account) {
      return;
    }
    const nextActive = !account.active;
    const messageKey = nextActive ? 'ACCOUNT.ACTIVATE_CONFIRM' : 'ACCOUNT.DEACTIVATE_CONFIRM';

    this.confirmService.confirm(this.translate.instant(messageKey), () => {
      this.accountService.setActive(account.id, nextActive).subscribe({
        next: (updated) => {
          this.account.set(updated);
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
    if (this.newPassword().length < 6 || !this.accountId) {
      return;
    }

    this.accountService.changePassword(this.accountId, this.newPassword()).subscribe({
      next: () => {
        this.passwordDialogVisible.set(false);
        this.notification.success(this.translate.instant('ACCOUNT.CHANGE_PASSWORD_SUCCESS'));
      },
      error: () => {},
    });
  }
}

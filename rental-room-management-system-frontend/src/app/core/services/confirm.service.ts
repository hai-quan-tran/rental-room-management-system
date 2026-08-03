import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';

/**
 * Thin wrapper around PrimeNG's ConfirmationService so call sites don't need
 * to know its full options shape or re-supply translated button labels every
 * time — just a message and what to do on OK/Cancel.
 *
 * Requires a single `<p-confirmDialog />` mounted once (see the root `App`
 * component) — this service only pushes configuration to it, it renders
 * nothing itself.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly confirmationService = inject(ConfirmationService);
  private readonly translate = inject(TranslateService);

  confirm(message: string, onAccept: () => void, onReject?: () => void): void {
    this.confirmationService.confirm({
      message,
      header: this.translate.instant('COMMON.CONFIRM_TITLE'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translate.instant('COMMON.OK'),
      rejectLabel: this.translate.instant('COMMON.CANCEL'),
      rejectButtonProps: { severity: 'danger' },
      accept: onAccept,
      reject: onReject,
    });
  }
}

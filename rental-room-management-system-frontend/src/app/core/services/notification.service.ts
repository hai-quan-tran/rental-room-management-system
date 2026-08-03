import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MessageService } from 'primeng/api';

/**
 * Thin wrapper around PrimeNG's MessageService so call sites just pass a
 * message string instead of building the full toast options object.
 * Requires a single `<p-toast />` mounted once (see the root `App` component).
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly messageService = inject(MessageService);
  private readonly translate = inject(TranslateService);

  success(message: string): void {
    this.messageService.add({
      severity: 'success',
      summary: this.translate.instant('COMMON.SUCCESS'),
      detail: message,
      life: 3000,
    });
  }

  error(message: string): void {
    this.messageService.add({
      severity: 'error',
      summary: this.translate.instant('COMMON.ERROR'),
      detail: message,
      life: 6000,
    });
  }
}

import { Component, computed, inject, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { Popover, PopoverModule } from 'primeng/popover';
import { SelectButtonModule } from 'primeng/selectbutton';

import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { AppLang, LanguageService } from '../../core/services/language.service';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [FormsModule, TranslatePipe, AvatarModule, ButtonModule, PopoverModule, SelectButtonModule],
  templateUrl: './app-topbar.html',
  styleUrl: './app-topbar.scss',
})
export class AppTopbar {
  private readonly languageService = inject(LanguageService);
  private readonly authService = inject(AuthService);
  private readonly confirmService = inject(ConfirmService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);

  private readonly userMenuRef = viewChild<Popover>('userMenu');

  readonly langOptions: { label: string; value: AppLang }[] = [
    { label: 'VI', value: 'vi' },
    { label: 'EN', value: 'en' },
  ];

  readonly currentLang = this.languageService.currentLang;
  readonly currentUser = this.authService.currentUser;
  readonly userInitial = computed(() => (this.currentUser()?.username ?? '?').charAt(0).toUpperCase());

  onLangChange(lang: AppLang): void {
    this.languageService.setLanguage(lang);
  }

  toggleUserMenu(event: Event): void {
    this.userMenuRef()?.toggle(event);
  }

  logout(): void {
    this.userMenuRef()?.hide();
    this.confirmService.confirm(this.translate.instant('AUTH.LOGOUT_CONFIRM'), () => {
      this.authService.logout().subscribe({
        complete: () => this.router.navigate(['/login']),
        error: () => this.router.navigate(['/login']),
      });
    });
  }
}

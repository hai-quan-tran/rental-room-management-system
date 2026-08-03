import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SelectButtonModule } from 'primeng/selectbutton';

import { AppLang, LanguageService } from '../../../core/services/language.service';
import { AuthService } from '../../../core/services/auth.service';
import { LoadingService } from '../../../core/services/loading.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [FormsModule, TranslatePipe, ButtonModule, CardModule, InputTextModule, PasswordModule, SelectButtonModule],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage {
  private readonly authService = inject(AuthService);
  private readonly languageService = inject(LanguageService);
  private readonly loadingService = inject(LoadingService);
  private readonly router = inject(Router);

  readonly username = signal('');
  readonly password = signal('');
  readonly loading = this.loadingService.isLoading;

  readonly currentLang = this.languageService.currentLang;
  readonly langOptions: { label: string; value: AppLang }[] = [
    { label: 'VI', value: 'vi' },
    { label: 'EN', value: 'en' },
  ];

  onLangChange(lang: AppLang): void {
    this.languageService.setLanguage(lang);
  }

  submit(): void {
    if (!this.username() || !this.password()) {
      return;
    }

    this.authService.login({ username: this.username(), password: this.password() }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: () => {
        // Failure is already surfaced globally by the error interceptor's toast.
      },
    });
  }
}

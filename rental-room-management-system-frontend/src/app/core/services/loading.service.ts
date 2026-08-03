import { Injectable, computed, signal } from '@angular/core';

/**
 * Counter-based (not boolean) so N concurrent requests only clear the
 * indicator once the last one finishes — a plain boolean would flip to
 * false as soon as any single request completed.
 */
@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly activeRequests = signal(0);

  readonly isLoading = computed(() => this.activeRequests() > 0);

  start(): void {
    this.activeRequests.update((count) => count + 1);
  }

  stop(): void {
    this.activeRequests.update((count) => Math.max(0, count - 1));
  }
}

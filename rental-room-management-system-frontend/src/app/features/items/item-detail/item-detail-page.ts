import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';

import { ConfirmService } from '../../../core/services/confirm.service';
import { ItemService } from '../../../core/services/item.service';
import { LoadingService } from '../../../core/services/loading.service';
import { NotificationService } from '../../../core/services/notification.service';

@Component({
  selector: 'app-item-detail-page',
  standalone: true,
  imports: [FormsModule, RouterLink, TranslatePipe, ButtonModule, InputTextModule, InputNumberModule],
  templateUrl: './item-detail-page.html',
})
export class ItemDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly itemService = inject(ItemService);
  private readonly translate = inject(TranslateService);
  private readonly notification = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  private readonly loadingService = inject(LoadingService);

  readonly loading = this.loadingService.isLoading;

  private itemId: number | null = null;
  private branchId!: number;
  readonly isNew = computed(() => this.itemId === null);
  readonly branchName = signal('');

  readonly name = signal('');
  readonly price = signal<number | null>(null);
  readonly quantityAvailable = signal<number | null>(null);
  readonly defaultQuantityPerRoom = signal<number | null>(null);
  readonly submitted = signal(false);

  readonly defaultQuantityExceedsStock = computed(() => {
    const defaultQty = this.defaultQuantityPerRoom();
    const available = this.quantityAvailable();
    return defaultQty !== null && available !== null && defaultQty > available;
  });

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam || idParam === 'new') {
      const branchIdParam = this.route.snapshot.queryParamMap.get('branchId');
      this.branchId = Number(branchIdParam);
      return;
    }

    this.itemId = Number(idParam);
    this.itemService.get(this.itemId).subscribe({
      next: (item) => {
        this.name.set(item.name);
        this.price.set(item.price);
        this.quantityAvailable.set(item.quantityAvailable);
        this.defaultQuantityPerRoom.set(item.defaultQuantityPerRoom);
        this.branchId = item.branchId;
        this.branchName.set(item.branchName);
      },
      error: () => {},
    });
  }

  save(): void {
    this.submitted.set(true);
    if (
      !this.name() ||
      this.price() === null ||
      this.quantityAvailable() === null ||
      !this.defaultQuantityPerRoom() ||
      this.defaultQuantityExceedsStock()
    ) {
      return;
    }

    const request = {
      name: this.name(),
      price: this.price()!,
      quantityAvailable: this.quantityAvailable()!,
      defaultQuantityPerRoom: this.defaultQuantityPerRoom()!,
    };

    const save$ = this.isNew()
      ? this.itemService.create(this.branchId, request)
      : this.itemService.update(this.itemId!, request);

    save$.subscribe({
      next: () => {
        this.notification.success(this.translate.instant(this.isNew() ? 'ITEM.CREATE_SUCCESS' : 'ITEM.UPDATE_SUCCESS'));
        this.router.navigate(['/items']);
      },
      error: () => {},
    });
  }

  remove(): void {
    if (!this.itemId) {
      return;
    }
    this.confirmService.confirm(this.translate.instant('ITEM.DELETE_CONFIRM'), () => {
      this.itemService.delete(this.itemId!).subscribe({
        next: () => {
          this.notification.success(this.translate.instant('ITEM.DELETE_SUCCESS'));
          this.router.navigate(['/items']);
        },
        error: () => {},
      });
    });
  }
}

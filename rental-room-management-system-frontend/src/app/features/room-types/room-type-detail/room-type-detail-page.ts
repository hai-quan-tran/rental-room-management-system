import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';

import { HandoverItemRequest } from '../../../core/models/room-type.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { LoadingService } from '../../../core/services/loading.service';
import { NotificationService } from '../../../core/services/notification.service';
import { RoomTypeService } from '../../../core/services/room-type.service';

interface HandoverItemRow extends HandoverItemRequest {
  key: number;
}

let rowKeySeq = 0;

@Component({
  selector: 'app-room-type-detail-page',
  standalone: true,
  imports: [FormsModule, RouterLink, TranslatePipe, ButtonModule, InputTextModule, TextareaModule, InputNumberModule],
  templateUrl: './room-type-detail-page.html',
})
export class RoomTypeDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly roomTypeService = inject(RoomTypeService);
  private readonly translate = inject(TranslateService);
  private readonly notification = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  private readonly loadingService = inject(LoadingService);

  readonly loading = this.loadingService.isLoading;

  private roomTypeId: number | null = null;
  readonly isNew = computed(() => this.roomTypeId === null);

  readonly name = signal('');
  readonly area = signal('');
  readonly description = signal('');
  readonly submitted = signal(false);

  readonly handoverItems = signal<HandoverItemRow[]>([]);

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam || idParam === 'new') {
      return;
    }

    this.roomTypeId = Number(idParam);
    this.roomTypeService.get(this.roomTypeId).subscribe({
      next: (roomType) => {
        this.name.set(roomType.name);
        this.area.set(roomType.area ?? '');
        this.description.set(roomType.description ?? '');
        this.handoverItems.set(
          roomType.handoverItems.map((item) => ({
            itemName: item.itemName,
            quantity: item.quantity,
            note: item.note,
            key: rowKeySeq++,
          })),
        );
      },
      error: () => {},
    });
  }

  addItem(): void {
    this.handoverItems.update((items) => [...items, { itemName: '', quantity: 1, note: null, key: rowKeySeq++ }]);
  }

  removeItem(key: number): void {
    this.handoverItems.update((items) => items.filter((item) => item.key !== key));
  }

  save(): void {
    this.submitted.set(true);
    if (!this.name() || this.handoverItems().some((item) => !item.itemName || !item.quantity || item.quantity < 1)) {
      return;
    }

    const request = { name: this.name(), area: this.area() || null, description: this.description() || null };
    const items: HandoverItemRequest[] = this.handoverItems().map(({ itemName, quantity, note }) => ({
      itemName,
      quantity,
      note: note || null,
    }));

    const save$ = this.isNew() ? this.roomTypeService.create(request) : this.roomTypeService.update(this.roomTypeId!, request);

    save$.subscribe({
      next: (roomType) => {
        this.roomTypeService.replaceHandoverItems(roomType.id, items).subscribe({
          next: () => {
            this.notification.success(
              this.translate.instant(this.isNew() ? 'ROOM_TYPE.CREATE_SUCCESS' : 'ROOM_TYPE.UPDATE_SUCCESS'),
            );
            this.router.navigate(['/room-types']);
          },
          error: () => {},
        });
      },
      error: () => {},
    });
  }

  remove(): void {
    if (!this.roomTypeId) {
      return;
    }
    this.confirmService.confirm(this.translate.instant('ROOM_TYPE.DELETE_CONFIRM'), () => {
      this.roomTypeService.delete(this.roomTypeId!).subscribe({
        next: () => {
          this.notification.success(this.translate.instant('ROOM_TYPE.DELETE_SUCCESS'));
          this.router.navigate(['/room-types']);
        },
        error: () => {},
      });
    });
  }
}

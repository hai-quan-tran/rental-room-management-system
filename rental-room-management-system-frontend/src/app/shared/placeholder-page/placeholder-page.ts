import { Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-placeholder-page',
  standalone: true,
  imports: [CardModule, TranslatePipe],
  templateUrl: './placeholder-page.html',
})
export class PlaceholderPage {
  readonly titleKey = input.required<string>();
}

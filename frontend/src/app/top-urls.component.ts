import { Component, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShortUrlService } from './short-url.service';
import { TopShortUrl } from './short-url.models';

@Component({
  selector: 'app-top-urls',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="card">
      <h2>熱門短網址<span class="muted"> · 最近 7 天</span></h2>

      @if (loading()) {
        <p class="muted">載入中…</p>
      } @else if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      } @else if (items().length === 0) {
        <p class="muted">這段期間還沒有任何點擊。建立一個短網址並點它幾次看看。</p>
      } @else {
        <table>
          <thead>
            <tr><th>短碼</th><th>原始網址</th><th class="num">點擊</th></tr>
          </thead>
          <tbody>
            @for (item of items(); track item.shortCode) {
              <tr [class.selected]="item.shortCode === selectedCode()"
                  (click)="shortCodeSelected.emit(item.shortCode)">
                <td><code>{{ item.shortCode }}</code></td>
                <td class="truncate" [title]="item.originalUrl">{{ item.originalUrl }}</td>
                <td class="num">{{ item.clicks }}</td>
              </tr>
            }
          </tbody>
        </table>
        <p class="muted">點一列可以看它的每日趨勢</p>
      }
    </section>
  `,
  styleUrl: './app.component.css'
})
export class TopUrlsComponent {
  private readonly service = inject(ShortUrlService);

  readonly selectedCode = input<string | null>(null);
  readonly shortCodeSelected = output<string>();

  readonly items = signal<TopShortUrl[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.topStats().subscribe({
      next: (stats) => {
        this.items.set(stats.items);
        this.loading.set(false);
      },
      error: (err: Error) => {
        this.error.set(err.message);
        this.loading.set(false);
      }
    });
  }
}

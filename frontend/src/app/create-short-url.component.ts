import { Component, inject, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShortUrlService } from './short-url.service';
import { CreateShortUrlRequest, ShortUrl } from './short-url.models';

@Component({
  selector: 'app-create-short-url',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <form class="card" (ngSubmit)="submit()">
      <h2>建立短網址</h2>

      <label for="originalUrl">原始網址</label>
      <input id="originalUrl" name="originalUrl" type="url" required
             placeholder="https://example.com/some/very/long/path"
             [(ngModel)]="originalUrl" />

      <details>
        <summary>進階選項</summary>
        <label for="customAlias">自訂短碼（4~16 碼英數字，留空則自動產生）</label>
        <input id="customAlias" name="customAlias" [(ngModel)]="customAlias" placeholder="mylink" />

        <label for="expireAt">有效期限（留空表示永不過期）</label>
        <input id="expireAt" name="expireAt" type="datetime-local" [(ngModel)]="expireAt" />
      </details>

      <button type="submit" [disabled]="submitting() || !originalUrl">
        {{ submitting() ? '建立中…' : '建立' }}
      </button>

      @if (error(); as message) {
        <p class="error" role="alert">{{ message }}</p>
      }

      @if (created(); as result) {
        <div class="result">
          <a [href]="result.shortUrl" target="_blank" rel="noopener">{{ result.shortUrl }}</a>
          <button type="button" class="link" (click)="copy(result.shortUrl)">
            {{ copied() ? '已複製' : '複製' }}
          </button>
          <p class="muted">{{ result.originalUrl }}</p>
        </div>
      }
    </form>
  `,
  styleUrl: './app.component.css'
})
export class CreateShortUrlComponent {
  private readonly service = inject(ShortUrlService);

  /** 建立成功後通知父元件更新排行 */
  readonly urlCreated = output<ShortUrl>();

  originalUrl = '';
  customAlias = '';
  expireAt = '';

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly created = signal<ShortUrl | null>(null);
  readonly copied = signal(false);

  submit(): void {
    if (!this.originalUrl) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.copied.set(false);

    const request: CreateShortUrlRequest = { originalUrl: this.originalUrl };
    if (this.customAlias) {
      request.customAlias = this.customAlias;
    }
    if (this.expireAt) {
      // datetime-local 給的是 2026-09-23T10:00，後端要的是 ISO LocalDateTime，補上秒數
      request.expireAt = `${this.expireAt}:00`;
    }

    this.service.create(request).subscribe({
      next: (result) => {
        this.created.set(result);
        this.submitting.set(false);
        this.customAlias = '';
        this.expireAt = '';
        this.urlCreated.emit(result);
      },
      error: (err: Error) => {
        this.error.set(err.message);
        this.submitting.set(false);
      }
    });
  }

  copy(text: string): void {
    navigator.clipboard.writeText(text).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }
}

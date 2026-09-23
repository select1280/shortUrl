import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShortUrlService } from './short-url.service';
import { DailyStats } from './short-url.models';

/** SVG 的內部座標系，跟實際顯示大小無關；用 viewBox 縮放，所以圖會隨容器自動伸縮 */
const WIDTH = 560;
const HEIGHT = 220;
const PADDING = { top: 16, right: 16, bottom: 28, left: 36 };

interface Point {
  x: number;
  y: number;
  date: string;
  clicks: number;
}

@Component({
  selector: 'app-daily-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="card">
      <h2>每日點擊<span class="muted"> · {{ shortCode() ?? '尚未選擇' }}</span></h2>

      @if (!shortCode()) {
        <p class="muted">從右邊的熱門清單點一個短碼。</p>
      } @else if (loading()) {
        <p class="muted">載入中…</p>
      } @else if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      } @else if (stats()) {
        <p class="total">{{ stats()!.totalClicks }}
          <span class="muted">次點擊 · {{ stats()!.from }} ~ {{ stats()!.to }}</span>
        </p>

        <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" class="chart" role="img"
             [attr.aria-label]="'每日點擊趨勢，共 ' + stats()!.totalClicks + ' 次'">
          <!-- 水平格線與 Y 軸刻度 -->
          @for (tick of yTicks(); track tick.value) {
            <line [attr.x1]="padding.left" [attr.x2]="width - padding.right"
                  [attr.y1]="tick.y" [attr.y2]="tick.y" class="grid" />
            <text [attr.x]="padding.left - 6" [attr.y]="tick.y + 4" class="axis" text-anchor="end">
              {{ tick.value }}
            </text>
          }

          <polyline [attr.points]="linePoints()" class="line" />

          @for (point of points(); track point.date) {
            <circle [attr.cx]="point.x" [attr.cy]="point.y" r="3" class="dot">
              <title>{{ point.date }}：{{ point.clicks }} 次</title>
            </circle>
          }

          <!-- 只標頭尾兩天，中間的日期標籤會擠在一起 -->
          @if (points().length > 0) {
            <text [attr.x]="points()[0].x" [attr.y]="height - 8" class="axis" text-anchor="start">
              {{ shortDate(points()[0].date) }}
            </text>
            <text [attr.x]="points()[points().length - 1].x" [attr.y]="height - 8" class="axis" text-anchor="end">
              {{ shortDate(points()[points().length - 1].date) }}
            </text>
          }
        </svg>
      }
    </section>
  `,
  styleUrl: './app.component.css'
})
export class DailyChartComponent {
  private readonly service = inject(ShortUrlService);

  readonly shortCode = input<string | null>(null);

  readonly stats = signal<DailyStats | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly width = WIDTH;
  readonly height = HEIGHT;
  readonly padding = PADDING;

  /** Y 軸最大值取至少 1，否則全部是 0 的時候會除以零 */
  private readonly maxClicks = computed(() =>
    Math.max(1, ...(this.stats()?.days ?? []).map((day) => day.clicks)));

  readonly points = computed<Point[]>(() => {
    const days = this.stats()?.days ?? [];
    if (days.length === 0) {
      return [];
    }
    const plotWidth = WIDTH - PADDING.left - PADDING.right;
    const plotHeight = HEIGHT - PADDING.top - PADDING.bottom;
    const step = days.length > 1 ? plotWidth / (days.length - 1) : 0;

    return days.map((day, index) => ({
      x: PADDING.left + step * index,
      y: PADDING.top + plotHeight * (1 - day.clicks / this.maxClicks()),
      date: day.date,
      clicks: day.clicks
    }));
  });

  readonly linePoints = computed(() =>
    this.points().map((point) => `${point.x},${point.y}`).join(' '));

  readonly yTicks = computed(() => {
    const max = this.maxClicks();
    const plotHeight = HEIGHT - PADDING.top - PADDING.bottom;
    // 最多 4 格，且刻度是整數，點擊數不會有小數
    const step = Math.max(1, Math.ceil(max / 4));
    const ticks: { value: number; y: number }[] = [];
    for (let value = 0; value <= max; value += step) {
      ticks.push({ value, y: PADDING.top + plotHeight * (1 - value / max) });
    }
    return ticks;
  });

  constructor() {
    // 選到的短碼一變就重新載入。
    // load() 會寫 loading / error 等 signal，預設不允許在 effect 裡寫 signal（NG0600），
    // 這裡是「輸入變了就去抓資料」的典型情境，所以明確開啟 allowSignalWrites。
    effect(() => {
      const code = this.shortCode();
      if (code) {
        this.load(code);
      } else {
        this.stats.set(null);
      }
    }, { allowSignalWrites: true });
  }

  shortDate(isoDate: string): string {
    return isoDate.slice(5);   // 2026-09-23 → 09-23
  }

  private load(shortCode: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.dailyStats(shortCode).subscribe({
      next: (data) => {
        this.stats.set(data);
        this.loading.set(false);
      },
      error: (err: Error) => {
        this.error.set(err.message);
        this.loading.set(false);
      }
    });
  }
}

import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { ApiError, CreateShortUrlRequest, DailyStats, ShortUrl, TopStats } from './short-url.models';

@Injectable({ providedIn: 'root' })
export class ShortUrlService {
  private readonly http = inject(HttpClient);
  /** 相對路徑：dev 由 proxy.conf.json 轉給 :8080，正式環境由 nginx 轉給 app 容器 */
  private readonly baseUrl = '/api';

  create(request: CreateShortUrlRequest): Observable<ShortUrl> {
    return this.http
      .post<ShortUrl>(`${this.baseUrl}/urls`, request)
      .pipe(catchError(this.toMessage));
  }

  get(shortCode: string): Observable<ShortUrl> {
    return this.http
      .get<ShortUrl>(`${this.baseUrl}/urls/${shortCode}`)
      .pipe(catchError(this.toMessage));
  }

  dailyStats(shortCode: string): Observable<DailyStats> {
    return this.http
      .get<DailyStats>(`${this.baseUrl}/urls/${shortCode}/stats/daily`)
      .pipe(catchError(this.toMessage));
  }

  topStats(limit = 10): Observable<TopStats> {
    return this.http
      .get<TopStats>(`${this.baseUrl}/stats/top`, { params: { limit } })
      .pipe(catchError(this.toMessage));
  }

  /** 後端錯誤格式統一，這裡把它轉成單純的訊息字串，元件只要顯示就好 */
  private toMessage(response: HttpErrorResponse) {
    const body = response.error as ApiError | null;
    const message = body?.message ?? (response.status === 0
      ? '無法連線到服務，請確認後端是否啟動'
      : `發生未預期的錯誤（HTTP ${response.status}）`);
    return throwError(() => new Error(message));
  }
}

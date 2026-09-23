export interface ShortUrl {
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  expireAt: string | null;
  createdAt: string;
  clickCount: number;
}

export interface CreateShortUrlRequest {
  originalUrl: string;
  customAlias?: string;
  expireAt?: string;
}

export interface DailyClicks {
  date: string;
  clicks: number;
}

export interface DailyStats {
  shortCode: string;
  from: string;
  to: string;
  totalClicks: number;
  days: DailyClicks[];
}

export interface TopShortUrl {
  shortCode: string;
  originalUrl: string;
  clicks: number;
}

export interface TopStats {
  from: string;
  to: string;
  items: TopShortUrl[];
}

/** 後端 GlobalExceptionHandler 回傳的錯誤格式 */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
}

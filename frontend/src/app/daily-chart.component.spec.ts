import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { DailyChartComponent } from './daily-chart.component';
import { DailyStats } from './short-url.models';

const STATS: DailyStats = {
  shortCode: '000001',
  from: '2026-09-21',
  to: '2026-09-23',
  totalClicks: 6,
  days: [
    { date: '2026-09-21', clicks: 4 },
    { date: '2026-09-22', clicks: 0 },
    { date: '2026-09-23', clicks: 2 }
  ]
};

describe('DailyChartComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DailyChartComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('沒選短碼時不打 API', () => {
    const fixture = TestBed.createComponent(DailyChartComponent);
    fixture.componentRef.setInput('shortCode', null);
    fixture.detectChanges();
    TestBed.flushEffects();

    http.expectNone(() => true);
    expect(fixture.nativeElement.textContent).toContain('從右邊的熱門清單點一個短碼');
  });

  it('把每一天畫成一個點，包含點擊數為 0 的那天', () => {
    const fixture = TestBed.createComponent(DailyChartComponent);
    fixture.componentRef.setInput('shortCode', '000001');
    fixture.detectChanges();
    // effect 預設是非同步排程的，測試裡要手動 flush，否則請求還沒送出就斷言了
    TestBed.flushEffects();
    http.expectOne('/api/urls/000001/stats/daily').flush(STATS);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('circle.dot').length).toBe(3);
    expect(fixture.nativeElement.textContent).toContain('6');
  });

  it('點擊數最高的那天畫在最上面，0 的那天畫在最下面', () => {
    const fixture = TestBed.createComponent(DailyChartComponent);
    fixture.componentRef.setInput('shortCode', '000001');
    fixture.detectChanges();
    // effect 預設是非同步排程的，測試裡要手動 flush，否則請求還沒送出就斷言了
    TestBed.flushEffects();
    http.expectOne('/api/urls/000001/stats/daily').flush(STATS);
    fixture.detectChanges();

    const [highest, zero] = fixture.componentInstance.points();
    // SVG 的 y 軸向下增加，所以數值越大 y 越小
    expect(highest.y).toBeLessThan(zero.y);
  });

  it('全部都是 0 時不會因為除以零而畫爛', () => {
    const fixture = TestBed.createComponent(DailyChartComponent);
    fixture.componentRef.setInput('shortCode', '000001');
    fixture.detectChanges();
    // effect 預設是非同步排程的，測試裡要手動 flush，否則請求還沒送出就斷言了
    TestBed.flushEffects();
    http.expectOne('/api/urls/000001/stats/daily').flush({
      ...STATS,
      totalClicks: 0,
      days: STATS.days.map((day) => ({ ...day, clicks: 0 }))
    });
    fixture.detectChanges();

    const ys = fixture.componentInstance.points().map((point) => point.y);
    expect(ys.every((y) => Number.isFinite(y))).toBe(true);
  });
});

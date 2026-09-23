import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AppComponent } from './app.component';
import { TopStats } from './short-url.models';

const EMPTY_TOP: TopStats = { from: '2026-09-17', to: '2026-09-23', items: [] };

describe('AppComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('載入時就向後端要熱門排行', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    http.expectOne((request) => request.url === '/api/stats/top').flush(EMPTY_TOP);
  });

  it('沒有點擊資料時顯示提示，而不是空白表格', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    http.expectOne((request) => request.url === '/api/stats/top').flush(EMPTY_TOP);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('這段期間還沒有任何點擊');
  });

  it('後端回錯誤時顯示錯誤訊息', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    http.expectOne((request) => request.url === '/api/stats/top')
      .flush({ status: 400, message: 'limit 必須介於 1 ~ 100' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('limit 必須介於 1 ~ 100');
  });
});

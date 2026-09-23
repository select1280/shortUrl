import { Component, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CreateShortUrlComponent } from './create-short-url.component';
import { TopUrlsComponent } from './top-urls.component';
import { DailyChartComponent } from './daily-chart.component';
import { ShortUrl } from './short-url.models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, CreateShortUrlComponent, TopUrlsComponent, DailyChartComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  readonly selectedCode = signal<string | null>(null);

  private readonly topUrls = viewChild.required(TopUrlsComponent);

  onCreated(url: ShortUrl): void {
    this.selectedCode.set(url.shortCode);
    this.topUrls().reload();
  }
}

import { Component, OnInit, OnDestroy, HostListener, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';

// Import shared components
import { AppIconComponent } from '../shared/app-icon/app-icon.component';
import { ActionButtonComponent } from './action-button.component';

// Import HTTP status service
import { HttpStatusService } from '../../core/services';

@Component({
  selector: 'app-not-found',
  imports: [
    CommonModule,
    RouterModule,
    AppIconComponent,
    ActionButtonComponent
  ],
  templateUrl: './not-found.component.html',
  styleUrls: ['./not-found.component.scss']
})
export class NotFoundComponent implements OnInit, OnDestroy {
  isNavigatingHome = false;
  isNavigatingValuation = false;
  private readonly isBrowser: boolean;
  private readonly prefersReducedMotion: MediaQueryList | null;

  constructor(
    private router: Router,
    private meta: Meta,
    private titleService: Title,
    private httpStatusService: HttpStatusService,
    @Inject(PLATFORM_ID) platformId: object
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
    this.prefersReducedMotion = this.isBrowser
      ? window.matchMedia('(prefers-reduced-motion: reduce)')
      : null;
  }

  ngOnInit(): void {
    this.setMetadata();
    this.setHttpStatus();
  }

  ngOnDestroy(): void {
    // Cleanup is handled automatically by removing @HostListener
  }

  @HostListener('mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    if (!this.isBrowser) {
      return;
    }

    // Skip animation if user prefers reduced motion
    if (this.prefersReducedMotion?.matches) {
      return;
    }

    const cards = document.querySelectorAll('.quick-link-card');
    cards.forEach(card => {
      if (card instanceof HTMLElement) {
        const rect = card.getBoundingClientRect();
        const x = ((event.clientX - rect.left) / rect.width) * 100;
        const y = ((event.clientY - rect.top) / rect.height) * 100;
        card.style.setProperty('--mouse-x', `${x}%`);
        card.style.setProperty('--mouse-y', `${y}%`);
      }
    });
  }

  private setMetadata(): void {
    this.titleService.setTitle('Page Not Found - StockValuation.io');

    this.meta.updateTag({
      name: 'description',
      content: 'The page you are looking for could not be found. Return to StockValuation.io for professional DCF analysis and stock valuation tools.'
    });

    this.meta.updateTag({
      name: 'robots',
      content: 'noindex, nofollow'
    });
  }

  private setHttpStatus(): void {
    // Set 404 status code for SSR responses
    this.httpStatusService.setNotFound();
  }

  async onHomeClick(): Promise<void> {
    if (this.isNavigatingHome) return;
    this.isNavigatingHome = true;
    try {
      await this.router.navigate(['/automated-dcf-analysis']);
    } finally {
      this.isNavigatingHome = false;
    }
  }

  async onValuationClick(): Promise<void> {
    if (this.isNavigatingValuation) return;
    this.isNavigatingValuation = true;
    try {
      await this.router.navigate(['/automated-dcf-analysis']);
    } finally {
      this.isNavigatingValuation = false;
    }
  }

  onBackClick(): void {
    if (this.isBrowser) {
      window.history.back();
    }
  }
}

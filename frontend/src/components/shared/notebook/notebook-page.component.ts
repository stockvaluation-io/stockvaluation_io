import { Component, OnInit, inject, PLATFORM_ID, signal } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { NotebookContainerComponent } from './notebook-container.component';
import { NotebookService } from './notebook.service';

/**
 * Standalone Notebook Page
 * Full-screen notebook accessible via /notebook/:sessionId or /notebook?ticker=ABC route
 */
@Component({
  selector: 'app-notebook-page',
  standalone: true,
  imports: [CommonModule, NotebookContainerComponent],
  template: `
    <div class="notebook-page">
      @if (!isBrowser) {
        <div class="loading-state">
          <h2>Loading Notebook...</h2>
        </div>
      } @else if (isCreatingSession()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <h2>Creating Session...</h2>
          <p>Setting up analysis for {{ ticker }}</p>
        </div>
      } @else if (sessionId) {
        <app-notebook-container
          [sessionId]="sessionId"
          [ticker]="ticker"
          (closed)="onClose()">
        </app-notebook-container>
      } @else if (ticker) {
        <div class="loading-state">
          <div class="spinner"></div>
          <h2>Initializing...</h2>
        </div>
      } @else {
        <div class="error-state">
          <h2>No Session</h2>
          <p>Please provide a ticker or session ID.</p>
          <button (click)="goHome()">Return to Home</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .notebook-page {
      position: fixed;
      inset: 0;
      background: #0d1117;
      z-index: 1000;
    }

    .loading-state,
    .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: #e5e7eb;
      text-align: center;
      padding: 2rem;
    }

    .loading-state h2,
    .error-state h2 {
      margin: 0 0 1rem;
      font-size: 1.5rem;
    }

    .loading-state p {
      color: #10b981;
      font-size: 1rem;
    }

    .spinner {
      width: 3rem;
      height: 3rem;
      border: 3px solid #30363d;
      border-top-color: #10b981;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin-bottom: 1.5rem;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .error-state p {
      margin: 0 0 2rem;
      color: #9ca3af;
    }

    .error-state button {
      padding: 0.75rem 1.5rem;
      background: #10b981;
      color: white;
      border: none;
      border-radius: 0.5rem;
      font-size: 1rem;
      cursor: pointer;
      transition: background 0.2s;
    }

    .error-state button:hover {
      background: #059669;
    }
  `]
})
export class NotebookPageComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private platformId = inject(PLATFORM_ID);
  private notebookService = inject(NotebookService);

  sessionId: string | null = null;
  ticker: string = '';
  isBrowser = false;
  isCreatingSession = signal(false);

  ngOnInit(): void {
    // Check if running in browser
    this.isBrowser = isPlatformBrowser(this.platformId);

    // Get sessionId from route params
    this.route.paramMap.subscribe(params => {
      this.sessionId = params.get('sessionId');
    });

    // Get ticker from query params
    this.route.queryParamMap.subscribe(params => {
      this.ticker = params.get('ticker') || '';

      // Auto-create session if ticker provided but no sessionId
      if (this.isBrowser && this.ticker && !this.sessionId) {
        this.createSession();
      }
    });
  }

  private createSession(): void {
    this.isCreatingSession.set(true);

    this.notebookService.createSession(this.ticker).subscribe({
      next: (session) => {
        this.sessionId = session.id;
        this.isCreatingSession.set(false);
        // Update URL to include sessionId without reloading
        this.router.navigate(['/notebook', session.id], {
          queryParams: { ticker: this.ticker },
          replaceUrl: true
        });
      },
      error: (err) => {
        console.error('Failed to create session:', err);
        this.isCreatingSession.set(false);
      }
    });
  }

  onClose(): void {
    // Close the window/tab
    if (this.isBrowser) {
      window.close();
    }

    // Fallback: navigate home if window.close() doesn't work (not opened by script)
    setTimeout(() => {
      this.router.navigate(['/']);
    }, 100);
  }

  goHome(): void {
    this.router.navigate(['/']);
  }
}

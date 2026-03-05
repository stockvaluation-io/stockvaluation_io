import { Component, Input, OnInit, OnDestroy, OnChanges, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, interval } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LoadingStage, LoadingProgress } from '../../../models';

/**
 * Enhanced loading component that reflects all 8 steps of the valuation-agent pipeline.
 *
 * Time budget totals 240 s (4 min) which is the worst-case observed for the agent.
 * Stages auto-advance on a 1-second tick so the UI never appears stuck, even though
 * the backend gives no streaming progress events.
 *
 * Pipeline (mirrors api/app.py valuate_endpoint):
 *  1. Loading Company Data          ~15 s  — Java baseline DCF call (no segments)
 *  2. Mapping Business Segments     ~25 s  — LLM segment-classification agent
 *  3. Gathering News & Evidence     ~35 s  — Tavily search + news-judge LLM
 *  4. Building Baseline Valuation   ~20 s  — Java DCF with segments attached
 *  5. Analysing DCF Assumptions     ~40 s  — LLM analyzer agent (main bottleneck)
 *  6. Recalculating Valuation       ~20 s  — Java DCF with LLM overrides applied
 *  7. Generating Investment Thesis  ~45 s  — LLM analyst narrative agent
 *  8. Finalising Results            ~40 s  — Persist & build response payload
 */
@Component({
  selector: 'app-enhanced-loading',
  imports: [CommonModule],
  template: `
    <div class="enhanced-loading-container">
      <div class="loading-content">
        <!-- Header -->
        <div class="loading-header">
          <div class="main-spinner">
            <div class="spinner-ring"></div>
            <div class="spinner-inner">
              <i [class]="getActiveStage()?.icon || 'pi pi-spin pi-spinner'" class="stage-icon"></i>
            </div>
          </div>
          <h2 class="loading-title">{{ title }}</h2>
          <p class="loading-subtitle">{{ subtitle }}</p>
        </div>

        <!-- Progress Bar -->
        <div class="progress-container">
          <div class="progress-bar">
            <div class="progress-fill" [style.width.%]="getProgressPercentage()"></div>
          </div>
          <div class="progress-text">
            {{ getProgressPercentage() }}% Complete
          </div>
        </div>

        <!-- Current Stage Card -->
        <div class="current-stage" *ngIf="getActiveStage()">
          <div class="stage-info">
            <h3 class="stage-title">{{ getActiveStage()?.title }}</h3>
            <p class="stage-description">{{ getActiveStage()?.description }}</p>
          </div>
        </div>

        <!-- Stages List -->
        <div class="stages-list">
          <div
            *ngFor="let stage of loadingProgress.stages; let i = index"
            class="stage-item"
            [class.completed]="isStageComplete(i)"
            [class.active]="isStageActive(i)"
            [class.pending]="isStagePending(i)">

            <div class="stage-indicator">
              <i *ngIf="isStageComplete(i)" class="pi pi-check"></i>
              <i *ngIf="isStageActive(i)" [class]="stage.icon"></i>
              <span *ngIf="isStagePending(i)" class="stage-number">{{ i + 1 }}</span>
            </div>

            <div class="stage-details">
              <div class="stage-name">{{ stage.title }}</div>
            </div>
          </div>
        </div>

        <!-- Estimated Time -->
        <div class="time-estimate" *ngIf="getRemainingTime() > 0">
          <i class="pi pi-clock"></i>
          Estimated time remaining: ~{{ getRemainingTime() }}s
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./enhanced-loading.component.scss']
})
export class EnhancedLoadingComponent implements OnInit, OnDestroy, OnChanges {
  @Input() title = 'Processing Request';
  @Input() subtitle = 'Please wait while we process your analysis...';
  @Input() companyName = '';
  /**
   * External stage index from the parent (currently unused for auto-tick logic;
   * kept for API compatibility with the parent container).
   */
  @Input() currentStage = 0;
  /** Stage IDs that the parent has already confirmed as done (e.g. after API returns). */
  @Input() completedStages: string[] = [];

  private destroy$ = new Subject<void>();

  /** Internal time-driven stage index (0-based). */
  timedStageIndex = 0;
  /** Seconds elapsed since loading started. */
  private elapsedSeconds = 0;

  loadingProgress: LoadingProgress = {
    currentStage: 0,
    stages: [],
    isComplete: false,
    hasError: false
  };

  constructor(private ngZone: NgZone) { }

  ngOnInit(): void {
    this.initializeStages();
    this.startTimedProgression();
  }

  ngOnChanges(): void {
    // If the parent company name changes, re-init descriptions.
    this.initializeStages();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ---------------------------------------------------------------------------
  // Stage definitions
  // ---------------------------------------------------------------------------

  private initializeStages(): void {
    const company = this.companyName || 'company';
    this.loadingProgress.stages = [
      {
        id: 'company-data',
        title: 'Loading Company Data',
        description: `Fetching ${company} financial statements and market data from our valuation service...`,
        duration: 5_000,
        icon: 'pi pi-download'
      },
      {
        id: 'segment-mapping',
        title: 'Mapping Business Segments',
        description: `Classifying ${company}'s business lines into valuation sectors using AI...`,
        duration: 25_000,
        icon: 'pi pi-sitemap'
      },
      {
        id: 'news-gathering',
        title: 'Gathering News & Evidence',
        description: `Searching recent news and analyst reports for ${company} to inform assumptions...`,
        duration: 35_000,
        icon: 'pi pi-search'
      },
      {
        id: 'baseline-valuation',
        title: 'Building Baseline Valuation',
        description: 'Running 10-year discounted cash-flow model with segment data attached...',
        duration: 30_000,
        icon: 'pi pi-calculator'
      },
      {
        id: 'dcf-analysis',
        title: 'Analysing DCF Assumptions',
        description: 'AI analyzer reviewing revenue growth, margins, and reinvestment efficiency...',
        duration: 40_000,
        icon: 'pi pi-chart-bar'
      },
      {
        id: 'recalculation',
        title: 'Recalculating Valuation',
        description: 'Applying AI-driven assumption adjustments to the DCF model...',
        duration: 20_000,
        icon: 'pi pi-refresh'
      },
      {
        id: 'narrative-insights',
        title: 'Generating Investment Thesis',
        description: `Crafting growth, margin, and risk narrative for ${company}...`,
        duration: 45_000,
        icon: 'pi pi-file-edit'
      },
      {
        id: 'finalizing',
        title: 'Finalising Results',
        description: 'Persisting valuation, assembling the full report — almost there...',
        duration: 40_000,
        icon: 'pi pi-check-circle'
      }
    ];
  }

  // ---------------------------------------------------------------------------
  // Time-driven progression
  // ---------------------------------------------------------------------------

  private startTimedProgression(): void {
    // Run the ticker outside Angular so change-detection is not hammered every second.
    // We manually call markForCheck only when the visible stage index changes.
    this.ngZone.runOutsideAngular(() => {
      interval(1000)
        .pipe(takeUntil(this.destroy$))
        .subscribe(() => {
          this.elapsedSeconds++;
          const newIndex = this.resolveTimedStageIndex();
          if (newIndex !== this.timedStageIndex) {
            this.ngZone.run(() => {
              this.timedStageIndex = newIndex;
            });
          }
        });
    });
  }

  /**
   * Determines which stage should currently be shown based on elapsed time.
   * Walks through cumulative stage durations to find the active bucket.
   */
  private resolveTimedStageIndex(): number {
    const stages = this.loadingProgress.stages;
    if (!stages.length) return 0;
    let cumulativeMs = 0;
    const elapsedMs = this.elapsedSeconds * 1000;
    for (let i = 0; i < stages.length; i++) {
      cumulativeMs += stages[i].duration;
      if (elapsedMs < cumulativeMs) return i;
    }
    // Past all budgeted time — stay on last stage until the API responds.
    return stages.length - 1;
  }

  // ---------------------------------------------------------------------------
  // Template helpers
  // ---------------------------------------------------------------------------

  /** The stage currently being worked on (union of timed index & parent signal). */
  getActiveStage(): LoadingStage | undefined {
    return this.loadingProgress.stages[this.timedStageIndex];
  }

  /** @deprecated kept only for legacy template bindings — use getActiveStage() instead. */
  getCurrentStage(): LoadingStage | undefined {
    return this.getActiveStage();
  }

  isStageComplete(i: number): boolean {
    const stage = this.loadingProgress.stages[i];
    if (!stage) return false;
    // Parent can explicitly mark stages complete, or time has already moved past this stage.
    return this.completedStages.includes(stage.id) || i < this.timedStageIndex;
  }

  isStageActive(i: number): boolean {
    return i === this.timedStageIndex && !this.isStageComplete(i);
  }

  isStagePending(i: number): boolean {
    return i > this.timedStageIndex && !this.isStageComplete(i);
  }

  getProgressPercentage(): number {
    const stages = this.loadingProgress.stages;
    if (!stages.length) return 0;

    // Count how many stages are fully done.
    let completedCount = 0;
    for (let i = 0; i < stages.length; i++) {
      if (this.isStageComplete(i)) completedCount++;
    }

    if (completedCount >= stages.length) return 100;

    // Base percentage from completed stages.
    const baseProgress = (completedCount / stages.length) * 100;

    // Add intra-stage progress based on time spent within the active stage.
    const activeStage = stages[this.timedStageIndex];
    let intraProgress = 0;
    if (activeStage) {
      let cumulativeBefore = 0;
      for (let i = 0; i < this.timedStageIndex; i++) {
        cumulativeBefore += stages[i].duration;
      }
      const elapsedMs = this.elapsedSeconds * 1000;
      const timeIntoStage = Math.max(0, elapsedMs - cumulativeBefore);
      const fraction = Math.min(timeIntoStage / activeStage.duration, 0.9); // cap at 90% within stage
      intraProgress = (fraction / stages.length) * 100;
    }

    // Always show at least 3% so bar is never fully empty.
    const totalProgress = Math.max(3, baseProgress + intraProgress);
    return Math.min(Math.round(totalProgress), 95); // cap at 95% until truly done
  }

  getRemainingTime(): number {
    const stages = this.loadingProgress.stages;
    const totalBudgetMs = stages.reduce((s, st) => s + st.duration, 0);
    const elapsedMs = this.elapsedSeconds * 1000;
    const remaining = Math.max(0, totalBudgetMs - elapsedMs);
    return Math.ceil(remaining / 1000);
  }
}
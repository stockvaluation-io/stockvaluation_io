import { Component, Input, OnInit, OnDestroy, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { LoadingStage, LoadingProgress } from '../../../models';

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
              <i [class]="getCurrentStage()?.icon || 'pi pi-spin pi-spinner'" class="stage-icon"></i>
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

        <!-- Current Stage -->
        <div class="current-stage" *ngIf="getCurrentStage()">
          <div class="stage-info">
            <h3 class="stage-title">{{ getCurrentStage()?.title }}</h3>
            <p class="stage-description">{{ getCurrentStage()?.description }}</p>
          </div>
        </div>

        <!-- Stages List -->
        <div class="stages-list">
          <div 
            *ngFor="let stage of loadingProgress.stages; let i = index"
            class="stage-item"
            [class.completed]="completedStages.includes(stage.id)"
            [class.active]="i === currentStage && !completedStages.includes(stage.id)"
            [class.pending]="i > currentStage && !completedStages.includes(stage.id)">
            
            <div class="stage-indicator">
              <i *ngIf="completedStages.includes(stage.id)" class="pi pi-check"></i>
              <i *ngIf="i === currentStage && !completedStages.includes(stage.id)" [class]="stage.icon"></i>
              <span *ngIf="i > currentStage && !completedStages.includes(stage.id)" class="stage-number">{{ i + 1 }}</span>
            </div>
            
            <div class="stage-details">
              <div class="stage-name">{{ stage.title }}</div>
            </div>
          </div>
        </div>

        <!-- Estimated Time -->
        <div class="time-estimate" *ngIf="getRemainingTime() > 0">
          <i class="pi pi-clock"></i>
          Estimated time remaining: {{ getRemainingTime() }}s
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
  @Input() currentStage = 0; // Accept current stage from parent
  @Input() completedStages: string[] = []; // Accept completed stages from parent

  private destroy$ = new Subject<void>();
  
  loadingProgress: LoadingProgress = {
    currentStage: 0,
    stages: [],
    isComplete: false,
    hasError: false
  };

  ngOnInit(): void {
    this.initializeStages();
    this.updateProgress();
  }

  ngOnChanges(): void {
    this.initializeStages();
    this.updateProgress();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeStages(): void {
      this.loadingProgress.stages = [
        {
          id: 'company-data',
          title: 'Loading Company Data',
          description: `Fetching ${this.companyName || 'company'} financial statements and market data...`,
          duration: 1000, // Real API estimate: 1-3 seconds
          icon: 'pi pi-download'
        },
        {
          id: 'dcf-analysis',
          title: 'Running DCF Analysis',
          description: 'Processing 10-year projections and terminal value calculation...',
          duration: 1000, // Real API estimate: 5-8 seconds (main bottleneck)
          icon: 'pi pi-calculator'
        },
        {
          id: 'narrative-insights',
          title: 'Generating Insights',
          description: 'Creating investment narrative and analysis...',
          duration: 6000, // Part of main API response
          icon: 'pi pi-file-edit'
        }
      ];
    }

  private updateProgress(): void {
    // Update progress based on real input from parent container
    this.loadingProgress.currentStage = this.currentStage;
    
    // The template will use completedStages array directly for completion logic
    // No need to modify the stage objects themselves
  }

  getCurrentStage(): LoadingStage | undefined {
    return this.loadingProgress.stages[this.loadingProgress.currentStage];
  }

  getProgressPercentage(): number {
    if (this.loadingProgress.stages.length === 0) return 0;
    
    const completedCount = this.completedStages.length;
    const totalStages = this.loadingProgress.stages.length;
    
    // If all stages completed, show 100%
    if (completedCount >= totalStages) {
      return 100;
    }
    
    // Calculate base progress from completed stages
    const baseProgress = (completedCount / totalStages) * 100;
    
    // Add partial progress for current stage to avoid "stuck at 0%" feeling
    // Show at least 10% when loading starts, then add partial progress per stage
    const minProgress = 10; // Always show at least 10% when loading
    const partialStageProgress = (1 / totalStages) * 30; // Add up to 30% for current stage
    
    const currentProgress = Math.max(minProgress, baseProgress + partialStageProgress);
    
    return Math.min(Math.round(currentProgress), 95); // Cap at 95% until complete
  }

  getRemainingTime(): number {
    // Calculate remaining time based on stages not yet completed
    const remainingStages = this.loadingProgress.stages.filter(stage => 
      !this.completedStages.includes(stage.id)
    );
    const totalRemainingTime = remainingStages.reduce((sum, stage) => sum + stage.duration, 0);
    return Math.ceil(totalRemainingTime / 1000);
  }
}
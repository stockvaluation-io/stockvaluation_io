import { Component, Input, ChangeDetectionStrategy, OnInit, OnChanges, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

import { CompanyData, ValuationResults } from '../../../../models';

@Component({
    selector: 'app-valuation-animation-section',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="valuation-animation-section" *ngIf="results.valuation_animation_base64">
      <div class="section-header">
        <div class="header-left">
          <i class="pi pi-video section-icon"></i>
          <div>
            <h3 class="section-title">DCF Value Breakdown</h3>
            <span class="section-subtitle">Visual explanation of where {{ company.name || 'company' }} value comes from</span>
          </div>
        </div>
        <div class="header-actions">
          <button class="action-btn" (click)="togglePlayback()" [title]="isPlaying ? 'Pause' : 'Play'">
            <i class="pi" [class.pi-pause]="isPlaying" [class.pi-play]="!isPlaying"></i>
          </button>
          <button class="action-btn" (click)="restartVideo()" title="Restart">
            <i class="pi pi-refresh"></i>
          </button>
        </div>
      </div>

      <div class="video-container">
        <video 
          #videoPlayer
          [src]="videoSrc"
          (play)="onPlay()"
          (pause)="onPause()"
          (ended)="onEnded()"
          controls
          autoplay
          muted
          playsinline
          class="animation-video">
          Your browser does not support the video tag.
        </video>
        
        <div class="video-overlay" *ngIf="!hasStarted" (click)="startPlayback()">
          <div class="play-button">
            <i class="pi pi-play"></i>
          </div>
          <span class="overlay-text">Click to watch valuation breakdown</span>
        </div>
      </div>

      <div class="animation-description">
        <div class="description-item">
          <i class="pi pi-chart-bar"></i>
          <span>Shows how free cash flows evolve over the projection period</span>
        </div>
        <div class="description-item">
          <i class="pi pi-percentage"></i>
          <span>Visualizes the discounting effect reducing future value to present</span>
        </div>
        <div class="description-item">
          <i class="pi pi-star"></i>
          <span>Highlights terminal value dominance in total valuation</span>
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./valuation-animation-section.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ValuationAnimationSection implements OnInit, OnChanges {
    @Input() company!: CompanyData;
    @Input() results!: ValuationResults;

    @ViewChild('videoPlayer') videoPlayer!: ElementRef<HTMLVideoElement>;

    videoSrc: SafeUrl | null = null;
    isPlaying = false;
    hasStarted = false;

    constructor(private sanitizer: DomSanitizer) { }

    ngOnInit(): void {
        this.buildVideoSource();
    }

    ngOnChanges(): void {
        this.buildVideoSource();
    }

    private buildVideoSource(): void {
        if (this.results?.valuation_animation_base64) {
            const dataUrl = `data:video/mp4;base64,${this.results.valuation_animation_base64}`;
            this.videoSrc = this.sanitizer.bypassSecurityTrustUrl(dataUrl);
        }
    }

    togglePlayback(): void {
        const video = this.videoPlayer?.nativeElement;
        if (!video) return;

        if (video.paused) {
            video.play();
        } else {
            video.pause();
        }
    }

    startPlayback(): void {
        const video = this.videoPlayer?.nativeElement;
        if (!video) return;

        this.hasStarted = true;
        video.play();
    }

    restartVideo(): void {
        const video = this.videoPlayer?.nativeElement;
        if (!video) return;

        video.currentTime = 0;
        video.play();
    }

    onPlay(): void {
        this.isPlaying = true;
        this.hasStarted = true;
    }

    onPause(): void {
        this.isPlaying = false;
    }

    onEnded(): void {
        this.isPlaying = false;
    }
}

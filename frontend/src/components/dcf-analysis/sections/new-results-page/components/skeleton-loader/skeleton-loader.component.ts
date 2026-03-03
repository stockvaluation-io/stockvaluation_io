import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-skeleton-loader',
    imports: [CommonModule],
    template: `
    <div class="skeleton-container" [class]="containerClass">
      <div 
        *ngFor="let line of lineArray; let i = index"
        class="skeleton-line"
        [class]="getLineClass(i)"
        [style.width]="getLineWidth(i)">
      </div>
    </div>
  `,
    styleUrls: ['./skeleton-loader.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class SkeletonLoaderComponent {
  @Input() type: 'text' | 'card' | 'table' | 'chart' = 'text';
  @Input() lines = 3;
  @Input() animated = true;

  get containerClass(): string {
    return `skeleton-${this.type} ${this.animated ? 'animated' : ''}`;
  }

  get lineArray(): number[] {
    return Array(this.lines).fill(0).map((_, i) => i);
  }

  getLineClass(index: number): string {
    const classes = ['skeleton-line'];
    
    if (this.type === 'card') {
      if (index === 0) classes.push('skeleton-title');
      else if (index === 1) classes.push('skeleton-subtitle');
      else classes.push('skeleton-content');
    } else if (this.type === 'table') {
      if (index === 0) classes.push('skeleton-header');
      else classes.push('skeleton-row');
    } else if (this.type === 'chart') {
      classes.push('skeleton-bar');
    }
    
    return classes.join(' ');
  }

  getLineWidth(index: number): string {
    if (this.type === 'text') {
      const widths = ['100%', '85%', '75%', '90%', '80%'];
      return widths[index % widths.length];
    } else if (this.type === 'card') {
      if (index === 0) return '60%'; // Title
      if (index === 1) return '40%'; // Subtitle
      return '85%'; // Content
    } else if (this.type === 'table') {
      return '100%';
    } else if (this.type === 'chart') {
      const heights = ['60%', '80%', '45%', '75%', '55%'];
      return heights[index % heights.length];
    }
    
    return '100%';
  }
}
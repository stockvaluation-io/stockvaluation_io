import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { IconService } from '../../../core/services';

@Component({
    selector: 'app-icon',
    imports: [CommonModule, NgIcon],
    providers: [provideIcons(IconService.allIcons)],
    template: `
    <ng-icon 
      [name]="resolvedIcon"
      [size]="resolvedSize + 'px'"
      [class]="additionalClasses">
    </ng-icon>
  `,
    host: {
        'class': 'app-icon'
    },
    styleUrls: ['./app-icon.component.scss']
})
export class AppIconComponent implements OnInit, OnChanges {
  
  @Input() icon!: keyof typeof this.iconService.icons | string;
  @Input() size: keyof typeof this.iconService.sizes | string | number = 'lg';
  @Input() class: string = '';
  @Input() style: string = '';
  
  resolvedIcon: string = '';
  resolvedSize: number = 20;
  
  constructor(public iconService: IconService) {}
  
  ngOnInit(): void {
    this.resolveIcon();
    this.resolveSize();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['icon']) {
      this.resolveIcon();
    }
    if (changes['size']) {
      this.resolveSize();
    }
  }

  private resolveIcon(): void {
    // Resolve icon name - if it's a key in our service, use that, otherwise use as-is
    if (this.icon && this.icon in this.iconService.icons) {
      this.resolvedIcon = this.iconService.getIcon(this.icon as keyof typeof this.iconService.icons);
    } else {
      this.resolvedIcon = this.icon as string;
    }
  }

  private resolveSize(): void {
    // Resolve size - if it's a key in our service, use that, otherwise use as-is
    if (typeof this.size === 'string' && this.size in this.iconService.sizes) {
      this.resolvedSize = parseInt(this.iconService.getSize(this.size as keyof typeof this.iconService.sizes));
    } else {
      this.resolvedSize = typeof this.size === 'number' ? this.size : parseInt(this.size as string) || 20;
    }
  }
  
  get additionalClasses(): string {
    return this.class;
  }
}
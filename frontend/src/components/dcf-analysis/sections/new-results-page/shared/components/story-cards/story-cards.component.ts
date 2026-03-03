import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface StoryCard {
  name: string;
  content: string;
  icon?: string;
}

@Component({
    selector: 'app-story-cards',
    imports: [CommonModule],
    template: `
    <div class="story-cards-container">
      <!-- Story Cards Grid -->
      <div class="story-cards-grid">
        <div class="story-card" *ngFor="let card of storyCards">
          <div class="card-header">
            <div class="card-icon">
              <i class="pi pi-{{ getCardIcon(card.name) }}" aria-hidden="true"></i>
            </div>
            <h3 class="card-title">{{ card.name }}</h3>
          </div>
          <div class="card-content">
            <p class="story-text">{{ card.content }}</p>
          </div>
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./story-cards.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class StoryCardsComponent {
  @Input() storyCards: StoryCard[] = [];

  getCardIcon(cardName: string): string {
    const iconMap: { [key: string]: string } = {
      'Growth': 'chart-line',
      'Profitability': 'percentage',
      'Risk': 'shield',
      'Market Expectation': 'eye',
      'Efficiency': 'cog'
    };
    return iconMap[cardName] || 'info-circle';
  }
}
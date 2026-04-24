import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Cell } from '../cell.models';
import { ReasoningCellComponent } from './reasoning-cell.component';

describe('ReasoningCellComponent', () => {
  let fixture: ComponentFixture<ReasoningCellComponent>;
  let component: ReasoningCellComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReasoningCellComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ReasoningCellComponent);
    component = fixture.componentInstance;
  });

  it('renders system opening messages as markdown AI content', () => {
    component.cell = buildCell({
      cell_type: 'system',
      author_type: 'system',
      content: {
        message: 'I just completed a DCF valuation for **GOOG**. **Fair Value:** 288.85 **Current Price:** 337.75'
      }
    });

    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('I just completed a DCF valuation for GOOG.');
    expect(element.textContent).not.toContain('**Fair Value:**');
    expect(Array.from(element.querySelectorAll('strong')).map(node => node.textContent)).toContain('Fair Value:');
  });

  it('keeps user content in the user bubble', () => {
    component.cell = buildCell({
      author_type: 'user',
      user_input: 'What growth is priced in?'
    });

    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('.user-message')?.textContent).toContain('What growth is priced in?');
    expect(element.querySelector('.ai-message')).toBeNull();
  });
});

function buildCell(overrides: Partial<Cell>): Cell {
  return {
    id: 'cell-1',
    session_id: 'session-1',
    sequence_number: 1,
    cell_type: 'reasoning',
    author_type: 'ai',
    created_at: new Date().toISOString(),
    ...overrides
  };
}

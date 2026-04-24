import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PricedInExpectationsComponent } from './priced-in-expectations.component';
import { ValuationResults } from '../../../../models';

describe('PricedInExpectationsComponent', () => {
  let fixture: ComponentFixture<PricedInExpectationsComponent>;
  let component: PricedInExpectationsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PricedInExpectationsComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(PricedInExpectationsComponent);
    component = fixture.componentInstance;
  });

  it('renders market expectations when data is available', () => {
    component.results = buildResults();

    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Market Expectations');
    expect(text).toContain('At 20.00% margin');
    expect(text).toContain('DCF Value Matrix');
    expect(text).toContain("Combinations That Justify Today's Price");
    expect(text).toContain('Closest tested case');
    expect(text).toContain('Market Price');
    expect(text).toContain('USD 90.00');
    expect(text).toContain('-10.00%');
    expect(text).not.toContain('Grid Result');
    expect(text).not.toContain('Solved');
    expect(text).not.toContain('Bounded');
  });

  it('hides the section when priced-in data is missing', () => {
    component.results = { assumptionTransparency: {} } as ValuationResults;

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.priced-in-section')).toBeNull();
  });

  it('switches displayed scenario with risk and capital efficiency toggles', () => {
    component.results = buildResults();
    fixture.detectChanges();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    buttons.find(button => button.textContent?.includes('High Risk'))?.click();
    buttons.find(button => button.textContent?.includes('Inefficient Capital'))?.click();
    fixture.detectChanges();

    expect(component.selectedScenario?.key).toBe('high_risk__inefficient');
    expect(fixture.nativeElement.textContent).toContain('high risk and inefficient capital');
  });

  it('renders percentage-point values without re-scaling small percentages', () => {
    expect(component.formatPercent(0.5)).toBe('0.50%');
    expect(component.formatSignedPercent(-0.47)).toBe('-0.47%');
    expect(component.formatSignedPercent(1.13)).toBe('+1.13%');
  });

  it('renders compact DCF values for matrix cells', () => {
    component.results = buildResults();

    expect(component.formatCompactMoney(336.16)).toBe('USD 336');
    expect(component.formatCompactMoney(13.18)).toBe('USD 13.18');
  });

  it('shows a closest tested case when no combination reaches market price', () => {
    component.results = buildResults(false);

    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Market Price Outside Tested Range');
    expect(text).toContain('Closest tested case');
    expect(text).toContain("does not clear today's price");
    expect(text).not.toContain('Nearest Frontier Bounds');
    expect(text).not.toContain('Bounded');
    expect(text).not.toContain("No Tested Combination Reaches Today's Price");
  });

  it('places the closest tested case below the DCF value matrix', () => {
    component.results = buildResults();

    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text.indexOf('DCF Value Matrix')).toBeLessThan(text.indexOf('Closest tested case'));
  });
});

function buildResults(solved = true): ValuationResults {
  return {
    currency: 'USD',
    stockCurrency: 'USD',
    assumptionTransparency: {
      pricedInExpectations: {
        marketPrice: 100,
        modelIntrinsicValue: 95,
        method: 'Deterministic reverse DCF grid.',
        baseCase: {
          gapToMarketPct: -5
        },
        grid: buildGrid(8, solved),
        frontier: buildFrontier(8, solved),
        scenarios: [
          {
            key: 'base_risk__base_efficiency',
            riskKey: 'base_risk',
            riskLabel: 'Base Risk',
            capitalEfficiencyKey: 'base_efficiency',
            capitalEfficiencyLabel: 'Base Efficiency',
            headline: "At 20.00% margin, today's market price needs about 8.00% revenue growth under base risk and base efficiency.",
            grid: buildGrid(8, solved),
            frontier: buildFrontier(8, solved)
          },
          {
            key: 'high_risk__inefficient',
            riskKey: 'high_risk',
            riskLabel: 'High Risk',
            capitalEfficiencyKey: 'inefficient',
            capitalEfficiencyLabel: 'Inefficient Capital',
            headline: "At 20.00% margin, today's market price needs about 12.00% revenue growth under high risk and inefficient capital.",
            grid: buildGrid(12),
            frontier: buildFrontier(12)
          }
        ]
      }
    }
  } as ValuationResults;
}

function buildGrid(requiredGrowth: number, solved = true) {
  return [15, 20].flatMap(operatingMargin =>
    [6, requiredGrowth].map(revenueGrowth => ({
      revenueGrowth,
      operatingMargin,
      initialCostOfCapital: 8,
      salesToCapital: 2,
      intrinsicValue: solved && revenueGrowth >= requiredGrowth ? 100 : 90,
      gapToMarketPct: solved && revenueGrowth >= requiredGrowth ? 0 : -10,
      supportsMarketPrice: solved && revenueGrowth >= requiredGrowth
    }))
  );
}

function buildFrontier(requiredGrowth: number, solved = true) {
  return [15, 20].map(operatingMargin => ({
    operatingMargin,
    impliedRevenueGrowth: requiredGrowth,
    intrinsicValue: solved ? 100 : 90,
    gapToMarketPct: solved ? 0 : -10,
    solved
  }));
}

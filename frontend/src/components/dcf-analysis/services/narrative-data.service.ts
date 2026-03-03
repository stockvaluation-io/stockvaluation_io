import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class NarrativeDataService {
  private staticNarrativeData = {
    key_assumptions: {
      narrative: "The DCF valuation for Tesla is built on several core assumptions. First, the revenue growth rate is expected to start high, reflecting Tesla's ongoing expansion and leadership in electric vehicles, and then gradually taper as the company matures. The initial growth rate is set at approximately 18% per year, declining to a terminal growth rate of 4.6% by the end of the projection period. The cost of capital (WACC) begins at 10.1%, reflecting Tesla's risk profile as an innovative but volatile company, and decreases to 8.9% as the business stabilizes. Operating margins are projected to remain robust at around 8.8%, higher than the industry average, due to Tesla's scale, brand, and technology advantages. Reinvestment needs are substantial in the early years, supporting capacity expansion and R&D, but moderate over time as growth slows. The terminal value is calculated using a perpetual growth model, with a conservative long-term growth rate to avoid overestimating future cash flows.",
      growth_rate: {
        initial: 18.1,
        terminal: 4.6
      },
      cost_of_capital: {
        initial: 10.1,
        terminal: 8.9
      },
      operating_margin: {
        average: 8.8
      },
      terminal_growth_rate: 4.6
    },
    value_drivers: {
      narrative: "Tesla's intrinsic value is primarily driven by its ability to sustain high revenue growth, maintain superior operating margins, and efficiently reinvest in growth opportunities. The model assumes Tesla will continue to outpace the automotive industry in both top-line growth and profitability, leveraging its brand, technology, and vertically integrated operations. The terminal value, which represents the value of all future cash flows beyond the explicit forecast period, is a major component of the total valuation, accounting for over 90% of the total enterprise value. This underscores the importance of long-term growth and margin assumptions in the overall valuation.",
      terminal_value_contribution: {
        percentage_of_total: 91.5
      },
      explicit_period_pv: {
        usd_billions: 15.88
      },
      terminal_value_pv: {
        usd_billions: 171.12
      }
    },
    valuation_summary: {
      narrative: "Based on the DCF analysis, Tesla's estimated intrinsic value per share is $65.23. This is significantly lower than the current market price of $341.04, indicating that Tesla is trading at a 423% premium to its intrinsic value. The market appears to be pricing in much higher growth and profitability than the model assumes, or is assigning significant value to potential future opportunities not captured in the base case (such as autonomous driving, energy, or robotics businesses).",
      intrinsic_value_per_share: {
        usd: 65.23
      },
      current_market_price: {
        usd: 341.04
      },
      premium_to_intrinsic: {
        percentage: 423
      }
    },
    sensitivity_and_uncertainties: {
      narrative: "The DCF valuation is highly sensitive to key assumptions, especially the terminal growth rate, cost of capital, and operating margin. Small changes in these inputs can result in large swings in the estimated intrinsic value. For example, increasing the terminal growth rate to 5.5% or lowering the WACC to 8% would materially increase the valuation, while more conservative assumptions would decrease it. Additionally, the heavy reliance on terminal value means that any long-term disruption to Tesla's business model or increased competition could significantly reduce value. Investors should be aware that the current market price implies much more optimistic assumptions than those used in this model.",
      sensitivity_examples: {
        terminal_growth_rate: {
          "3.0_percent": 55.45,
          "4.6_percent": 65.23,
          "5.5_percent": 78.28
        },
        wacc: {
          "9.0_percent": 75.01,
          "10.1_percent": 65.23,
          "11.0_percent": 56.75
        }
      }
    },
    key_takeaways: {
      narrative: "Tesla's DCF valuation highlights the company's impressive growth and profitability, but also reveals that its current market price is well above what can be justified by fundamental cash flow projections. The stock appears overvalued unless investors believe in far more aggressive growth, margin, or new business assumptions than those used in this analysis. The valuation is particularly sensitive to long-term assumptions, and the high proportion of value in the terminal period increases the risk of over- or under-estimation."
    }
  };

  getNarrativeData(ticker: string): any {
    // In the future, this would make an API call to get narrative data for the specific ticker
    // For now, return the static data for demonstration
    return this.staticNarrativeData;
  }

  injectNarrativeIntoResults(valuationResults: any, ticker: string): any {
    // Inject narrative data into the results object
    return {
      ...valuationResults,
      narratives: this.getNarrativeData(ticker)
    };
  }
}
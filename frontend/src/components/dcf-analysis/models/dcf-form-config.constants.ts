import { DCFFieldConfig, OverrideTabConfig, CompanyRiskLevel } from './dcf-assumptions-form.interface';

// Core DCF Field Configurations (based on defaultFields from Model.model.ts)
export const DCF_FIELD_CONFIGS: DCFFieldConfig[] = [
  {
    key: 'revenueNextYear',
    name: 'Revenue growth rate for next year',
    value: 0,
    max: 100,
    isVisible: true,
    toolTip: "Default is the analyst projection for the next period.",
    isReadOnly: false
  },
  {
    key: 'operatingMarginNextYear',
    name: 'Operating Margin for next year',
    value: 0,
    max: 99,
    isVisible: true,
    toolTip: "Default value assumes your company maintains the current year's margin.",
    isReadOnly: false
  },
  {
    key: 'compoundAnnualGrowth2_5',
    name: 'Compounded annual revenue growth rate - years 2-5',
    value: 0,
    max: 100,
    isVisible: true,
    toolTip: "Uses next year's revenue projection as the default value.",
    isReadOnly: false
  },
  {
    key: 'targetPreTaxOperatingMargin',
    name: 'Target pre-tax operating margin',
    value: 0,
    max: 99,
    isVisible: true,
    toolTip: "Default is the industry average; adjust if your company operates differently.",
    isReadOnly: false
  },
  {
    key: 'salesToCapitalYears1To5',
    name: 'Sales to capital ratio (for years 1-5)',
    value: 0,
    max: 50,
    isVisible: true,
    toolTip: "For every dollar spent, this value shows revenue earned (e.g., a value of 5 means $5 revenue per $1 spent). It reflects company efficiency. The default is 90th decile value for the industry giving extra kick in the earlier growth phase, but adjust if your company stands out.",
    isReadOnly: false
  },
  {
    key: 'salesToCapitalYears6To10',
    name: 'Sales to capital ratio (for years 6-10)',
    value: 0,
    max: 50,
    isVisible: true,
    toolTip: "Default matches the average value for the industry.",
    isReadOnly: false
  },
  {
    key: 'riskFreeRate',
    name: 'Riskfree rate',
    value: 0,
    max: 100,
    isVisible: true,
    toolTip: "Currency specific 10 year bond rate net of default risk. It's updated once a year.",
    isReadOnly: true
  },
  {
    key: 'initialCostCapital',
    name: 'Initial cost of capital',
    value: 0,
    max: 100,
    isVisible: true,
    toolTip: "Cost of capital is based on the risk-free rate and Aswath Damodaran's dynamic equity risk premium. It's used to discount the future cash flow.",
    isReadOnly: true
  }
];

// Risk Assessment Questions Configuration
export const RISK_QUESTIONS_CONFIG = [
  {
    id: 'isExpensesCapitalize',
    text: 'Do you have R & D expenses to capitalize?',
    type: 'radio' as const,
    options: [true, false],
    defaultAnswer: true,
    isVisible: true
  },
  {
    id: 'hasOperatingLease',
    text: 'Do you have operating lease commitments?',
    type: 'radio' as const,
    options: [true, false],
    defaultAnswer: false,
    isVisible: false
  },
  {
    id: 'companyRiskLevel',
    text: 'How risky is your company?',
    type: 'radio' as const,
    options: ['Very Low', 'Low', 'Medium', 'High', 'Very High'],
    defaultAnswer: 'Medium',
    isVisible: true
  },
  {
    id: 'hasEmployeeOptions',
    text: 'Do you have employee options outstanding?',
    type: 'radio' as const,
    options: [true, false],
    defaultAnswer: false,
    isVisible: false
  }
];

// Employee Options Questions Configuration
export const EMPLOYEE_OPTIONS_CONFIG = [
  {
    id: 'numberOfOptions',
    text: 'No of options outstanding =',
    type: 'slider' as const,
    defaultValue: 0,
    min: 0,
    max: 100,
    isVisible: true
  },
  {
    id: 'averageStrikePrice',
    text: 'Avg strike price =',
    type: 'slider' as const,
    defaultValue: 0,
    min: 0,
    max: 100,
    isVisible: true
  },
  {
    id: 'averageMaturity',
    text: 'Avg maturity =',
    type: 'slider' as const,
    defaultValue: 0,
    min: 0,
    max: 100,
    isVisible: true
  },
  {
    id: 'stockPriceStdDev',
    text: 'Standard deviation on stock price =',
    type: 'slider' as const,
    defaultValue: 0,
    min: 0,
    max: 100,
    isVisible: true
  }
];

// Advanced Override Tabs Configuration
export const OVERRIDE_TABS_CONFIG: OverrideTabConfig[] = [
  {
    key: 'overrideAssumptionCostCapital',
    header: 'In stable growth, I will assume that your firm will have a cost of capital similar to that of typical mature companies (riskfree rate + 4.5%)',
    question: 'Do you want to override this assumption?',
    inputLabel: 'If yes, enter the cost of capital after year 10 = '
  },
  {
    key: 'overrideAssumptionReturnOnCapital',
    header: 'I will assume that your firm will earn a return on capital equal to its cost of capital after year 10.',
    question: 'Do you want to override this assumption?',
    inputLabel: 'If yes, enter the return on capital you expect after year 10 = '
  },
  {
    key: 'overrideAssumptionProbabilityOfFailure',
    header: 'I will assume that your firm has no chance of failure over the foreseeable future.',
    question: 'Do you want to override this assumption?',
    inputLabel: 'If yes, enter the probability of failure = ',
    additionalInputLabel: 'Enter the distress proceeds as percentage of book or fair value',
    additionalRadioLabel: 'What do you want to tie your proceeds in failure to?',
    options: ['V', 'B'],
    hasAdditionalInput: true,
    hasAdditionalRadio: true
  },
  {
    key: 'overrideAssumptionReinvestmentLag',
    header: 'I assume that reinvestment in a year translates into growth in the next year, i.e., there is a one year lag between reinvesting and generating growth from that reinvestment.',
    question: 'Do you want to override this assumption?',
    inputLabel: 'If yes, enter a different lag (0 = no lag to 3 = lag of 3 years)'
  },
  {
    key: 'overrideAssumptionTaxRate',
    header: 'We are setting Effective tax equals to marginal tax rate.',
    question: 'Do you want to override this assumption?',
    inputLabel: ''
  },
  {
    key: 'overrideAssumptionNOL',
    header: 'I will assume that you have no losses carried forward from prior years (NOL) coming into the valuation. If you have a money-losing company, you may want to override.',
    question: 'Do you want to override this assumption?',
    inputLabel: 'If yes, enter the NOL that you are carrying over into year 1'
  },
  {
    key: 'overrideAssumptionRiskFreeRate',
    header: 'I will assume that today\'s risk-free rate will prevail in perpetuity. If you override this assumption, I will change the risk-free rate after year 10.',
    question: 'Do you want to override this assumption?',
    inputLabel: 'If yes, enter the risk-free rate after year 10'
  },
  {
    key: 'overrideAssumptionGrowthRate',
    header: 'I will assume that the growth rate in perpetuity will be equal to the risk-free rate.',
    question: 'Do you want to override this assumption?',
    inputLabel: 'If yes, enter the growth rate in perpetuity = '
  },
  {
    key: 'overrideAssumptionCashPosition',
    header: 'I have assumed that none of the cash is trapped (in foreign countries) and that there is no additional tax liability coming due and that cash is a neutral asset.',
    question: 'Do you want to override this assumption?',
    inputLabel: 'If yes, enter trapped cash (if taxes) or entire balance (if mistrust)',
    additionalInputLabel: '& Average tax rate of the foreign markets where the cash is trapped',
    hasAdditionalInput: true
  }
];

// Company Risk Level Options
export const COMPANY_RISK_LEVELS: CompanyRiskLevel[] = [
  'Very Low',
  'Low', 
  'Medium',
  'High',
  'Very High'
];

// Validation Rules
export const VALIDATION_RULES = {
  coreAssumptions: {
    revenueNextYear: { min: 0, max: 100, required: true },
    operatingMarginNextYear: { min: 0, max: 99, required: true },
    compoundAnnualGrowth2_5: { min: 0, max: 100, required: true },
    targetPreTaxOperatingMargin: { min: 0, max: 99, required: true },
    salesToCapitalYears1To5: { min: 0, max: 10, required: true },
    salesToCapitalYears6To10: { min: 0, max: 10, required: true },
    riskFreeRate: { min: 0, max: 100, required: false }, // Read-only
    initialCostCapital: { min: 0, max: 100, required: false } // Read-only
  },
  employeeOptions: {
    numberOfOptions: { min: 0, max: 1000000, required: false },
    averageStrikePrice: { min: 0, max: 10000, required: false },
    averageMaturity: { min: 0, max: 20, required: false },
    stockPriceStdDev: { min: 0, max: 100, required: false }
  },
  overrides: {
    probabilityOfFailure: { min: 0, max: 100, required: false },
    distressProceeds: { min: 0, max: 100, required: false },
    foreignTaxRate: { min: 0, max: 50, required: false },
    reinvestmentLag: { min: 0, max: 3, required: false },
    costOfCapital: { min: 0, max: 50, required: false },
    returnOnCapital: { min: 0, max: 50, required: false },
    taxRate: { min: 0, max: 50, required: false },
    nol: { min: 0, max: 1000000, required: false },
    riskFreeRate: { min: 0, max: 20, required: false },
    growthRate: { min: 0, max: 10, required: false }
  }
};
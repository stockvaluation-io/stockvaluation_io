export interface TopStock {
    symbol: string;
    name: string;
    price: number;
    sector: string;
    marketCap: number;
    change: number;
    changePercent: number;
    dayLow: number;
    dayHigh: number;
    week52Low: number;
    week52High: number;
    volume: number;
    avgVolume: number;
    peRatio: number | null;
    bid: number;
    ask: number;
    dividend: number;
    dividendYield: number;
}
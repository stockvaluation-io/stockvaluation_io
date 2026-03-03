export interface Quote {
    text: string;
    author: string;
    source?: string;
}

export interface QuotesContent {
    title: string;
    subtitle: string;
    quotes: Quote[];
}
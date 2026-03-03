export interface FAQItem {
    id: string;
    question: string;
    answer: string;
    category: string;
    searchKeywords: string[];
}

export interface FAQContent {
    hero: {
        title: string;
        subtitle: string;
    };
    categories: Array<{
        id: string;
        name: string;
        description: string;
        icon: string;
    }>;
    faqs: FAQItem[];
    contact: {
        title: string;
        description: string;
        email: string;
    };
}
export interface ContentSection {
    id: string;
    title: string;
    icon: string;
    content: string[];
}

export interface PrivacyContent {
    lastUpdated: string;
    hero: {
        title: string;
        subtitle: string;
        summary: {
            title: string;
            items: Array<{
                title: string;
                icon: string;
                description: string;
            }>;
        };
    };
    sections: ContentSection[];
    contact: {
        title: string;
        description: string;
        email: string;
        headerIcon: string;
        buttonIcon: string;
    };
}
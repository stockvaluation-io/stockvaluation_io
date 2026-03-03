import { Routes } from '@angular/router';
import { environment } from '../env/environment';

const legacyBullbeargptEnabled = ((environment as any).features?.legacyBullbeargpt ?? false) === true;

export const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        redirectTo: 'automated-dcf-analysis',
    },
    {
        path: 'automated-dcf-analysis',
        loadComponent: () => import('../components/dcf-analysis').then(m => m.DCFAnalysisPageContainer),
    },

    {
        path: 'automated-dcf-analysis/:symbol/valuation',
        loadComponent: () => import('../components/dcf-analysis').then(m => m.DCFAnalysisPageContainer),
    },
    ...(legacyBullbeargptEnabled
        ? [
            {
                path: 'notebook',
                loadComponent: () => import('../components/shared/notebook/notebook-page.component').then(m => m.NotebookPageComponent),
            },
            {
                path: 'notebook/:sessionId',
                loadComponent: () => import('../components/shared/notebook/notebook-page.component').then(m => m.NotebookPageComponent),
            },
        ]
        : []),
    {
        path: '**',
        loadComponent: () => import('../components/not-found/not-found.component').then(m => m.NotFoundComponent)
    }
];

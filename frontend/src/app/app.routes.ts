import { Routes } from '@angular/router';

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
    {
        path: '**',
        loadComponent: () => import('../components/not-found/not-found.component').then(m => m.NotFoundComponent)
    }
];

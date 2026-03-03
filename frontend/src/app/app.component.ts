import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, AfterViewInit, DestroyRef, inject } from '@angular/core';
import { RouteConfigLoadEnd, RouteConfigLoadStart, Router, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ToastModule } from 'primeng/toast';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import * as CookieConsent from 'vanilla-cookieconsent';
import { environment } from '../env/environment';
import { NgxSonnerToaster } from 'ngx-sonner';
import { PlatformDetectionService } from '../core/services';

declare var grecaptcha: any;

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, ToastModule, ProgressSpinnerModule, NgxSonnerToaster],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  providers: [MessageService]
})
export class AppComponent implements AfterViewInit {
  loading = false;
  isBrowser = false;
  private destroyRef = inject(DestroyRef);

  constructor(
    private router: Router,
    private cd: ChangeDetectorRef,
    private platformDetection: PlatformDetectionService
  ) {
    if (environment.production) {
      //console.log = function():void{}; 
      //console.debug = function():void{};
      //console.warn = function():void{};
      //console.info = function():void{};
    }
    this.isBrowser = !!this.platformDetection.getWindow();
    // Handle loading states for lazy-loaded routes
    this.router.events
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(event => {
        if (event instanceof RouteConfigLoadStart) {
          this.loading = true;
          this.cd.detectChanges();
        } else if (event instanceof RouteConfigLoadEnd) {
          this.loading = false;
          this.cd.detectChanges();
        }
      });
  }

  ngOnInit(): void {
    // Component initialization complete
    this.isBrowser = !!this.platformDetection.getWindow();
  }

  ngAfterViewInit(): void {
    // Only initialize cookie consent in browser
    if (!this.isBrowser) return;

    setTimeout(() => {
      CookieConsent.run({
        onFirstConsent: ({ cookie }) => {
        },

        onConsent: ({ cookie }) => {
        },

        onChange: ({ changedCategories, changedServices }) => {
        },

        onModalReady: ({ modalName }) => {
        },

        onModalShow: ({ modalName }) => {
        },

        onModalHide: ({ modalName }) => {
        },

        categories: {
          necessary: {
            enabled: true, // this category is enabled by default
            readOnly: true, // this category cannot be disabled
          },
          analytics: {
            autoClear: {
              cookies: [
                {
                  name: /^_ga/, // regex: match all cookies starting with '_ga'
                },
                {
                  name: '_gid', // string: exact cookie name
                },
              ],
            },

            services: {
              ga: {
                label: 'Google Analytics (ga)',
                onAccept: () => { },
                onReject: () => { },
              },
              gid: {
                label: 'Google Analytics (gid)',
                onAccept: () => { },
                onReject: () => { },
              },
            },
          },
          ads: {},
        },

        language: {
          default: 'en',
          translations: {
            en: {
              consentModal: {
                title: 'We use cookies',
                description:
                  'This website uses cookies to ensure you get the best experience on our website.',
                acceptAllBtn: 'Accept all',
                acceptNecessaryBtn: 'Reject all',
                showPreferencesBtn: 'Manage Individual preferences',
                // closeIconLabel: 'Reject all and close modal',
                //footer: `
                //      <a href="#path-to-impressum.html" target="_blank">Impressum</a>
                //      <a href="#path-to-privacy-policy.html" target="_blank">Privacy Policy</a>
                //  `,
              },
              preferencesModal: {
                title: 'Manage cookie preferences',
                acceptAllBtn: 'Accept all',
                acceptNecessaryBtn: 'Reject all',
                savePreferencesBtn: 'Accept current selection',
                closeIconLabel: 'Close modal',
                serviceCounterLabel: 'Service|Services',
                sections: [
                  //{
                  //  title: 'Your Privacy Choices',
                  //  description: `In this panel you can express some preferences related to the processing of your personal information. You may review and change expressed choices at any time by resurfacing this panel via the provided link. To deny your consent to the specific processing activities described below, switch the toggles to off or use the “Reject all” button and confirm you want to save your choices.`,
                  //},
                  {
                    title: 'Strictly Necessary',
                    description:
                      'These cookies are essential for the proper functioning of the website and cannot be disabled.',
                    linkedCategory: 'necessary',
                  },
                  {
                    title: 'Advertising and Analytics',
                    description:
                      'We use cookies from companies in the following categories to offer features like personalized advertising, allowing us to tailor ads for you outside of our Products.',
                    linkedCategory: 'analytics',
                    cookieTable: {
                      caption: 'Cookie table',
                      headers: {
                        name: 'Cookie',
                        domain: 'Domain',
                        desc: 'Description',
                      },
                      body: [
                        {
                          name: '_ga',
                          domain: this.platformDetection.getWindow()?.location.hostname || 'stockvaluation.io',
                          desc: 'This cookie is used to distinguish users and has a typical lifespan of 2 years',
                        },
                        {
                          name: '_gid',
                          domain: this.platformDetection.getWindow()?.location.hostname || 'stockvaluation.io',
                          desc: 'This cookie is used to distinguish users within a session and has a typical lifespan of 24 hours',
                        },
                      ],
                    },
                  },
                ],
              },
            },
          },
        },
      });
    }, 1000)


  }


}

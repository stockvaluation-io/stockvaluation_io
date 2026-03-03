import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { environment } from './env/environment';

function hardenConsoleForProduction(): void {
  if (!environment.hardenConsole) {
    return;
  }

  const noop = () => undefined;
  const methods: Array<'log' | 'info' | 'debug' | 'trace'> = [
    'log',
    'info',
    'debug',
    'trace',
  ];

  for (const method of methods) {
    try {
      Object.defineProperty(console, method, {
        value: noop,
        writable: false,
        configurable: false,
      });
    } catch {
      console[method] = noop;
    }
  }
}

hardenConsoleForProduction();

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));

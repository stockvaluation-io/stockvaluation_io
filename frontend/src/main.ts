import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { environment } from './env/environment';

function hardenConsoleForProduction(): void {
  if (!environment.production) {
    return;
  }

  const noop = () => undefined;
  console.log = noop;
  console.info = noop;
  console.debug = noop;
  console.warn = noop;
}

hardenConsoleForProduction();

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));

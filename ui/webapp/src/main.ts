import { MAT_BUTTON_TOGGLE_DEFAULT_OPTIONS, MatButtonToggleDefaultOptions } from '@angular/material/button-toggle';
import { bootstrapApplication } from '@angular/platform-browser';
import { environment } from 'src/environments/environment';
import { provideCoreDependencies } from './app/modules/core/core.deps';
import { LoadingBarHttpClientModule } from '@ngx-loading-bar/http-client';
import { LoadingBarRouterModule } from '@ngx-loading-bar/router';
import { AppComponent } from './app/app.component';
import { importProvidersFrom } from '@angular/core';
import { PreloadAllModules, provideRouter, withHashLocation, withPreloading } from '@angular/router';
import { APP_ROUTES } from './app/app.routing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

const toggleOpts: MatButtonToggleDefaultOptions = {
  hideSingleSelectionIndicator: true,
  hideMultipleSelectionIndicator: true
};

bootstrapApplication(AppComponent, {
  providers: [
    importProvidersFrom(LoadingBarHttpClientModule, LoadingBarRouterModule),
    { provide: MAT_BUTTON_TOGGLE_DEFAULT_OPTIONS, useValue: toggleOpts },
    provideAnimationsAsync(environment.uiTest ? 'noop' : 'animations'),
    provideCoreDependencies(),
    provideRouter(APP_ROUTES, withHashLocation(), withPreloading(PreloadAllModules))
  ]
}).catch(err => console.error(err));

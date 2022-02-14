import { NgModule } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import {
  MatTooltipDefaultOptions,
  MAT_TOOLTIP_DEFAULT_OPTIONS,
} from '@angular/material/tooltip';
import { BrowserModule } from '@angular/platform-browser';
import {
  BrowserAnimationsModule,
  NoopAnimationsModule,
} from '@angular/platform-browser/animations';
import { LoadingBarHttpClientModule } from '@ngx-loading-bar/http-client';
import { LoadingBarRouterModule } from '@ngx-loading-bar/router';
import { environment } from 'src/environments/environment';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { CoreModule } from './modules/core/core.module';

export const delayTooltipsForTests: Partial<MatTooltipDefaultOptions> = {
  showDelay: 30000,
};

@NgModule({
  declarations: [AppComponent],
  imports: [
    BrowserModule,
    environment.animations ? BrowserAnimationsModule : NoopAnimationsModule,
    CoreModule,
    AppRoutingModule,
    LoadingBarHttpClientModule,
    LoadingBarRouterModule,
    MatProgressBarModule,
  ],
  // in case animations are disabled we're in test mode, and need to delay all tooltips *a lot*.
  providers: environment.animations
    ? []
    : [
        {
          provide: MAT_TOOLTIP_DEFAULT_OPTIONS,
          useValue: delayTooltipsForTests,
        },
      ],
  bootstrap: [AppComponent],
})
export class AppModule {}

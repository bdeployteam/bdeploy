import { NgModule } from '@angular/core';
import { MAT_BUTTON_TOGGLE_DEFAULT_OPTIONS, MatButtonToggleDefaultOptions } from '@angular/material/button-toggle';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule, NoopAnimationsModule } from '@angular/platform-browser/animations';
import { LoadingBarHttpClientModule } from '@ngx-loading-bar/http-client';
import { LoadingBarRouterModule } from '@ngx-loading-bar/router';
import { environment } from 'src/environments/environment';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { CoreModule } from './modules/core/core.module';

const toggleOpts: MatButtonToggleDefaultOptions = {
  hideSingleSelectionIndicator: true,
  hideMultipleSelectionIndicator: true,
};

@NgModule({
  declarations: [AppComponent],
  imports: [
    BrowserModule,
    environment.uiTest ? NoopAnimationsModule : BrowserAnimationsModule,
    CoreModule,
    AppRoutingModule,
    LoadingBarHttpClientModule,
    LoadingBarRouterModule,
    MatProgressBarModule,
  ],
  providers: [{ provide: MAT_BUTTON_TOGGLE_DEFAULT_OPTIONS, useValue: toggleOpts }],
  bootstrap: [AppComponent],
})
export class AppModule {}

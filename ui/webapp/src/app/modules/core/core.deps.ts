import { DragDropModule } from '@angular/cdk/drag-drop';
import { LayoutModule } from '@angular/cdk/layout';
import { PlatformModule } from '@angular/cdk/platform';
import { CdkScrollableModule } from '@angular/cdk/scrolling';
import { TextFieldModule } from '@angular/cdk/text-field';
import { CommonModule } from '@angular/common';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import {
    EnvironmentProviders,
    ErrorHandler,
    importProvidersFrom,
    inject,
    provideAppInitializer,
    Provider
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatRippleModule } from '@angular/material/core';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTreeModule } from '@angular/material/tree';
import { RouterModule } from '@angular/router';
import { AuthModule } from '@auth0/auth0-angular';
import { NgTerminalModule } from 'ng-terminal';
import { GravatarModule } from 'ngx-gravatar';
import { GlobalErrorHandler } from 'src/app/modules/core/global-error-handler';
import { httpInterceptorProviders } from './interceptors';
import { ConfigService } from './services/config.service';
import { provideZxvbnServiceForPSM } from 'angular-password-strength-meter/zxcvbn';

function loadAppConfig(cfgService: ConfigService) {
  return () => cfgService.load();
}

export function provideCoreDependencies(): (EnvironmentProviders | Provider)[] {
    return [
        provideHttpClient(withInterceptorsFromDi()),
        httpInterceptorProviders,
        /* make sure that ConfigService and HistoryService are initialize always on startup */
        provideAppInitializer(() => {
            const initializerFn = (loadAppConfig)(inject(ConfigService));
            return initializerFn();
        }),
        provideZxvbnServiceForPSM(),
        { provide: ErrorHandler, useClass: GlobalErrorHandler },
        importProvidersFrom([
            CommonModule,
            PlatformModule,
            DragDropModule,
            CdkScrollableModule,
            TextFieldModule,
            AuthModule.forRoot(),
            // angular material components used in our own components.
            MatButtonModule,
            MatToolbarModule,
            MatIconModule,
            MatListModule,
            MatSnackBarModule,
            MatCardModule,
            MatFormFieldModule,
            MatInputModule,
            MatSelectModule,
            MatTableModule,
            MatDividerModule,
            MatAutocompleteModule,
            MatTooltipModule,
            MatProgressBarModule,
            MatProgressSpinnerModule,
            MatCheckboxModule,
            MatRippleModule,
            MatSlideToggleModule,
            MatBadgeModule,
            MatTreeModule,
            MatSortModule,
            MatTabsModule,
            MatExpansionModule,
            MatButtonToggleModule,
            MatRadioModule,
            MatDialogModule,
            // angular base infrastructure used throughout the application.
            FormsModule,
            RouterModule,
            LayoutModule,
            // additional libraries used to provide cool UI :)
            GravatarModule,
            NgTerminalModule
        ])
    ];
}

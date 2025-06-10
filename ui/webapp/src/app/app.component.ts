
import { Component, inject, DOCUMENT } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { environment } from 'src/environments/environment';
import { NavAreasService } from './modules/core/services/nav-areas.service';
import { LoadingBarModule } from '@ngx-loading-bar/core';
import { MainNavComponent } from './modules/core/components/main-nav/main-nav.component';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    imports: [LoadingBarModule, MainNavComponent, RouterOutlet]
})
export class AppComponent {
  private readonly router = inject(Router);
  private readonly areas = inject(NavAreasService);

  subscription: Subscription;

  constructor() {
    const document = inject<Document>(DOCUMENT);

    console.log('----------------------------------------');
    console.log('BDeploy started...');
    console.log('----------------------------------------');

    // in case of UI tests we may need some additional classes. this dummy class gives the scope.
    if (environment.uiTest) {
      document.body.classList.add('ui-test');
    }
  }
}

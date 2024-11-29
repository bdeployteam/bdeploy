import { Component, inject } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
    selector: 'app-auth0-tab',
    templateUrl: './auth0-tab.component.html',
    standalone: false
})
export class Auth0TabComponent {
  protected readonly settings = inject(SettingsService);
}

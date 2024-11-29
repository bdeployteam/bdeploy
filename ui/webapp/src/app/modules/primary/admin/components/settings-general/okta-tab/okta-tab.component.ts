import { Component, inject } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
    selector: 'app-okta-tab',
    templateUrl: './okta-tab.component.html',
    standalone: false
})
export class OktaTabComponent {
  protected readonly settings = inject(SettingsService);
}

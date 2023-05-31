import { Component } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-okta-tab',
  templateUrl: './okta-tab.component.html',
})
export class OktaTabComponent {
  constructor(public settings: SettingsService) {}
}

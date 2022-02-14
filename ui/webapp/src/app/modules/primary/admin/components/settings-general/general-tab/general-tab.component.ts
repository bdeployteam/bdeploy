import { Component } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-general-tab',
  templateUrl: './general-tab.component.html',
})
export class GeneralTabComponent {
  constructor(public settings: SettingsService) {}
}

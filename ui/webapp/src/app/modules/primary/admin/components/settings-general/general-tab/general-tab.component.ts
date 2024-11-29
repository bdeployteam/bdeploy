import { Component, inject } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
    selector: 'app-general-tab',
    templateUrl: './general-tab.component.html',
    standalone: false
})
export class GeneralTabComponent {
  protected readonly settings = inject(SettingsService);
}

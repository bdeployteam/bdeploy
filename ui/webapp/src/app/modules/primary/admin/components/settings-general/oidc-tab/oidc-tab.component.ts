import { Component, inject } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-oidc-tab',
  templateUrl: './oidc-tab.component.html',
})
export class OidcTabComponent {
  protected readonly settings = inject(SettingsService);
}

import { Component, inject } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { BdFormToggleComponent } from '../../../../../core/components/bd-form-toggle/bd-form-toggle.component';
import { FormsModule } from '@angular/forms';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';

@Component({
    selector: 'app-auth0-tab',
    templateUrl: './auth0-tab.component.html',
    imports: [BdFormToggleComponent, FormsModule, BdFormInputComponent]
})
export class Auth0TabComponent {
  protected readonly settings = inject(SettingsService);
}

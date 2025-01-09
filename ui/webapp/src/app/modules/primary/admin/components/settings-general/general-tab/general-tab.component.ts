import { Component, inject } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { BdFormToggleComponent } from '../../../../../core/components/bd-form-toggle/bd-form-toggle.component';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'app-general-tab',
    templateUrl: './general-tab.component.html',
    imports: [BdFormToggleComponent, FormsModule]
})
export class GeneralTabComponent {
  protected readonly settings = inject(SettingsService);
}

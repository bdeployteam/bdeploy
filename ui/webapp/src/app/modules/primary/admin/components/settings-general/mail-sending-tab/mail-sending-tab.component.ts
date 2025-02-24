import { Component, inject } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { FormsModule } from '@angular/forms';

import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'app-mail-sending-tab',
    templateUrl: './mail-sending-tab.component.html',
  imports: [FormsModule, MatIcon]
})
export class MailSendingTabComponent {
  protected readonly settings = inject(SettingsService);
}

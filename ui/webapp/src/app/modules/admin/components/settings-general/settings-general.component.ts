import { Component, OnInit } from '@angular/core';
import { Observable, of } from 'rxjs';
import { MessageBoxMode } from 'src/app/modules/core/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/core/services/messagebox.service';
import { SettingsService } from '../../../core/services/settings.service';

@Component({
  selector: 'app-settings-general',
  templateUrl: './settings-general.component.html',
  styleUrls: ['./settings-general.component.css'],
})
export class SettingsGeneralComponent implements OnInit {
  constructor(public settings: SettingsService, private messageBoxService: MessageboxService) {}

  ngOnInit() {}

  canDeactivate(): Observable<boolean> {
    if (!this.settings.isDirty()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Settings were modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }
}

import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { cloneDeep } from 'lodash-es';
import { Observable, of } from 'rxjs';
import { CustomPropertyDescriptor, MinionMode } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { CustomPropertyEditComponent } from 'src/app/modules/shared/components/custom-property-edit/custom-property-edit.component';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';


@Component({
  selector: 'app-settings-instance-group',
  templateUrl: './settings-instance-group.component.html',
  styleUrls: ['./settings-instance-group.component.css'],
  providers: [SettingsService]
})
export class SettingsInstanceGroupComponent implements OnInit {

  private log: Logger = this.loggingService.getLogger('SettingsInstanceGroupComponent');

  constructor(
    private dialog: MatDialog,
    private messageBoxService: MessageboxService,
    private loggingService: LoggingService,
    public settings: SettingsService,
    private config: ConfigService
  ) { }

  ngOnInit() {
  }

  isPropertiesEditable(): boolean {
    return this.config.config.mode !== MinionMode.MANAGED;
  }

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

  add() {
    this.dialog.open(CustomPropertyEditComponent, {
      width: '500px',
      data: null,
    }).afterClosed().subscribe(r => {
      if (r) {
        this.getProperties().push(r);
        this.sortProperties();
      }
    });
  }

  edit(property: CustomPropertyDescriptor, index: number) {
    this.dialog.open(CustomPropertyEditComponent, {
      width: '500px',
      data: cloneDeep(property),
    }).afterClosed().subscribe(r => {
      if (r) {
        this.getProperties().splice(index, 1, r);
        this.sortProperties();
      }
    });
  }

  remove(index: number) {
    this.getProperties().splice(index, 1);
  }

  getProperties(): CustomPropertyDescriptor[] {
    return this.settings.getSettings().instanceGroup.properties;
  }

  hasProperties(): boolean {
    return this.settings?.getSettings()?.instanceGroup?.properties.length > 0;
  }

  private sortProperties() {
    if (this.hasProperties()) {
      this.settings.getSettings().instanceGroup.properties = this.settings.getSettings().instanceGroup.properties.sort((a, b) => a.name.localeCompare(b.name));
    }
  }

}

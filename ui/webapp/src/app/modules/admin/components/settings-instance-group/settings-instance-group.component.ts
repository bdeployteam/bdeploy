import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { cloneDeep } from 'lodash-es';
import { Observable, of } from 'rxjs';
import { CustomAttributeDescriptor, MinionMode } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { CustomAttributeEditComponent } from 'src/app/modules/shared/components/custom-attribute-edit/custom-attribute-edit.component';
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

  isAttributesEditable(): boolean {
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
    this.dialog.open(CustomAttributeEditComponent, {
      width: '500px',
      data: null,
    }).afterClosed().subscribe(r => {
      if (r) {
        this.getAttributes().push(r);
        this.sortAttributes();
      }
    });
  }

  edit(attribute: CustomAttributeDescriptor, index: number) {
    this.dialog.open(CustomAttributeEditComponent, {
      width: '500px',
      data: cloneDeep(attribute),
    }).afterClosed().subscribe(r => {
      if (r) {
        this.getAttributes().splice(index, 1, r);
        this.sortAttributes();
      }
    });
  }

  remove(index: number) {
    this.getAttributes().splice(index, 1);
  }

  getAttributes(): CustomAttributeDescriptor[] {
    return this.settings.getSettings().instanceGroup.attributes;
  }

  hasAttributes(): boolean {
    return this.settings?.getSettings()?.instanceGroup?.attributes.length > 0;
  }

  private sortAttributes() {
    if (this.hasAttributes()) {
      this.settings.getSettings().instanceGroup.attributes = this.settings.getSettings().instanceGroup.attributes.sort((a, b) => a.name.localeCompare(b.name));
    }
  }

}

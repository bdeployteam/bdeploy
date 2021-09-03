import { Component, forwardRef, Inject, Input, OnInit } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { PluginAdminService } from 'src/app/modules/admin/services/plugin-admin.service';
import { SettingsGeneralComponent } from '../../settings-general.component';

@Component({
  selector: 'app-plugin-delete-action',
  templateUrl: './plugin-delete-action.component.html',
  styleUrls: ['./plugin-delete-action.component.css'],
})
export class PluginDeleteActionComponent implements OnInit {
  @Input() record: PluginInfoDto;

  constructor(@Inject(forwardRef(() => SettingsGeneralComponent)) private parent: SettingsGeneralComponent, private plugins: PluginAdminService) {}

  ngOnInit(): void {}

  /* template */ doDelete() {
    this.parent.dialog
      .confirm(`Delete ${this.record.name} ${this.record.version}`, 'Are you sure? The plugin will be deleted permanently.', 'warning')
      .subscribe((r) => {
        if (r) {
          this.plugins.deleteGlobalPlugin(this.record);
        }
      });
  }
}

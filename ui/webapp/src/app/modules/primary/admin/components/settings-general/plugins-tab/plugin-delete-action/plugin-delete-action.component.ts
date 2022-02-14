import { Component, forwardRef, Inject, Input } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { PluginAdminService } from 'src/app/modules/primary/admin/services/plugin-admin.service';
import { SettingsGeneralComponent } from '../../settings-general.component';

@Component({
  selector: 'app-plugin-delete-action',
  templateUrl: './plugin-delete-action.component.html',
})
export class PluginDeleteActionComponent {
  @Input() record: PluginInfoDto;

  constructor(
    @Inject(forwardRef(() => SettingsGeneralComponent))
    private parent: SettingsGeneralComponent,
    private plugins: PluginAdminService
  ) {}

  /* template */ doDelete() {
    this.parent.dialog
      .confirm(
        `Delete ${this.record.name} ${this.record.version}`,
        'Are you sure? The plugin will be deleted permanently.',
        'warning'
      )
      .subscribe((r) => {
        if (r) {
          this.plugins.deleteGlobalPlugin(this.record);
        }
      });
  }
}

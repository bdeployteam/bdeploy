import { Component, Input } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { PluginAdminService } from 'src/app/modules/primary/admin/services/plugin-admin.service';

@Component({
  selector: 'app-plugin-load-action',
  templateUrl: './plugin-load-action.component.html',
})
export class PluginLoadActionComponent {
  @Input() record: PluginInfoDto;

  constructor(private plugins: PluginAdminService) {}

  /* template */ doLoadUnload() {
    if (this.record.loaded) {
      this.plugins.unloadPlugin(this.record);
    } else {
      this.plugins.loadGlobalPlugin(this.record);
    }
  }
}

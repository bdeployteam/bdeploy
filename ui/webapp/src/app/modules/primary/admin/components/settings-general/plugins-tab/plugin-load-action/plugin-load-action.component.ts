import { Component, Input, inject } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { PluginAdminService } from 'src/app/modules/primary/admin/services/plugin-admin.service';

@Component({
    selector: 'app-plugin-load-action',
    templateUrl: './plugin-load-action.component.html',
    standalone: false
})
export class PluginLoadActionComponent {
  private readonly plugins = inject(PluginAdminService);

  @Input() record: PluginInfoDto;

  protected doLoadUnload() {
    if (this.record.loaded) {
      this.plugins.unloadPlugin(this.record);
    } else {
      this.plugins.loadGlobalPlugin(this.record);
    }
  }
}

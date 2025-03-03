import { Component, Input, inject } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { PluginAdminService } from 'src/app/modules/primary/admin/services/plugin-admin.service';
import { BdButtonComponent } from '../../../../../../core/components/bd-button/bd-button.component';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDataColumn } from '../../../../../../../models/data';
import {
  TableCellDisplay
} from '../../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-plugin-load-action',
    templateUrl: './plugin-load-action.component.html',
    imports: [BdButtonComponent, MatTooltip]
})
export class PluginLoadActionComponent implements TableCellDisplay<PluginInfoDto> {
  private readonly plugins = inject(PluginAdminService);

  @Input() record: PluginInfoDto;
  @Input() column: BdDataColumn<PluginInfoDto>;

  protected doLoadUnload() {
    if (this.record.loaded) {
      this.plugins.unloadPlugin(this.record);
    } else {
      this.plugins.loadGlobalPlugin(this.record);
    }
  }
}

import { Component, Input, forwardRef, inject } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { PluginAdminService } from 'src/app/modules/primary/admin/services/plugin-admin.service';
import { SettingsGeneralComponent } from '../../settings-general.component';
import { BdButtonComponent } from '../../../../../../core/components/bd-button/bd-button.component';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDataColumn } from '../../../../../../../models/data';
import {
  CellComponent
} from '../../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-plugin-delete-action',
    templateUrl: './plugin-delete-action.component.html',
    imports: [BdButtonComponent, MatTooltip]
})
export class PluginDeleteActionComponent implements CellComponent<PluginInfoDto, PluginInfoDto> {
  private readonly parent = inject(forwardRef(() => SettingsGeneralComponent));
  private readonly plugins = inject(PluginAdminService);

  @Input() record: PluginInfoDto;
  @Input() column: BdDataColumn<PluginInfoDto, PluginInfoDto>;

  protected doDelete() {
    this.parent.dialog
      .confirm(
        `Delete ${this.record.name} ${this.record.version}`,
        'Are you sure? The plugin will be deleted permanently.',
        'warning',
      )
      .subscribe((r: boolean) => {
        if (r) {
          this.plugins.deleteGlobalPlugin(this.record);
        }
      });
  }
}

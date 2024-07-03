import { Injectable, inject } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { FileStatusType } from 'src/app/models/gen.dtos';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { ConfigFilesActionsComponent } from '../components/instance-settings/config-files/config-files-actions/config-files-actions.component';
import { ConfigFile, ConfigFilesService } from './config-files.service';

@Injectable({
  providedIn: 'root',
})
export class ConfigFilesColumnsService {
  private readonly cfgFiles = inject(ConfigFilesService);

  private readonly colStatus: BdDataColumn<ConfigFile> = {
    id: 'status',
    name: 'Status',
    data: (r) => this.getStatusIcon(r),
    width: '30px',
    component: BdDataIconCellComponent,
    showWhen: '(min-width: 500px)',
  };

  private readonly colFileName: BdDataColumn<ConfigFile> = {
    id: 'name',
    name: 'Name',
    data: (r) => (r.persistent?.path ? r.persistent.path : r.modification.file),
  };

  private readonly colProductState: BdDataColumn<ConfigFile> = {
    id: 'prodState',
    name: 'Sync. State',
    data: (r) => this.getSyncStateText(r),
    classes: (r) => (this.cfgFiles.getStatus(r) === 'local' ? ['bd-secondary-text'] : []),
    showWhen: '(min-width: 800px)',
  };

  private readonly colActions: BdDataColumn<ConfigFile> = {
    id: 'actions',
    name: 'Actions',
    data: (r) => r,
    component: ConfigFilesActionsComponent,
    width: '230px',
  };

  public readonly defaultColumns: BdDataColumn<ConfigFile>[] = [
    this.colFileName,
    this.colStatus,
    this.colProductState,
    this.colActions,
  ];

  private getStatusIcon(r: ConfigFile) {
    if (!r.modification) {
      return null;
    }

    switch (r.modification.type) {
      case FileStatusType.ADD:
        if (this.cfgFiles.isMoved(r)) {
          return 'drive_file_rename_outline';
        }
        return 'add';
      case FileStatusType.EDIT:
        return 'edit';
      case FileStatusType.DELETE:
        return 'delete';
    }
  }

  private getSyncStateText(r: ConfigFile) {
    const s = this.cfgFiles.getStatus(r);
    switch (s) {
      case 'local':
        return 'File not in selected product version.';
      case 'unsync':
        return 'File differs from product.';
      case 'missing':
        return 'File only in product templates.';
    }
    return '';
  }
}

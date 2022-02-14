import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { FileStatusType } from 'src/app/models/gen.dtos';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { DeleteActionComponent } from '../components/instance-settings/config-files/delete-action/delete-action.component';
import { EditActionComponent } from '../components/instance-settings/config-files/edit-action/edit-action.component';
import { ProductSyncComponent } from '../components/instance-settings/config-files/product-sync/product-sync.component';
import { RenameActionComponent } from '../components/instance-settings/config-files/rename-action/rename-action.component';
import { ConfigFile, ConfigFilesService } from './config-files.service';

@Injectable({
  providedIn: 'root',
})
export class ConfigFilesColumnsService {
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
    data: (r) => r,
    component: ProductSyncComponent,
    showWhen: '(min-width: 800px)',
  };

  private readonly colDelete: BdDataColumn<ConfigFile> = {
    id: 'delete',
    name: 'Delete',
    data: (r) => r,
    component: DeleteActionComponent,
    width: '65px',
  };

  private readonly colRename: BdDataColumn<ConfigFile> = {
    id: 'rename',
    name: 'Rename',
    data: (r) => r,
    component: RenameActionComponent,
    width: '65px',
    showWhen: '(min-width: 600px)',
  };

  private readonly colEdit: BdDataColumn<ConfigFile> = {
    id: 'edit',
    name: 'Edit',
    data: (r) => r,
    component: EditActionComponent,
    width: '65px',
  };

  public defaultColumns: BdDataColumn<ConfigFile>[] = [
    this.colFileName,
    this.colStatus,
    this.colProductState,
    this.colDelete,
    this.colRename,
    this.colEdit,
  ];

  constructor(private cfgFiles: ConfigFilesService) {}

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
}

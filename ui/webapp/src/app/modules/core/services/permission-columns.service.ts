import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { ScopedPermission } from 'src/app/models/gen.dtos';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';

const COL_SCOPE: BdDataColumn<ScopedPermission> = {
  id: 'scope',
  name: 'Scope',
  data: (r) => (r.scope ? r.scope : 'Global'),
  classes: (r) => (r.scope ? [] : ['bd-text-secondary']),
};

const COL_PERMISSION: BdDataColumn<ScopedPermission> = {
  id: 'permission',
  name: 'Permission',
  data: (r) => r.permission,
  component: BdDataPermissionLevelCellComponent,
};

@Injectable({
  providedIn: 'root',
})
export class PermissionColumnsService {
  public defaultPermissionColumns = [COL_SCOPE, COL_PERMISSION];
}

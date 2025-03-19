import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { Permission, ScopedPermission } from 'src/app/models/gen.dtos';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';

const colScope: BdDataColumn<ScopedPermission, string> = {
  id: 'scope',
  name: 'Scope',
  data: (r) => (r.scope ? r.scope : 'Global'),
  classes: (r) => (r.scope ? [] : ['bd-text-secondary']),
};

const colPermission: BdDataColumn<ScopedPermission, Permission> = {
  id: 'permission',
  name: 'Permission',
  data: (r) => r.permission,
  component: BdDataPermissionLevelCellComponent,
};

@Injectable({
  providedIn: 'root',
})
export class PermissionColumnsService {
  public defaultPermissionColumns: BdDataColumn<ScopedPermission, unknown>[] = [colScope, colPermission];
}

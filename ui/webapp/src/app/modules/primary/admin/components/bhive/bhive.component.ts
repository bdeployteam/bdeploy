import { Component, OnInit, inject } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BdDataColumn } from 'src/app/models/data';
import { HiveInfoDto, HiveType } from 'src/app/models/gen.dtos';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { HiveService } from '../../services/hive.service';
import { PoolingStatusCellComponent } from './pooling-status-cell/pooling-status-cell.component';

const colAvatar: BdDataColumn<HiveInfoDto> = {
  id: 'avatar',
  name: '',
  data: (r) =>
    r.type === HiveType.PLAIN ? 'sd_storage' : r.type === HiveType.INSTANCE_GROUP ? 'view_carousel' : 'storage',
  component: BdDataIconCellComponent,
  width: '30px',
};

const colId: BdDataColumn<HiveInfoDto> = {
  id: 'id',
  name: 'BHive',
  data: (r) => r.name,
};

const colPoolEnabled: BdDataColumn<HiveInfoDto> = {
  id: 'isPooling',
  name: 'Pool Enabled',
  data: (r) => r.pooling,
  component: PoolingStatusCellComponent,
  width: '60px',
};

const colPerm: BdDataColumn<HiveInfoDto> = {
  id: 'minPerm',
  name: 'Min. Perm.',
  data: (r) => r.minPermission,
  component: BdDataPermissionLevelCellComponent,
  width: '120px',
};

@Component({
  selector: 'app-bhive',
  templateUrl: './bhive.component.html',
})
export class BHiveComponent implements OnInit {
  protected readonly hives = inject(HiveService);

  protected readonly columns: BdDataColumn<HiveInfoDto>[] = [colAvatar, colId, colPoolEnabled, colPerm];
  protected sort: Sort = { active: 'id', direction: 'asc' };

  protected getRecordRoute = (row: HiveInfoDto) => ['', { outlets: { panel: ['panels', 'admin', 'bhive', row.name] } }];

  ngOnInit() {
    this.load();
  }

  protected load() {
    this.hives.loadHives();
  }
}

import { Component } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataSvgIconCellComponent } from 'src/app/modules/core/components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import {
  MinionRecord,
  NodesAdminService,
} from '../../services/nodes-admin.service';

const nodeColName: BdDataColumn<MinionRecord> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const nodeColStatus: BdDataColumn<MinionRecord> = {
  id: 'status',
  name: 'Status',
  data: (r) =>
    r.status.offline
      ? 'Offline' + (r.status.infoText ? ' (' + r.status.infoText + ')' : '')
      : 'Online',
};

const nodeColVersion: BdDataColumn<MinionRecord> = {
  id: 'version',
  name: 'Version',
  data: (r) =>
    r.status.config?.version ? convert2String(r.status.config.version) : '',
  tooltip: (r) => 'The last known version of the node',
  width: '140px',
};

const nodeColOs: BdDataColumn<MinionRecord> = {
  id: 'os',
  name: 'OS',
  data: (r) => r.status.config?.os,
  component: BdDataSvgIconCellComponent,
  width: '30px',
};

@Component({
  selector: 'app-nodes',
  templateUrl: './nodes.component.html',
})
export class NodesComponent {
  /* template */ sort: Sort = { active: 'name', direction: 'asc' };
  /* template */ getRecordRoute = (row: MinionRecord) => {
    return [
      '',
      { outlets: { panel: ['panels', 'admin', 'node-detail', row.name] } },
    ];
  };
  /* template */ columns: BdDataColumn<MinionRecord>[] = [
    nodeColName,
    nodeColStatus,
    nodeColVersion,
    nodeColOs,
  ];

  constructor(public nodesAdmin: NodesAdminService) {}
}

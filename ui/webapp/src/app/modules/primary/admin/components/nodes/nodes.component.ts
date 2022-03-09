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
  width: '180px',
};

const nodeColStatus: BdDataColumn<MinionRecord> = {
  id: 'status',
  name: 'Status',
  data: (r) => (r.status.offline ? 'Offline' : 'Online'),
  width: '80px',
};

const nodeColInfo: BdDataColumn<MinionRecord> = {
  id: 'info',
  name: 'Additional Information',
  data: (r) => r.status.infoText,
  showWhen: '(min-width: 1280px)',
};

const nodeColVersion: BdDataColumn<MinionRecord> = {
  id: 'version',
  name: 'Version',
  data: (r) =>
    r.status.config?.version ? convert2String(r.status.config.version) : '',
  tooltip: () => 'The last known version of the node',
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
    nodeColInfo,
    nodeColVersion,
    nodeColOs,
  ];

  constructor(public nodesAdmin: NodesAdminService) {}
}

import { Component, inject } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataSvgIconCellComponent } from 'src/app/modules/core/components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { MinionRecord, NodesAdminService } from '../../services/nodes-admin.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';
import { MinionNodeType } from '../../../../../models/gen.dtos';

const nodeColName: BdDataColumn<MinionRecord, string> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  width: '350px',
};

const nodeColStatus: BdDataColumn<MinionRecord, string> = {
  id: 'status',
  name: 'Status',
  data: (r) => (r.status.offline ? 'Offline' : 'Online'),
  width: '80px',
};

const nodeColInfo: BdDataColumn<MinionRecord, string> = {
  id: 'info',
  name: 'Additional Information',
  data: (r) => r.status.infoText,
  showWhen: '(min-width: 1280px)',
};

const nodeColVersion: BdDataColumn<MinionRecord, string> = {
  id: 'version',
  name: 'Version',
  data: (r) => (r.status.config?.version ? convert2String(r.status.config.version) : ''),
  tooltip: () => 'The last known version of the node',
  width: '140px',
};

const nodeColOs: BdDataColumn<MinionRecord, string> = {
  id: 'os',
  name: 'OS',
  data: (r) => r.status.config?.os,
  component: BdDataSvgIconCellComponent,
  width: '30px',
};

@Component({
    selector: 'app-nodes',
    templateUrl: './nodes.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, BdPanelButtonComponent, BdDialogContentComponent, BdDataTableComponent, AsyncPipe]
})
export class NodesComponent {
  protected readonly nodesAdmin = inject(NodesAdminService);

  protected sort: Sort = { active: 'name', direction: 'asc' };
  protected getRecordRoute = (row: MinionRecord) => {
    if (row?.status?.config?.minionNodeType == MinionNodeType.MULTI) {
      return [
        '',
        { outlets: { panel: ['panels', 'admin', 'multi-node-detail', row.name] } }
      ];
    } else {
      return [
        '',
        { outlets: { panel: ['panels', 'admin', 'node-detail', row.name] } }
      ];
    }
  };

  protected readonly columns: BdDataColumn<MinionRecord, unknown>[] = [
    nodeColName,
    nodeColStatus,
    nodeColInfo,
    nodeColVersion,
    nodeColOs,
  ];
}

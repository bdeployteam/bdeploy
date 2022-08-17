import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { ParameterType } from 'src/app/models/gen.dtos';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import {
  ACTION_CANCEL,
  ACTION_CONFIRM,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import {
  PortParam,
  PortsEditService,
} from '../../../services/ports-edit.service';
import { PortTypeCellComponent } from './port-type-cell/port-type-cell.component';

const colName: BdDataColumn<PortParam> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  tooltip: (r) => `${r.description} (source: ${r.source})`,
  tooltipDelay: 50,
};

const colExpr: BdDataColumn<PortParam> = {
  id: 'expression',
  name: 'Expr.',
  data: (r) => (r.expression ? 'check' : null),
  component: BdDataIconCellComponent,
  width: '24px',
};

const colType: BdDataColumn<PortParam> = {
  id: 'type',
  name: 'Type',
  data: (r) => r,
  component: PortTypeCellComponent,
  width: '24px',
};

const colPort: BdDataColumn<PortParam> = {
  id: 'port',
  name: 'Port',
  data: (r) => r.port,
  width: '45px',
};

@Component({
  selector: 'app-ports',
  templateUrl: './ports.component.html',
})
export class PortsComponent implements OnInit {
  /* template */ columns: BdDataColumn<PortParam>[] = [
    colName,
    colExpr,
    colType,
    colPort,
  ];
  /* template */ checked: PortParam[];
  /* template */ ports: PortParam[] = [];
  /* template */ amount: number;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    public edit: InstanceEditService,
    public portEdit: PortsEditService,
    private dl: DownloadService,
    private systems: SystemsService
  ) {}

  ngOnInit(): void {
    this.portEdit.ports$.subscribe((ports) => {
      if (ports?.length) {
        this.ports = ports.filter((p) => !p.expression);
        this.checked = [...this.ports];
      }
    });
  }

  /* template */ exportCsv() {
    let csv = 'Application,Name,Description,Port';
    // only interested in server ports on applications.
    const system = this.edit.state$?.value?.config?.config?.system
      ? this.systems.systems$.value?.find(
          (s) => s.key.name === this.edit.state$.value.config.config.system.name
        )
      : null;
    for (const port of this.portEdit.ports$.value.filter(
      (p) => p.type === ParameterType.SERVER_PORT && p.app
    )) {
      csv +=
        '\n' +
        [
          port.source,
          port.name,
          port.description,
          getRenderPreview(
            port.value,
            port.app,
            this.edit.state$.value?.config,
            system?.config
          ),
        ]
          .map((e) => `"${e}"`)
          .join(',');
    }

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    this.dl.downloadBlob(
      'ports-' + this.edit.current$.value.instanceConfiguration.uuid + '.csv',
      blob
    );
  }

  /* template */ shiftSelectedPorts(tpl: TemplateRef<any>) {
    this.amount = null;
    this.dialog
      .message({
        header: 'Shift ports',
        template: tpl,
        icon: 'electrical_services',
        actions: [ACTION_CANCEL, ACTION_CONFIRM],
      })
      .subscribe((r) => {
        if (r) {
          const errors = this.portEdit.shiftPorts(this.checked, this.amount);
          if (errors.length) {
            console.log(errors);
            this.dialog
              .message({
                header: 'Failed to shift ports.',
                message: `<ul>${errors
                  .map((e) => `<li>${e}</li>`)
                  .join('')}</ul>`,
                actions: [ACTION_CANCEL],
              })
              .subscribe();
          }
        }
      });
  }
}

import { Component, inject, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { VariableType } from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_CONFIRM
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { PortParam, PortsEditService } from '../../../services/ports-edit.service';
import { PortTypeCellComponent } from './port-type-cell/port-type-cell.component';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';
import { FormsModule } from '@angular/forms';

import { BdDialogToolbarComponent } from '../../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { BdDataTableComponent } from '../../../../../core/components/bd-data-table/bd-data-table.component';

const colName: BdDataColumn<PortParam, string> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  tooltip: (r) => `${r.description} (source: ${r.source})`,
  tooltipDelay: 50,
};

const colType: BdDataColumn<PortParam, string> = {
  id: 'type',
  name: 'Type',
  data: (r) => r.type,
  component: PortTypeCellComponent,
  width: '24px',
};

const colPort: BdDataColumn<PortParam, string | number> = {
  id: 'port',
  name: 'Port',
  data: (r) => (isNaN(Number(r.port)) ? r.port : Number(r.port)),
  width: '45px',
};

@Component({
    selector: 'app-ports',
    templateUrl: './ports.component.html',
    imports: [
        BdFormInputComponent,
        FormsModule,
      BdDialogComponent,
        BdDialogToolbarComponent,
        BdDialogContentComponent,
        BdButtonComponent,
        BdDataTableComponent,
    ],
})
export class PortsComponent implements OnInit {
  private readonly dl = inject(DownloadService);
  private readonly systems = inject(SystemsService);
  private readonly instances = inject(InstancesService);
  protected readonly edit = inject(InstanceEditService);
  protected readonly portEdit = inject(PortsEditService);

  protected readonly columns: BdDataColumn<PortParam, unknown>[] = [colName, colType, colPort];
  protected checked: PortParam[];
  protected ports: PortParam[] = [];
  protected amount: number;

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  ngOnInit(): void {
    this.portEdit.ports$.subscribe((ports) => {
      if (ports?.length) {
        this.ports = ports.filter((p) => !p.expression);
        this.checked = [...this.ports];
      }
    });
  }

  protected exportCsv() {
    const columns = ['Application', 'Name', 'Description', 'Port', 'Node'];
    const rows: string[][] = [];
    // only interested in server ports on applications.
    const system = this.edit.state$?.value?.config?.config?.system
      ? this.systems.systems$.value?.find((s) => s.key.name === this.edit.state$.value.config.config.system.name)
      : null;
    const nodeConfigDtos = this.instances.activeNodeCfgs$.value?.nodeConfigDtos;
    for (const port of this.portEdit.ports$.value.filter((p) => p.type === VariableType.SERVER_PORT && p.app)) {
      const node = nodeConfigDtos?.find((nodeCfg) =>
        nodeCfg.nodeConfiguration.applications.some(
          (a) => a.application.name === port.app.application.name && a.application.tag === port.app.application.tag,
        ),
      );
      rows.push([
        port.source,
        port.name,
        port.description,
        getRenderPreview(port.value, port.app, this.edit.state$.value?.config, system?.config),
        node?.nodeName,
      ]);
    }
    const filename = `ports-${this.edit.current$.value.instanceConfiguration.id}.csv`;
    this.dl.downloadCsv(filename, columns, rows);
  }

  protected shiftSelectedPorts(tpl: TemplateRef<unknown>) {
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
                message: `<ul class="list-disc list-inside">${errors.map((e) => `<li>${e}</li>`).join('')}</ul>`,
                actions: [ACTION_CANCEL],
              })
              .subscribe();
          }
        }
      });
  }
}

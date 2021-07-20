import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { ParameterType } from 'src/app/models/gen.dtos';
import { ACTION_CANCEL, ACTION_CONFIRM } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { PortParmGroup, PortsEditService } from '../../../services/ports-edit.service';

const colName: BdDataColumn<PortParmGroup> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.desc.name,
  tooltip: (r) => `Used in ${r.apps[0].name}${r.apps.length > 1 ? ` and ${r.apps.length - 1} other application${r.apps.length > 2 ? 's' : ''}` : ''}`,
  tooltipDelay: 50,
};

const colOccurances: BdDataColumn<PortParmGroup> = {
  id: 'occurances',
  name: 'Occ.',
  description: 'Number of occurences',
  data: (r) => r.params.length,
  width: '20px',
};

const colPort: BdDataColumn<PortParmGroup> = {
  id: 'port',
  name: 'Port',
  data: (r) => r.port,
  width: '45px',
};

@Component({
  selector: 'app-ports',
  templateUrl: './ports.component.html',
  styleUrls: ['./ports.component.css'],
})
export class PortsComponent implements OnInit {
  /* template */ columns: BdDataColumn<PortParmGroup>[] = [colName, colOccurances, colPort];
  /* template */ checked: PortParmGroup[];
  /* template */ amount: number;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(public edit: InstanceEditService, public portEdit: PortsEditService, private dl: DownloadService) {}

  ngOnInit(): void {
    this.portEdit.ports$.subscribe((ports) => {
      this.checked = ports;
    });
  }

  /* template */ exportCsv() {
    let csv = 'Application,Name,Description,Port';
    for (const group of this.portEdit.ports$.value.filter((p) => p.desc.type === ParameterType.SERVER_PORT)) {
      csv +=
        '\n' +
        group.apps
          .map((app) => {
            return [app.name, group.desc.name, group.desc.longDescription, group.port];
          })
          .map((r) => r.map((e) => `"${e}"`).join(','))
          .join('\n');
    }

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    this.dl.downloadBlob('ports-' + this.edit.current$.value.instanceConfiguration.uuid + '.csv', blob);
  }

  /* template */ shiftSelectedPorts(tpl: TemplateRef<any>) {
    this.amount = null;
    this.dialog.message({ header: 'Shift ports', template: tpl, icon: 'electrical_services', actions: [ACTION_CANCEL, ACTION_CONFIRM] }).subscribe((r) => {
      if (r) {
        const errors = this.portEdit.shiftPorts(this.checked, this.amount);
        if (!!errors.length) {
          console.log(errors);
          this.dialog
            .message({
              header: 'Failed to shift ports.',
              message: `<ul>${errors.map((e) => `<li>${e}</li>`).join('')}</ul>`,
              actions: [ACTION_CANCEL],
            })
            .subscribe();
        }
      }
    });
  }
}

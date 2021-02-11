import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import {
  ApplicationConfiguration,
  ApplicationDescriptor,
  ParameterConfiguration,
  ParameterDescriptor,
  ParameterType,
} from 'src/app/models/gen.dtos';
import { URLish } from 'src/app/modules/legacy/shared/utils/url.utils';

export interface ShiftableParameter {
  applicationUid: string;
  applicationName: string;
  cfg: ParameterConfiguration;
  desc: ParameterDescriptor;
  selected: boolean;
  appCfg: ApplicationConfiguration;
  appDesc: ApplicationDescriptor;
}

@Component({
  selector: 'app-instance-shift-ports',
  templateUrl: './instance-shift-ports.component.html',
  styleUrls: ['./instance-shift-ports.component.css'],
})
export class InstanceShiftPortsComponent implements OnInit {
  public columnsToDisplay = ['status', 'type', 'application', 'name', 'current', 'target'];
  public dataSource: MatTableDataSource<ShiftableParameter>;

  constructor(@Inject(MAT_DIALOG_DATA) private params: ShiftableParameter[]) {}

  ngOnInit(): void {
    this.dataSource = new MatTableDataSource(this.params);
  }

  isAtLeastOneSelected() {
    const numSelected = this.params.filter((lp) => lp.selected).length;
    return numSelected > 0;
  }

  isAllSelected() {
    const numSelected = this.params.filter((lp) => lp.selected).length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  toggleSelection() {
    const targetState = this.isAllSelected() ? false : true;
    this.params.forEach((lp) => (lp.selected = targetState));
  }

  toggleRowSelection(row: ShiftableParameter) {
    row.selected = !row.selected;
  }

  getTargetValue(row: ShiftableParameter, amount: any) {
    return (Number(this.getPortValue(row)) + Number(amount)).toString();
  }

  getPortValue(row: ShiftableParameter) {
    if (row.desc.type === ParameterType.URL) {
      // the instance-edit-ports component will give us only parameters where this is valid!
      return new URLish(row.cfg.value).port;
    }
    return row.cfg.value;
  }

  getTargetClass(row: ShiftableParameter, amount: any) {
    const num = Number(this.getTargetValue(row, amount));
    if (num < 0 || num > 65535) {
      return 'port-target-bad';
    }
  }

  getIcon(row: ShiftableParameter) {
    if (row.desc.type === ParameterType.CLIENT_PORT) {
      return 'computer';
    } else if (row.desc.type === ParameterType.SERVER_PORT) {
      return 'dns';
    } else if (row.desc.type === ParameterType.URL) {
      return 'public';
    }
  }
}

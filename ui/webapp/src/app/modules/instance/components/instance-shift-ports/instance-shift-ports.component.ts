import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { ParameterConfiguration, ParameterDescriptor } from 'src/app/models/gen.dtos';

export interface ShiftableParameter {
  applicationUid: string;
  applicationName: string;
  cfg: ParameterConfiguration;
  desc: ParameterDescriptor;
  client: boolean;
  selected: boolean;
}

@Component({
  selector: 'app-instance-shift-ports',
  templateUrl: './instance-shift-ports.component.html',
  styleUrls: ['./instance-shift-ports.component.css']
})
export class InstanceShiftPortsComponent implements OnInit {

  public columnsToDisplay = ['status', 'type', 'application', 'name', 'current', 'target'];
  public dataSource: MatTableDataSource<ShiftableParameter>;

  constructor(@Inject(MAT_DIALOG_DATA) private params: ShiftableParameter[]) { }

  ngOnInit(): void {
    this.dataSource = new MatTableDataSource(this.params);
  }

  isAtLeastOneSelected() {
    const numSelected = this.params.filter(lp => lp.selected).length;
    return numSelected > 0;
  }

  isAllSelected() {
    const numSelected = this.params.filter(lp => lp.selected).length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  toggleSelection() {
    const targetState = this.isAllSelected() ? false : true;
    this.params.forEach(lp => (lp.selected = targetState));
  }

  toggleRowSelection(row: ShiftableParameter) {
    row.selected = !row.selected;
  }

  getTargetValue(row: ShiftableParameter, amount: any) {
    return (Number(row.cfg.value) + Number(amount)).toString();
  }

  getTargetClass(row: ShiftableParameter, amount: any) {
    const num = Number(this.getTargetValue(row, amount));
    if(num < 0 || num > 65535) {
      return 'port-target-bad';
    }
  }

}

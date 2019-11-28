import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { cloneDeep, isEqual } from 'lodash';
import { LinkedParameter } from '../../../../models/application.model';

@Component({
  selector: 'app-application-edit-optional',
  templateUrl: './application-edit-optional.component.html',
  styleUrls: ['./application-edit-optional.component.css'],
})
export class ApplicationEditOptionalComponent implements OnInit {
  public columnsToDisplay = ['status', 'name', 'description'];
  public dataSource: MatTableDataSource<LinkedParameter>;

  public clonedParameters: LinkedParameter[];
  public isDirty = false;

  constructor(
    @Inject(MAT_DIALOG_DATA) public parameters: LinkedParameter[],
    public dialogRef: MatDialogRef<ApplicationEditOptionalComponent>,
  ) {}

  ngOnInit() {
    this.clonedParameters = cloneDeep(this.parameters);
    this.dataSource = new MatTableDataSource<LinkedParameter>(this.parameters);
    this.dataSource.filterPredicate = (p, f) => {
      if (p.desc.name.toLowerCase().indexOf(f.toLowerCase()) !== -1) {
        return true;
      }

      if (p.desc.longDescription.toLowerCase().indexOf(f.toLowerCase()) !== -1) {
        return true;
      }

      return false;
    };
  }

  isAllSelected() {
    const numSelected = this.parameters.filter(lp => lp.rendered).length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  isAtLeastOneSelected() {
    const numSelected = this.parameters.filter(lp => lp.rendered).length;
    return numSelected > 0;
  }

  toggleSelection() {
    const targetState = this.isAllSelected() ? false : true;
    this.parameters.forEach(lp => (lp.rendered = targetState));
    this.updateDirtyState();
  }

  toggleRowSelection(row: LinkedParameter) {
    row.rendered = !row.rendered;
    this.updateDirtyState();
  }

  applyFilter(filterValue: string) {
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }

  updateDirtyState() {
    this.isDirty = !isEqual(this.parameters, this.clonedParameters);
  }
}

import { Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatInput } from '@angular/material/input';
import { MatTableDataSource } from '@angular/material/table';
import { cloneDeep, isEqual } from 'lodash-es';
import { LinkedParameter } from '../../../../models/application.model';

export interface EditOptionalData {
  filter: string;
  parameters: LinkedParameter[];
}

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

  @ViewChild('searchField', { static: true }) searchField: ElementRef<MatInput>;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: EditOptionalData,
    public dialogRef: MatDialogRef<ApplicationEditOptionalComponent>
  ) {}

  ngOnInit() {
    this.clonedParameters = cloneDeep(this.data.parameters);
    this.dataSource = new MatTableDataSource<LinkedParameter>(this.data.parameters);
    this.dataSource.filterPredicate = (p, f) => {
      const name = p.desc.name ? p.desc.name.toLowerCase() : '';
      const desc = p.desc.longDescription ? p.desc.longDescription.toLowerCase() : '';
      const filter = f.toLowerCase();
      return name.indexOf(filter) !== -1 || desc.indexOf(filter) !== -1;
    };

    if (this.data.filter) {
      this.searchField.nativeElement.value = this.data.filter;
      this.applyFilter(this.data.filter);
    }
  }

  isAllSelected() {
    const numSelected = this.data.parameters.filter((lp) => lp.rendered).length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  isAtLeastOneSelected() {
    const numSelected = this.data.parameters.filter((lp) => lp.rendered).length;
    return numSelected > 0;
  }

  toggleSelection() {
    const targetState = this.isAllSelected() ? false : true;
    this.data.parameters.forEach((lp) => (lp.rendered = targetState));
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
    this.isDirty = !isEqual(this.data.parameters, this.clonedParameters);
  }
}

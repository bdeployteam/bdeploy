import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, MatTableDataSource } from '@angular/material';
import { CleanupAction, CleanupGroup } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-master-cleanup-group',
  templateUrl: './master-cleanup-group.component.html',
  styleUrls: ['./master-cleanup-group.component.css']
})
export class MasterCleanupGroupComponent implements OnInit {

  @Input()
  public group: CleanupGroup;

  public dataSource: MatTableDataSource<CleanupAction>;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  columns = ['description', 'type', 'what'];

  constructor() { }

  ngOnInit() {
    this.dataSource = new MatTableDataSource(this.group.actions);
    this.dataSource.paginator = this.paginator;
  }

}

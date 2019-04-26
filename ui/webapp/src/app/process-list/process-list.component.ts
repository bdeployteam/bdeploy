import { Component, Inject, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, MatSort, MatTableDataSource, MAT_BOTTOM_SHEET_DATA } from '@angular/material';
import { ProcessDetailDto, ProcessStatusDto } from '../models/gen.dtos';

@Component({
  selector: 'app-process-list',
  templateUrl: './process-list.component.html',
  styleUrls: ['./process-list.component.css'],
})
export class ProcessListComponent implements OnInit {
  public readonly displayedColumns = ['pid', 'user', 'totalCpuDuration', 'command', 'arguments'];

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(MatSort)
  sort: MatSort;

  public dataSource = new MatTableDataSource<ProcessDetailDto>();
  public totalCpuDuration = 0;

  constructor(@Inject(MAT_BOTTOM_SHEET_DATA) public data: any) {
    const statusDto: ProcessStatusDto = data.statusDto;
    this.initDataSource(statusDto.processDetails);
  }

  ngOnInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  initDataSource(parent: ProcessDetailDto) {
    this.totalCpuDuration += parent.totalCpuDuration;
    this.dataSource.data.push(parent);
    parent.children.forEach(child => {
      this.initDataSource(child);
    });
  }
}

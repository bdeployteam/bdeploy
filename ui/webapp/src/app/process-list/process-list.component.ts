import { Component, Inject, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, MatSort, MatTable, MatTableDataSource, MAT_BOTTOM_SHEET_DATA } from '@angular/material';
import { ApplicationGroup } from '../models/application.model';
import { ApplicationConfiguration, OperatingSystem, ProcessDetailDto, ProcessStatusDto } from '../models/gen.dtos';

@Component({
  selector: 'app-process-list',
  templateUrl: './process-list.component.html',
  styleUrls: ['./process-list.component.css'],
})
export class ProcessListComponent implements OnInit {
  @ViewChild(MatTable)
  public table: MatTable<any>;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(MatSort)
  sort: MatSort;

  public isRunning = false;
  public statusDto: ProcessStatusDto;
  public appConfig: ApplicationConfiguration;

  public displayedColumns: string[] = [];
  public dataSource = new MatTableDataSource<ProcessDetailDto>();

  constructor(@Inject(MAT_BOTTOM_SHEET_DATA) public data: any) {
    this.appConfig = data.appConfig;
    this.displayedColumns = this.initTableColumns();
    this.setStatus(data.statusDto);
  }

  ngOnInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  setStatus(statusDto: ProcessStatusDto) {
    this.statusDto = statusDto;
    this.isRunning = this.statusDto.processDetails ? true : false;
    this.dataSource.data.splice(0, this.dataSource.data.length);
    if (this.isRunning) {
      this.initDataSource(statusDto.processDetails);
    }
    if (this.table) {
      this.sort.sortChange.emit();
      this.table.renderRows();
    }
  }

  initDataSource(parent: ProcessDetailDto) {
    this.dataSource.data.push(parent);
    parent.children.forEach(child => {
      this.initDataSource(child);
    });
  }

  initTableColumns() {
    // On windows the arguments are always empty due to permissions
    // We cannot determine them for a running application
    const os = ApplicationGroup.getAppOs(this.appConfig.application);
    if (os === OperatingSystem.WINDOWS) {
      return ['pid', 'user', 'totalCpuDuration', 'command'];
    }
    return ['pid', 'user', 'totalCpuDuration', 'command', 'arguments'];
  }
}

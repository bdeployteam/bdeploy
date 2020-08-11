import { Component, Inject, OnInit, ViewChild } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA } from '@angular/material/bottom-sheet';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTable, MatTableDataSource } from '@angular/material/table';
import { ApplicationConfiguration, OperatingSystem, ProcessDetailDto, ProcessHandleDto } from '../../../../models/gen.dtos';
import { getAppOs } from '../../../shared/utils/manifest.utils';

@Component({
  selector: 'app-process-list',
  templateUrl: './process-list.component.html',
  styleUrls: ['./process-list.component.css'],
})
export class ProcessListComponent implements OnInit {
  @ViewChild(MatTable, { static: true })
  public table: MatTable<any>;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  public detailsDto: ProcessDetailDto;
  public appConfig: ApplicationConfiguration;

  public displayedColumns: string[] = [];
  public dataSource = new MatTableDataSource<ProcessHandleDto>();

  constructor(@Inject(MAT_BOTTOM_SHEET_DATA) public data: any) {
    this.appConfig = data.appConfig;
    this.displayedColumns = this.initTableColumns();
    this.setStatus(data.statusDto);
  }

  ngOnInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  setStatus(detailsDto: ProcessDetailDto) {
    this.detailsDto = detailsDto;
    this.dataSource.data.splice(0, this.dataSource.data.length);
    this.initDataSource(detailsDto.handle);
    if (this.table) {
      this.sort.sortChange.emit();
      this.table.renderRows();
    }
    this.dataSource.paginator = this.paginator;
  }

  initDataSource(parent: ProcessHandleDto) {
    this.dataSource.data.push(parent);
    parent.children.forEach(child => {
      this.initDataSource(child);
    });
  }

  initTableColumns() {
    // On windows the arguments are always empty due to permissions
    // We cannot determine them for a running application
    const os = getAppOs(this.appConfig.application);
    if (os === OperatingSystem.WINDOWS) {
      return ['pid', 'user', 'totalCpuDuration', 'command'];
    }
    return ['pid', 'user', 'totalCpuDuration', 'command', 'arguments'];
  }
}

import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';
import { ReportDescriptor } from 'src/app/models/gen.dtos';

@Injectable({
  providedIn: 'root',
})
export class ReportsColumnsService {
  private readonly reportNameColumn: BdDataColumn<ReportDescriptor> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    sortCard: true,
  };

  private readonly reportDescriptionColumn: BdDataColumn<ReportDescriptor> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.description,
    sortCard: true,
  };

  private readonly reportTypeColumn: BdDataColumn<ReportDescriptor> = {
    id: 'type',
    name: 'Report Type',
    hint: BdDataColumnTypeHint.TYPE,
    data: (r) => r.type,
    sortCard: true,
  };

  public readonly defaultReportColumns: BdDataColumn<ReportDescriptor>[] = [
    this.reportNameColumn,
    this.reportDescriptionColumn,
    this.reportTypeColumn,
  ];
}

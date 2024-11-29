import { formatDate } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { combineLatest, Subscription } from 'rxjs';
import { BdDataColumn, BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { ReportDescriptor, ReportResponseDto } from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';

@Component({
    selector: 'app-report',
    templateUrl: './report.component.html',
    standalone: false
})
export class ReportComponent implements OnInit, OnDestroy {
  private readonly download = inject(DownloadService);
  private readonly router = inject(Router);
  protected readonly reports = inject(ReportsService);

  protected report: ReportDescriptor;
  protected columns: BdDataColumn<{ [index: string]: string }>[] = [];
  protected generated: ReportResponseDto;
  protected definitions: BdDataGroupingDefinition<{ [index: string]: string }>[] = [];
  protected grouping: BdDataGrouping<{ [index: string]: string }>[] = [];
  protected header: string;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([this.reports.current$, this.reports.generatedReport$]).subscribe(
      ([desc, generated]) => {
        this.report = desc;
        this.generated = generated;
        this.header = `Report "${desc.name}"`;
        this.columns = desc.columns
          .filter((col) => col.main)
          .map((col) => ({
            id: col.key,
            name: col.name,
            data: (r) => r[col.key],
          }));
        this.definitions = desc.columns
          .filter((col) => col.main)
          .map((col) => ({
            name: col.name,
            associatedColumn: col.key,
            group: (r) => r[col.key],
          }));
      },
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected showRowDetails(row: { [index: string]: string }) {
    this.reports.selectedRow$.next(row);
    this.router.navigate(['', { outlets: { panel: ['panels', 'reports', 'row-details'] } }]);
  }

  protected exportCsv() {
    const columns = this.report.columns;
    const csvData = [
      columns.map((col) => col.name).join(','),
      ...this.generated.rows.map((row) => columns.map((col) => this.escapeCsvValue(row[col.key])).join(',')),
    ].join('\n');
    const filename = `${this.report.name} - ${formatDate(this.generated.generatedAt, 'dd.MM.yyyy HH:mm:ss', 'en-US')}.csv`;
    const blob = new Blob([csvData], { type: 'text/csv' });
    this.download.downloadBlob(filename, blob);
  }

  private escapeCsvValue(value: string): string {
    if (value === null || value === undefined) {
      return '';
    }
    if (value.includes(',') || value.includes('"')) {
      value = `"${value.replace(/"/g, '""')}"`;
    }
    return value;
  }
}

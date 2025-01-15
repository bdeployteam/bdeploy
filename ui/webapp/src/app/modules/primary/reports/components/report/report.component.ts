import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { BdDataColumn, BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { ReportColumnDescriptor, ReportDescriptor, ReportResponseDto } from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';

interface GeneratedReportRow {
  [index: string]: string;
}

@Component({
  selector: 'app-report',
  templateUrl: './report.component.html',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportComponent implements OnInit, OnDestroy, BdSearchable {
  private readonly cd = inject(ChangeDetectorRef);
  private readonly download = inject(DownloadService);
  private readonly router = inject(Router);
  private readonly search = inject(SearchService);
  private readonly reports = inject(ReportsService);
  private readonly search$ = new BehaviorSubject<string>(null);

  protected report: ReportDescriptor;
  protected columns: BdDataColumn<GeneratedReportRow>[] = [];
  protected rows: GeneratedReportRow[] = [];
  protected generated: ReportResponseDto;
  protected definitions: BdDataGroupingDefinition<GeneratedReportRow>[] = [];
  protected grouping: BdDataGrouping<GeneratedReportRow>[] = [];
  protected header: string;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([this.reports.current$, this.reports.generatedReport$, this.search$]).subscribe(
      ([desc, generated, search]) => {
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
        this.rows = this.calculateRows(desc.columns, generated?.rows || [], search);
        this.cd.markForCheck();
      },
    );
    this.subscription.add(this.search.register(this));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  bdOnSearch(value: string): void {
    this.search$.next(value);
  }

  private calculateRows(
    columns: ReportColumnDescriptor[],
    rows: GeneratedReportRow[],
    search: string,
  ): GeneratedReportRow[] {
    if (!search) {
      return rows;
    }
    return rows.filter((row) =>
      columns.some((col) => row[col.key]?.length && row[col.key].toUpperCase().indexOf(search.toUpperCase()) !== -1),
    );
  }

  protected showRowDetails(row: GeneratedReportRow) {
    this.reports.selectedRow$.next(row);
    this.router.navigate(['', { outlets: { panel: ['panels', 'reports', 'row-details'] } }]);
  }

  protected exportCsv() {
    const columns = this.report.columns;
    const columnNames = columns.map((col) => col.name);
    const rows = this.rows.map((row) => columns.map((col) => row[col.key]));
    const filename = `${this.report.name} - ${this.generated.generatedAt}.csv`;
    this.download.downloadCsv(filename, columnNames, rows);
  }
}

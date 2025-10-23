import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { BdDataColumn, BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { ReportColumnDescriptor, ReportDescriptor, ReportResponseDto } from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDataGroupingComponent } from '../../../../core/components/bd-data-grouping/bd-data-grouping.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';

@Component({
  selector: 'app-report',
  templateUrl: './report.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdButtonComponent,
    RouterLink,
    BdDataGroupingComponent,
    BdDialogContentComponent,
    BdDataTableComponent,
  ],
})
export class ReportComponent implements OnInit, OnDestroy, BdSearchable {
  private readonly cd = inject(ChangeDetectorRef);
  private readonly download = inject(DownloadService);
  private readonly router = inject(Router);
  private readonly search = inject(SearchService);
  private readonly reports = inject(ReportsService);
  private readonly search$ = new BehaviorSubject<string>(null);

  protected report: ReportDescriptor;
  protected columns: BdDataColumn<Record<string, string>, string>[] = [];
  protected rows: Record<string, string>[] = [];
  protected generated: ReportResponseDto;
  protected definitions: BdDataGroupingDefinition<Record<string, string>>[] = [];
  protected grouping: BdDataGrouping<Record<string, string>>[] = [];
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
      }
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
    rows: Record<string, string>[],
    search: string
  ): Record<string, string>[] {
    if (!search) {
      return rows;
    }
    return rows.filter((row) =>
      columns.some((col) => row[col.key]?.length && row[col.key].toUpperCase().includes(search.toUpperCase()))
    );
  }

  protected showRowDetails(row: Record<string, string>) {
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

import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';

@Component({
  selector: 'app-report',
  templateUrl: './report.component.html',
})
export class ReportComponent implements OnInit, OnDestroy {
  protected readonly reports = inject(ReportsService);

  protected columns: BdDataColumn<any>[];

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.reports.current$.subscribe((desc) => {
      if (!desc) {
        this.columns = null;
        return;
      }
      this.columns = desc.columns.map((col) => ({
        id: col.key,
        name: col.name,
        data: (r) => r[col.key],
      }));
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}

import { Component, inject } from '@angular/core';
import { map } from 'rxjs';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';

@Component({
    selector: 'app-report-row-details',
    templateUrl: './report-row-details.component.html',
    standalone: false
})
export class ReportRowDetailsComponent {
  protected readonly reports = inject(ReportsService);
  protected readonly mainColumns$ = this.reports.current$.pipe(map((r) => r.columns.filter((c) => c.main)));
  protected readonly hiddenColumns$ = this.reports.current$.pipe(map((r) => r.columns.filter((c) => !c.main)));
}

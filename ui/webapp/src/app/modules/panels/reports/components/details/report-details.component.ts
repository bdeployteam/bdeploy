import { Component, inject } from '@angular/core';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';

@Component({
  selector: 'app-report-details',
  templateUrl: './report-details.component.html',
  standalone: false,
})
export class ReportDetailsComponent {
  protected readonly reports = inject(ReportsService);
}

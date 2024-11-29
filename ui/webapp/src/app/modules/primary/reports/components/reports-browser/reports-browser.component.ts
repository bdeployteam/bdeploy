import { Component, inject } from '@angular/core';
import { ReportDescriptor } from 'src/app/models/gen.dtos';
import { ReportsColumnsService } from '../../services/reports-columns.service';
import { ReportsService } from '../../services/reports.service';

@Component({
    selector: 'app-reports-browser',
    templateUrl: './reports-browser.component.html',
    standalone: false
})
export class ReportsBrowserComponent {
  protected readonly reports = inject(ReportsService);
  protected readonly columns = inject(ReportsColumnsService);

  protected isCardView = false;

  protected getRecordRoute = (r: ReportDescriptor) => [
    '',
    { outlets: { panel: ['panels', 'reports', r.type, 'form'] } },
  ];
}

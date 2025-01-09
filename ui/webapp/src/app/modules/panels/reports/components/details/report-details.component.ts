import { Component, inject } from '@angular/core';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-report-details',
    templateUrl: './report-details.component.html',
    imports: [
        BdDialogComponent,
        BdDialogToolbarComponent,
        BdDialogContentComponent,
        AsyncPipe,
    ],
})
export class ReportDetailsComponent {
  protected readonly reports = inject(ReportsService);
}

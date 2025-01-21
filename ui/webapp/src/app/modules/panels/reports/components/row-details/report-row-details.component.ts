import { Component, inject } from '@angular/core';
import { map } from 'rxjs';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatDivider } from '@angular/material/divider';
import { BdIdentifierComponent } from '../../../../core/components/bd-identifier/bd-identifier.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-report-row-details',
    templateUrl: './report-row-details.component.html',
    imports: [
        BdDialogComponent,
        BdDialogToolbarComponent,
        BdDialogContentComponent,
        MatDivider,
        BdIdentifierComponent,
        AsyncPipe,
    ],
})
export class ReportRowDetailsComponent {
  protected readonly reports = inject(ReportsService);
  protected readonly mainColumns$ = this.reports.current$.pipe(map((r) => r.columns.filter((c) => c.main)));
  protected readonly hiddenColumns$ = this.reports.current$.pipe(map((r) => r.columns.filter((c) => !c.main)));
}

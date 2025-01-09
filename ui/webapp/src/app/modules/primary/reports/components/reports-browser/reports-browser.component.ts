import { Component, inject } from '@angular/core';
import { ReportDescriptor } from 'src/app/models/gen.dtos';
import { ReportsColumnsService } from '../../services/reports-columns.service';
import { ReportsService } from '../../services/reports.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-reports-browser',
    templateUrl: './reports-browser.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, BdDialogContentComponent, BdDataDisplayComponent, AsyncPipe]
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

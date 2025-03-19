import { Component, ViewEncapsulation, inject } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { CleanupAction } from 'src/app/models/gen.dtos';
import { CleanupService } from '../../services/cleanup.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatTabGroup, MatTab, MatTabLabel, MatTabContent } from '@angular/material/tabs';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

const colType: BdDataColumn<CleanupAction, string> = {
  id: 'type',
  name: 'Action',
  data: (r) => r.type,
  width: '150px',
};

const colWhat: BdDataColumn<CleanupAction, string> = {
  id: 'what',
  name: 'Target',
  data: (r) => r.what,
};

const colDesc: BdDataColumn<CleanupAction, string> = {
  id: 'description',
  name: 'Description',
  data: (r) => r.description,
};

@Component({
    selector: 'app-master-cleanup',
    templateUrl: './master-cleanup.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, BdDialogContentComponent, MatTabGroup, MatTab, MatTabLabel, MatTabContent, BdDataTableComponent, BdNoDataComponent, AsyncPipe]
})
export class MasterCleanupComponent {
  protected readonly cleanup = inject(CleanupService);

  protected readonly columns: BdDataColumn<CleanupAction, unknown>[] = [colType, colWhat, colDesc];
}

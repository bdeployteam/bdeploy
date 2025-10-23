import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { combineLatest, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { Edit, InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

interface InstanceEditRow {
  edit: Edit;
  isUndo: boolean;
  isCurrent: boolean;
}

const descColumn: BdDataColumn<InstanceEditRow, string> = {
  id: 'desc',
  name: 'Modification',
  data: (r) => r.edit.description,
  classes: (r) => (r.isUndo ? [] : ['bd-secondary-text']),
};

const currentColumn: BdDataColumn<InstanceEditRow, string> = {
  id: 'current',
  name: 'Cur.',
  data: (r) => (r.isCurrent ? 'arrow_back' : null),
  component: BdDataIconCellComponent,
  width: '24px',
};

const redoColumn: BdDataColumn<InstanceEditRow, string> = {
  id: 'redo',
  name: 'Redo',
  data: (r) => (r.isUndo ? null : 'redo'),
  component: BdDataIconCellComponent,
  classes: (r) => (r.isUndo ? [] : ['bd-secondary-text']),
  width: '24px',
};

@Component({
  selector: 'app-local-changes',
  templateUrl: './local-changes.component.html',
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    BdDataTableComponent,
    BdPanelButtonComponent,
    BdButtonComponent,
    AsyncPipe,
  ],
})
export class LocalChangesComponent implements OnInit, OnDestroy {
  protected readonly edit = inject(InstanceEditService);

  protected records: InstanceEditRow[] = [];
  protected readonly columns: BdDataColumn<InstanceEditRow, unknown>[] = [descColumn, currentColumn, redoColumn];

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([this.edit.undo$, this.edit.redo$]).subscribe(() => {
      const recs: InstanceEditRow[] = [];
      // fetch the whole list and build data for the table.
      for (const undo of this.edit.undos) {
        recs.push({ edit: undo, isUndo: true, isCurrent: false });
      }

      if (this.edit.undos.length) {
        recs.at(-1).isCurrent = true;
      }

      for (const redo of [...this.edit.redos].reverse()) {
        recs.push({ edit: redo, isUndo: false, isCurrent: false });
      }

      this.records = recs;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected doDiscard() {
    this.dialog
      .confirm(
        `Discard unsaved changes?`,
        `Discard <strong>${this.edit.undos.length}</strong> unsaved local changes? This cannot be undone.`,
        'undo'
      )
      .subscribe((confirm) => {
        if (confirm) {
          this.edit.reset();
        }
      });
  }
}

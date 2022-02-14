import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { combineLatest, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import {
  Edit,
  InstanceEditService,
} from 'src/app/modules/primary/instances/services/instance-edit.service';

interface InstanceEditRow {
  edit: Edit;
  isUndo: boolean;
  isCurrent: boolean;
}

const descColumn: BdDataColumn<InstanceEditRow> = {
  id: 'desc',
  name: 'Modification',
  data: (r) => r.edit.description,
  classes: (r) => (r.isUndo ? [] : ['bd-secondary-text']),
};

const currentColumn: BdDataColumn<InstanceEditRow> = {
  id: 'current',
  name: 'Cur.',
  data: (r) => (r.isCurrent ? 'arrow_back' : null),
  component: BdDataIconCellComponent,
  width: '24px',
};

const redoColumn: BdDataColumn<InstanceEditRow> = {
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
})
export class LocalChangesComponent implements OnInit, OnDestroy {
  /* template */ records: InstanceEditRow[] = [];
  /* template */ columns: BdDataColumn<InstanceEditRow>[] = [
    descColumn,
    currentColumn,
    redoColumn,
  ];

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(public edit: InstanceEditService) {}

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.edit.undo$,
      this.edit.redo$,
    ]).subscribe(() => {
      const recs: InstanceEditRow[] = [];
      // fetch the whole list and build data for the table.
      for (const undo of this.edit.undos) {
        recs.push({ edit: undo, isUndo: true, isCurrent: false });
      }

      if (this.edit.undos.length) {
        recs[recs.length - 1].isCurrent = true;
      }

      for (const redo of [...this.edit.redos].reverse()) {
        recs.push({ edit: redo, isUndo: false, isCurrent: false });
      }

      this.records = recs;
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ doDiscard() {
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

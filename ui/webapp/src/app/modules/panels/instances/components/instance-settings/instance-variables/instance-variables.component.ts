import { Component, OnDestroy, TemplateRef, ViewChild } from '@angular/core';
import { Observable, of, Subscription, tap } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

class ConfigVariable {
  name: string;
  value: string;
  description: string;
}

const colName: BdDataColumn<ConfigVariable> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  width: '300px',
};

const colValue: BdDataColumn<ConfigVariable> = {
  id: 'value',
  name: 'Value',
  data: (r) => r.value,
};

const colDesc: BdDataColumn<ConfigVariable> = {
  id: 'description',
  name: 'Description',
  data: (r) => r.description,
};

@Component({
  selector: 'app-instance-variables',
  templateUrl: './instance-variables.component.html',
})
export class InstanceVariablesComponent implements DirtyableDialog, OnDestroy {
  private readonly colEdit: BdDataColumn<ConfigVariable> = {
    id: 'edit',
    name: 'Edit',
    data: () => 'Edit',
    width: '40px',
    icon: () => 'edit',
    action: (r) => {
      this.onEdit(r);
    },
  };

  private readonly colDelete: BdDataColumn<ConfigVariable> = {
    id: 'delete',
    name: 'Del.',
    data: () => 'Delete',
    width: '40px',
    icon: () => 'delete',
    action: (r) => {
      this.onDelete(r);
    },
  };

  /* template */ records: ConfigVariable[] = [];
  /* template */ columns: BdDataColumn<ConfigVariable>[] = [
    colName,
    colValue,
    colDesc,
    this.colEdit,
    this.colDelete,
  ];

  /* template */ newId: string;
  /* template */ newValue: string;
  /* template */ newDescription: string;
  /* template */ newUsedIds: string[] = [];

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) tb: BdDialogToolbarComponent;

  @ViewChild('editTemplate') editTemplate: TemplateRef<any>;

  constructor(private edit: InstanceEditService, areas: NavAreasService) {
    this.subscription = this.edit.state$.subscribe((c) => {
      if (c) {
        this.buildVariables();
      }
    });

    this.subscription.add(areas.registerDirtyable(this, 'panel'));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private buildVariables() {
    if (
      !this.edit.state$.value?.config?.config?.instanceVariables ||
      !Object.keys(this.edit.state$.value.config.config.instanceVariables)
        .length
    ) {
      this.records = [];
      return;
    }

    this.records = Object.keys(
      this.edit.state$.value.config.config.instanceVariables
    ).map((k) => ({
      name: k,
      value: this.edit.state$.value.config.config.instanceVariables[k]?.value,
      description:
        this.edit.state$.value.config.config.instanceVariables[k]?.description,
    }));
  }

  public isDirty(): boolean {
    return this.edit.hasPendingChanges();
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<any> {
    return of(true).pipe(
      tap(() => {
        this.edit.conceal('Change Instance Variables');
      })
    );
  }

  /* template */ onAdd(templ: TemplateRef<any>) {
    this.newUsedIds = this.records.map((r) => r.name);
    this.dialog
      .message({
        header: 'Add Variable',
        icon: 'add',
        template: templ,
        validation: () => !!this.newId?.length,
        actions: [ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        const id = this.newId;
        const value = this.newValue;
        const desc = this.newDescription;
        this.newId = this.newValue = this.newDescription = null;

        if (!r) {
          return;
        }

        if (!this.edit.state$.value.config.config.instanceVariables) {
          this.edit.state$.value.config.config.instanceVariables = {};
        }

        this.edit.state$.value.config.config.instanceVariables[id] = {
          value: value,
          description: desc,
        };
        this.buildVariables();
      });
  }

  /* template */ onEdit(variable: ConfigVariable) {
    this.newId = variable.name;
    this.newValue = variable.value;
    this.newDescription = variable.description;
    this.dialog
      .message({
        header: 'Edit Variable',
        icon: 'edit',
        template: this.editTemplate,
        actions: [ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        const id = this.newId;
        const value = this.newValue;
        const desc = this.newDescription;
        this.newId = this.newValue = this.newDescription = null;

        if (!r) {
          return;
        }

        this.edit.state$.value.config.config.instanceVariables[id] = {
          value: value,
          description: desc,
        };
        this.buildVariables();
      });
  }

  private onDelete(r: ConfigVariable) {
    delete this.edit.state$.value.config.config.instanceVariables[r.name];
    this.buildVariables();
  }
}

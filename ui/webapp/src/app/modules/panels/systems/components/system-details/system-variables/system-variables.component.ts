import { Component, OnDestroy, TemplateRef, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import {
  BehaviorSubject,
  combineLatest,
  finalize,
  Observable,
  of,
  Subscription,
  switchMap,
} from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import {
  InstanceDto,
  ParameterType,
  SystemConfigurationDto,
  VariableValue,
} from 'src/app/models/gen.dtos';
import { ContentCompletion } from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  buildCompletionPrefixes,
  buildCompletions,
} from 'src/app/modules/core/utils/completion.utils';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { getPreRenderable } from 'src/app/modules/core/utils/linked-values.utils';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { SystemsEditService } from '../../../services/systems-edit.service';

const MAGIC_ABORT = 'abort_save';

class ConfigVariable {
  name: string;
  value: VariableValue;
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
  data: (r) => getPreRenderable(r.value.value),
};

const colDesc: BdDataColumn<ConfigVariable> = {
  id: 'description',
  name: 'Description',
  data: (r) => r.value.description,
};

@Component({
  selector: 'app-system-variables',
  templateUrl: './system-variables.component.html',
})
export class SystemVariablesComponent implements DirtyableDialog, OnDestroy {
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

  private orig: SystemConfigurationDto;
  /* template */ system: SystemConfigurationDto;
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ records: ConfigVariable[] = [];
  /* template */ columns: BdDataColumn<ConfigVariable>[] = [
    colName,
    colValue,
    colDesc,
    this.colEdit,
    this.colDelete,
  ];

  /* template */ newId: string;
  /* template */ newValue: VariableValue;
  /* template */ newUsedIds: string[] = [];

  /* template */ typeValues: ParameterType[] = Object.values(ParameterType);

  /* template */ completionPrefixes = buildCompletionPrefixes();
  /* template */ completions: ContentCompletion[];

  private subscription: Subscription;
  private instancesUsing: InstanceDto[];

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) tb: BdDialogToolbarComponent;

  @ViewChild('addForm', { static: false }) addForm: NgForm;
  @ViewChild('editForm', { static: false }) editForm: NgForm;

  @ViewChild('editTemplate') editTemplate: TemplateRef<any>;

  constructor(
    private edit: SystemsEditService,
    instances: InstancesService,
    areas: NavAreasService
  ) {
    this.subscription = this.edit.current$.subscribe((c) => {
      if (!c) {
        return;
      }

      this.system = cloneDeep(c);
      this.orig = cloneDeep(c);

      this.completions = buildCompletions(
        this.completionPrefixes,
        null,
        this.system.config,
        null,
        null
      );

      this.buildVariables();
    });

    this.subscription.add(
      combineLatest([edit.current$, instances.instances$]).subscribe(
        ([c, i]) => {
          if (!c || !i) {
            this.instancesUsing = [];
            return;
          }

          this.instancesUsing = i.filter(
            (i) => i.instanceConfiguration?.system?.name === c.key?.name
          );
        }
      )
    );

    this.subscription.add(areas.registerDirtyable(this, 'panel'));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private buildVariables() {
    if (!this.system?.config) {
      return;
    }
    this.records = Object.keys(this.system.config.systemVariables).map((k) => ({
      name: k,
      value: this.system.config.systemVariables[k],
    }));
  }

  public isDirty(): boolean {
    return isDirty(this.system, this.orig);
  }

  public doSave(): Observable<any> {
    this.saving$.next(true);

    const save = this.edit
      .update(this.system)
      .pipe(finalize(() => this.saving$.next(false)));

    if (this.instancesUsing?.length) {
      return this.dialog
        .confirm(
          `Saving ${this.instancesUsing.length} instances`,
          `Affected <strong>${this.instancesUsing.length}</strong> will be updated with the new system version. This needs to be installed and activated on all affected instances.`,
          'warning'
        )
        .pipe(
          switchMap((b) => {
            if (b) {
              return save;
            } else {
              return of(MAGIC_ABORT).pipe(
                finalize(() => this.saving$.next(false))
              );
            }
          })
        );
    } else {
      // no confirmation required
      return save;
    }
  }

  /* template */ onSave(): void {
    this.doSave().subscribe((x) => {
      if (x != MAGIC_ABORT) {
        this.system = this.orig = null;
        this.tb.closePanel();
      }
    });
  }

  /* template */ onAdd(templ: TemplateRef<any>) {
    this.newUsedIds = this.records.map((r) => r.name);
    this.newValue = {
      value: { value: '', linkExpression: null },
      description: '',
      type: ParameterType.STRING,
      customEditor: null,
    };
    this.dialog
      .message({
        header: 'Add Variable',
        icon: 'add',
        template: templ,
        validation: () => !!this.addForm && this.addForm.valid,
        actions: [ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        const id = this.newId;
        const value = this.newValue;
        this.newId = this.newValue = null;

        if (!r) {
          return;
        }

        this.system.config.systemVariables[id] = value;
        this.buildVariables();
      });
  }

  /* template */ onEdit(variable: ConfigVariable) {
    this.newId = variable.name;
    this.newValue = cloneDeep(variable.value);
    this.dialog
      .message({
        header: 'Edit Variable',
        icon: 'edit',
        template: this.editTemplate,
        validation: () => !!this.editForm && this.editForm.valid,
        actions: [ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        const id = this.newId;
        const value = this.newValue;
        this.newId = this.newValue = null;

        if (!r) {
          return;
        }

        this.system.config.systemVariables[id] = value;
        this.buildVariables();
      });
  }

  private onDelete(r: ConfigVariable) {
    delete this.system.config.systemVariables[r.name];
    this.buildVariables();
  }
}

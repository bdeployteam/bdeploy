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
import { getMaskedPreRenderable } from 'src/app/modules/core/utils/linked-values.utils';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { SystemsEditService } from '../../../services/systems-edit.service';
import { VariableConfiguration } from './../../../../../../models/gen.dtos';

const MAGIC_ABORT = 'abort_save';

class ConfigVariable {
  name: string;
  value: VariableConfiguration;
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
  data: (r) => getMaskedPreRenderable(r.value.value, r.value.type),
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

  /* template */ newValue: VariableConfiguration;
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
    if (!this.system?.config?.systemVariables?.length) {
      this.records = [];
      return;
    }
    this.records = this.system.config.systemVariables.map((v) => ({
      name: v.id,
      value: v,
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
      id: '',
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
        const value = this.newValue;
        this.newValue = null;

        if (!r) {
          return;
        }

        this.system.config.systemVariables.push(value);
        this.system.config.systemVariables.sort((a, b) =>
          a.id.localeCompare(b.id)
        );
        this.buildVariables();
      });
  }

  /* template */ onEdit(variable: ConfigVariable) {
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
        const value = this.newValue;
        this.newValue = null;

        if (!r) {
          return;
        }

        this.system.config.systemVariables.splice(
          this.system.config.systemVariables.findIndex(
            (x) => x.id === value.id
          ),
          1,
          value
        );
        this.buildVariables();
      });
  }

  /* template */ onTypeChange(value: ParameterType) {
    // check if we need to clear the value in case we switch from password to *something*.
    if (
      this.newValue.type !== value &&
      this.newValue.type === ParameterType.PASSWORD &&
      !this.newValue.value.linkExpression
    ) {
      // type changed, it is not an expression and previously was password. clear the value.
      this.newValue.value.value = '';
    }

    this.newValue.type = value;
  }

  private onDelete(r: ConfigVariable) {
    this.system.config.systemVariables.splice(
      this.system.config.systemVariables.findIndex((x) => x.id === r.name),
      1
    );
    this.buildVariables();
  }
}

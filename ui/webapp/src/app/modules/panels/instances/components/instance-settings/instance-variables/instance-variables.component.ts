import { Component, OnDestroy, TemplateRef, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { combineLatest, Observable, of, Subscription, tap } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import {
  ApplicationDto,
  InstanceConfigurationDto,
  ParameterType,
  SystemConfiguration,
  VariableConfiguration,
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
import { PluginService } from 'src/app/modules/core/services/plugin.service';
import {
  buildCompletionPrefixes,
  buildCompletions,
} from 'src/app/modules/core/utils/completion.utils';
import { getMaskedPreRenderable } from 'src/app/modules/core/utils/linked-values.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

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

  /* template */ newValue: VariableConfiguration;
  /* template */ newUsedIds: string[] = [];

  /* template */ instance: InstanceConfigurationDto;
  /* template */ system: SystemConfiguration;
  /* template */ apps: ApplicationDto[];
  /* template */ typeValues: ParameterType[] = Object.values(ParameterType);
  /* template */ editorValues: string[];

  /* template */ completionPrefixes = buildCompletionPrefixes();
  /* template */ completions: ContentCompletion[];

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) tb: BdDialogToolbarComponent;

  @ViewChild('editTemplate') editTemplate: TemplateRef<any>;

  @ViewChild('addForm', { static: false }) addForm: NgForm;
  @ViewChild('editForm', { static: false }) editForm: NgForm;

  constructor(
    public edit: InstanceEditService,
    public groups: GroupsService,
    systems: SystemsService,
    areas: NavAreasService,
    plugins: PluginService
  ) {
    this.subscription = combineLatest([
      this.edit.state$,
      this.edit.stateApplications$,
      systems.systems$,
    ]).subscribe(([instance, apps, systems]) => {
      if (instance?.config) {
        this.buildVariables(instance.config);

        this.instance = instance.config;
        this.apps = apps;

        if (instance?.config?.config?.system && systems?.length) {
          this.system = systems.find(
            (s) => s.key.name === instance.config.config.system.name
          )?.config;
        }

        this.completions = buildCompletions(
          this.completionPrefixes,
          this.instance,
          this.system,
          null,
          this.apps
        );

        plugins
          .getAvailableEditorTypes(
            groups.current$?.value?.name,
            instance.config.config.product
          )
          .subscribe((editors) => {
            this.editorValues = editors;
          });
      }
    });

    this.subscription.add(areas.registerDirtyable(this, 'panel'));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private buildVariables(config: InstanceConfigurationDto) {
    if (!config?.config?.instanceVariables?.length) {
      this.records = [];
      return;
    }

    this.records = config.config.instanceVariables.map((v) => ({
      name: v.id,
      value: v,
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
    this.newValue = {
      id: '',
      type: ParameterType.STRING,
      customEditor: null,
      value: { value: '', linkExpression: null },
      description: '',
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

        const instance = this.edit.state$.value.config.config;
        if (!instance.instanceVariables) {
          instance.instanceVariables = [];
        }

        instance.instanceVariables.push(value);
        instance.instanceVariables.sort((a, b) => a.id.localeCompare(b.id));
        this.buildVariables(this.edit.state$.value.config);
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

        const vars = this.edit.state$.value.config.config.instanceVariables;
        const index = vars.findIndex((x) => x.id === value.id);
        if (index) {
          vars.splice(index, 1, value);
          this.buildVariables(this.edit.state$.value.config);
        }
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
    const vars = this.edit.state$.value.config.config.instanceVariables;
    vars.splice(
      vars.findIndex((x) => x.id === r.name),
      1
    );
    this.buildVariables(this.edit.state$.value.config);
  }
}

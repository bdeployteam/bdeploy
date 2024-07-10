import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { cloneDeep } from 'lodash-es';
import { Observable, Subscription, combineLatest, interval, of, startWith, tap } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import {
  ApplicationDto,
  InstanceConfigurationDto,
  SystemConfiguration,
  VariableConfiguration,
  VariableType,
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
import { buildCompletionPrefixes, buildCompletions } from 'src/app/modules/core/utils/completion.utils';
import { getPreRenderable } from 'src/app/modules/core/utils/linked-values.utils';
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
  data: (r) => getPreRenderable(r.value.value, r.value.type),
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
export class InstanceVariablesComponent implements DirtyableDialog, OnInit, OnDestroy {
  private readonly systems = inject(SystemsService);
  private readonly areas = inject(NavAreasService);
  private readonly plugins = inject(PluginService);
  private readonly snackbar = inject(MatSnackBar);
  protected readonly edit = inject(InstanceEditService);
  protected readonly groups = inject(GroupsService);

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

  protected records: ConfigVariable[] = [];
  protected readonly columns: BdDataColumn<ConfigVariable>[] = [
    colName,
    colValue,
    colDesc,
    this.colEdit,
    this.colDelete,
  ];
  protected checked: ConfigVariable[];
  protected clipboardVars: ConfigVariable[];

  protected newValue: VariableConfiguration;
  protected newUsedIds: string[] = [];

  protected instance: InstanceConfigurationDto;
  protected system: SystemConfiguration;
  protected apps: ApplicationDto[];
  protected typeValues: VariableType[] = Object.values(VariableType);
  protected editorValues: string[];

  protected completionPrefixes = buildCompletionPrefixes();
  protected completions: ContentCompletion[];

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) tb: BdDialogToolbarComponent;

  @ViewChild('editTemplate') editTemplate: TemplateRef<unknown>;

  @ViewChild('addForm', { static: false }) addForm: NgForm;
  @ViewChild('editForm', { static: false }) editForm: NgForm;

  ngOnInit() {
    this.subscription = combineLatest([
      this.edit.state$,
      this.edit.stateApplications$,
      this.systems.systems$,
    ]).subscribe(([instance, apps, systems]) => {
      if (instance?.config) {
        this.buildVariables(instance.config);

        this.instance = instance.config;
        this.apps = apps;

        if (instance?.config?.config?.system && systems?.length) {
          this.system = systems.find((s) => s.key.name === instance.config.config.system.name)?.config;
        }

        this.completions = buildCompletions(this.completionPrefixes, this.instance, this.system, null, this.apps);

        this.plugins
          .getAvailableEditorTypes(this.groups.current$?.value?.name, instance.config.config.product)
          .subscribe((editors) => {
            this.editorValues = editors;
          });
      }
    });

    this.subscription.add(this.areas.registerDirtyable(this, 'panel'));

    this.subscription.add(
      interval(1000)
        .pipe(startWith(null))
        .subscribe(() => this.readFromClipboard()),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
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

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<unknown> {
    return of(true).pipe(
      tap(() => {
        this.edit.conceal('Change Instance Variables');
      }),
    );
  }

  protected doCopy() {
    const json = JSON.stringify(this.checked, null, '\t');

    navigator.clipboard.writeText(json).then(
      () =>
        this.snackbar.open('Copied to clipboard successfully', null, {
          duration: 1000,
        }),
      () =>
        this.snackbar.open('Unable to write to clipboard.', null, {
          duration: 1000,
        }),
    );
  }

  protected doPaste() {
    if (!this.clipboardVars?.length) {
      this.snackbar.open('Unable to read from clipboard.', null, {
        duration: 1000,
      });
      return;
    }
    const newVars: VariableConfiguration[] = [];
    const existingVars: VariableConfiguration[] = [];
    const instance = this.edit.state$.value.config.config;
    if (!instance.instanceVariables) {
      instance.instanceVariables = [];
    }
    this.clipboardVars.forEach((instanceVar) => {
      const found = instance.instanceVariables.some((iv) => iv.id === instanceVar.value.id);
      if (found) {
        existingVars.push(instanceVar.value);
      } else {
        newVars.push(instanceVar.value);
      }
    });
    let message = `${this.clipboardVars.length} instance variables copied from clipboard. `;
    if (newVars.length) {
      instance.instanceVariables.push(...newVars);
      message += `Added ${newVars.length} instance variables. `;
    } else {
      message += 'No new instance variables to add. ';
    }

    if (existingVars.length) {
      message += `Skipped ${existingVars.length} instance variables for conflicting with existing ones.`;
    }

    instance.instanceVariables.sort((a, b) => a.id.localeCompare(b.id));
    this.buildVariables(this.edit.state$.value.config);

    this.snackbar.open(message, 'DISMISS');
  }

  private readFromClipboard() {
    if (!navigator.clipboard.readText) {
      // must be firefox. firefox allows reading the clipboard *only* from browser
      // extensions but never from web pages itself. it is rumored that there is a config
      // which can be enabled ("Dom.Events.Testing.AsynClipBoard"), however that did not
      // change browser behaviour in tests.
      this.clipboardVars = null;
      console.error('Clipboard access is not supported in this browser. Pasting applications is not possible.');
      return;
    }
    navigator.clipboard.readText().then(
      (data) => {
        this.clipboardVars = null;
        try {
          const instanceVariables: ConfigVariable[] = JSON.parse(data);
          const validNames = instanceVariables.every((iv) => !!iv.name);
          const validVariables = instanceVariables.every((iv) => !!iv.value && !!iv.value.id);
          if (!validNames || !validVariables) {
            console.error(`Invalid instance variables format.`);
          }
          this.clipboardVars = instanceVariables;
        } catch (e) {
          console.error('Unable to parse from clipboard', e);
        }
      },
      (e) => {
        console.error('Unable to read from clipboard', e);
        this.clipboardVars = null;
      },
    );
  }

  protected onAdd(templ: TemplateRef<unknown>) {
    this.newUsedIds = this.records.map((r) => r.name);
    this.newValue = {
      id: '',
      type: VariableType.STRING,
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

  protected onEdit(variable: ConfigVariable) {
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
        if (index !== -1) {
          vars.splice(index, 1, value);
          this.buildVariables(this.edit.state$.value.config);
        }
      });
  }

  protected onTypeChange(value: VariableType) {
    // check if we need to clear the value in case we switch from password to *something*.
    if (
      this.newValue.type !== value &&
      this.newValue.type === VariableType.PASSWORD &&
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
      1,
    );
    this.buildVariables(this.edit.state$.value.config);
  }
}

import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { cloneDeep } from 'lodash-es';
import {
  BehaviorSubject,
  Observable,
  Subscription,
  combineLatest,
  finalize,
  interval,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceDto, ParameterType, SystemConfigurationDto } from 'src/app/models/gen.dtos';
import { ContentCompletion } from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { buildCompletionPrefixes, buildCompletions } from 'src/app/modules/core/utils/completion.utils';
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
export class SystemVariablesComponent implements DirtyableDialog, OnInit, OnDestroy {
  private edit = inject(SystemsEditService);
  private instances = inject(InstancesService);
  private areas = inject(NavAreasService);
  private snackbar = inject(MatSnackBar);
  protected auth = inject(AuthenticationService);

  private readonly colEdit: BdDataColumn<ConfigVariable> = {
    id: 'edit',
    name: 'Edit',
    data: () => 'Edit',
    width: '40px',
    icon: () => 'edit',
    actionDisabled: () => !this.auth.isCurrentScopeWrite(),
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
    actionDisabled: () => !this.auth.isCurrentScopeWrite(),
    action: (r) => {
      this.onDelete(r);
    },
  };

  private orig: SystemConfigurationDto;
  protected system: SystemConfigurationDto;
  protected saving$ = new BehaviorSubject<boolean>(false);
  protected records: ConfigVariable[] = [];
  protected columns: BdDataColumn<ConfigVariable>[] = [colName, colValue, colDesc, this.colEdit, this.colDelete];
  protected checked: ConfigVariable[];

  protected newValue: VariableConfiguration;
  protected newUsedIds: string[] = [];

  protected typeValues: ParameterType[] = Object.values(ParameterType);

  protected completionPrefixes = buildCompletionPrefixes();
  protected completions: ContentCompletion[];

  protected clipboardVars: ConfigVariable[];

  private subscription: Subscription;
  private instancesUsing: InstanceDto[];

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) tb: BdDialogToolbarComponent;

  @ViewChild('addForm', { static: false }) addForm: NgForm;
  @ViewChild('editForm', { static: false }) editForm: NgForm;

  @ViewChild('editTemplate') editTemplate: TemplateRef<any>;

  ngOnInit() {
    this.subscription = this.edit.current$.subscribe((c) => {
      if (!c) {
        return;
      }

      this.system = cloneDeep(c);
      this.orig = cloneDeep(c);

      this.completions = buildCompletions(this.completionPrefixes, null, this.system.config, null, null);

      this.buildVariables();
    });

    this.subscription.add(
      combineLatest([this.edit.current$, this.instances.instances$]).subscribe(([c, i]) => {
        if (!c || !i) {
          this.instancesUsing = [];
          return;
        }

        this.instancesUsing = i.filter((i) => i.instanceConfiguration?.system?.name === c.key?.name);
      })
    );

    this.subscription.add(this.areas.registerDirtyable(this, 'panel'));

    this.subscription.add(
      interval(1000)
        .pipe(startWith(null))
        .subscribe(() => this.readFromClipboard())
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
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

    const save = this.edit.update(this.system).pipe(finalize(() => this.saving$.next(false)));

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
              return of(MAGIC_ABORT).pipe(finalize(() => this.saving$.next(false)));
            }
          })
        );
    } else {
      // no confirmation required
      return save;
    }
  }

  protected onSave(): void {
    this.doSave().subscribe((x) => {
      if (x != MAGIC_ABORT) {
        this.system = this.orig = null;
        this.tb.closePanel();
      }
    });
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
        })
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
    this.clipboardVars.forEach((systemVar) => {
      const found = this.system.config.systemVariables.some((sv) => sv.id === systemVar.value.id);
      if (found) {
        existingVars.push(systemVar.value);
      } else {
        newVars.push(systemVar.value);
      }
    });
    let message = `${this.clipboardVars.length} system variables copied from clipboard. `;
    if (newVars.length) {
      this.system.config.systemVariables.push(...newVars);
      message += `Added ${newVars.length} system variables. `;
    } else {
      message += 'No new system variables to add. ';
    }

    if (existingVars.length) {
      message += `Skipped ${existingVars.length} system variables for conflicting with existing ones.`;
    }

    this.system.config.systemVariables.sort((a, b) => a.id.localeCompare(b.id));
    this.buildVariables();
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
          const systemVariables: ConfigVariable[] = JSON.parse(data);
          const validNames = systemVariables.every((sv) => !!sv.name);
          const validVariables = systemVariables.every((sv) => !!sv.value && !!sv.value.id);
          if (!validNames || !validVariables) {
            console.error(`Invalid system variables format.`);
          }
          this.clipboardVars = systemVariables;
        } catch {}
      },
      (e) => {
        console.error('Unable to read from clipboard', e);
        this.clipboardVars = null;
      }
    );
  }

  protected onAdd(templ: TemplateRef<any>) {
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
        this.system.config.systemVariables.sort((a, b) => a.id.localeCompare(b.id));
        this.buildVariables();
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

        this.system.config.systemVariables.splice(
          this.system.config.systemVariables.findIndex((x) => x.id === value.id),
          1,
          value
        );
        this.buildVariables();
      });
  }

  protected onTypeChange(value: ParameterType) {
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

import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription, combineLatest, interval, of, startWith, tap } from 'rxjs';
import {
  ApplicationDto,
  CustomEditor,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  SystemConfiguration,
  VariableConfiguration,
  VariableDescriptor,
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
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { buildCompletionPrefixes, buildCompletions } from 'src/app/modules/core/utils/completion.utils';
import { VariableGroup, VariablePair } from 'src/app/modules/core/utils/variable-utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

const UNGROUPED = 'Ungrouped Variables';
const CUSTOM = 'Custom Variables';

class ConfigVariable {
  name: string;
  value: VariableConfiguration;
}

@Component({
  selector: 'app-instance-variables',
  templateUrl: './instance-variables.component.html',
  styleUrl: './instance-variables.component.css',
})
export class InstanceVariablesComponent implements DirtyableDialog, OnInit, OnDestroy, BdSearchable {
  private readonly systems = inject(SystemsService);
  private readonly areas = inject(NavAreasService);
  private readonly plugins = inject(PluginService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly products = inject(ProductsService);
  private readonly searchService = inject(SearchService);
  private readonly bop = inject(BreakpointObserver);
  private readonly edit = inject(InstanceEditService);
  protected readonly groups = inject(GroupsService);

  protected search: string;
  protected clipboardVars: ConfigVariable[];
  protected checked: ConfigVariable[] = [];

  protected newValue: VariableConfiguration;
  protected newUsedIds: string[] = [];

  protected instance: InstanceConfigurationDto;
  protected system: SystemConfiguration;
  protected apps: ApplicationDto[];
  protected typeValues: VariableType[] = Object.values(VariableType);
  protected editorValues: string[];

  protected completionPrefixes = buildCompletionPrefixes();
  protected completions: ContentCompletion[];

  protected narrow$ = new BehaviorSubject<boolean>(false);
  protected groups$ = new BehaviorSubject<VariableGroup[]>([]);
  private custom: VariableGroup;
  private descriptors: VariableDescriptor[];

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) tb: BdDialogToolbarComponent;

  @ViewChild('addForm', { static: false }) addForm: NgForm;
  @ViewChild('editForm', { static: false }) editForm: NgForm;

  ngOnInit() {
    this.subscription = combineLatest([
      this.edit.state$,
      this.edit.stateApplications$,
      this.systems.systems$,
      this.products.products$,
    ]).subscribe(([instance, apps, systems, products]) => {
      if (instance?.config) {
        const product = instance.config.config.product;
        this.descriptors = products.find(
          (p) => p.key.name === product.name && p.key.tag === product.tag,
        ).instanceVariables;

        this.instance = instance.config;
        this.apps = apps;

        if (instance?.config?.config?.system && systems?.length) {
          this.system = systems.find((s) => s.key.name === instance.config.config.system.name)?.config;
        }

        this.plugins
          .getAvailableEditorTypes(this.groups.current$?.value?.name, instance.config.config.product)
          .subscribe((editors) => {
            this.editorValues = editors;
          });

        this.groupVariables();
        this.buildCompletions();
      }
    });

    this.subscription.add(this.areas.registerDirtyable(this, 'panel'));
    this.subscription.add(this.searchService.register(this));

    this.subscription.add(
      this.bop.observe('(max-width: 800px)').subscribe((bs) => {
        this.narrow$.next(bs.matches);
      }),
    );

    this.subscription.add(
      interval(1000)
        .pipe(startWith(null))
        .subscribe(() => this.readFromClipboard()),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public bdOnSearch(s: string) {
    this.search = !s?.length ? null : s;
  }

  private buildCompletions() {
    this.completions = buildCompletions(this.completionPrefixes, this.instance, this.system, null, this.apps);
  }

  private groupVariables() {
    const descriptors = this.descriptors;
    const values = this.edit.state$.value.config.config.instanceVariables;
    // group all variable descriptors and configurations together for simple iteration in the template.
    const r: VariableGroup[] = [];
    for (const d of descriptors) {
      const grpName = d.groupName?.length ? d.groupName : UNGROUPED;
      let grp = r.find((g) => g.name === grpName);
      if (!grp) {
        grp = {
          name: grpName,
          pairs: [],
          isCustom: false,
          isSelectMode: false,
        };
        r.push(grp);
      }

      const pair: VariablePair = {
        descriptor: d,
        value: values.find((v) => v.id === d.id),
        editorEnabled: true, // used to lock once custom editor is loaded.
      };
      grp.pairs.push(pair);
    }

    // sort groups by name, ungrouped variables come last.
    r.sort((a, b) => {
      if (a?.name === b?.name) {
        return 0;
      }

      if (a?.name === UNGROUPED) {
        return 1;
      } else if (b?.name === UNGROUPED) {
        return -1;
      }

      return a?.name?.localeCompare(b?.name);
    });

    // find custom variables and add them
    this.custom = {
      name: CUSTOM,
      pairs: [],
      isCustom: true,
      isSelectMode: false,
    };
    for (const v of values) {
      if (!descriptors?.find((d) => d.id === v.id)) {
        // no descriptor -> custom
        this.custom.pairs.push({
          descriptor: null,
          value: v,
          editorEnabled: true,
        });
      }
    }

    // *always* add custom variables, even if none are there yet to allow adding some later. custom variables come even after ungrouped ones.
    r.push(this.custom);

    this.groups$.next(r);
  }

  protected doTrack(index: number, group: VariableGroup) {
    return group.name;
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

  protected isChecked(p: VariablePair) {
    return this.checked.some((cv) => cv.value.id === p.value.id);
  }

  protected toggleCheck(p: VariablePair) {
    const isChecked = this.isChecked(p);
    if (isChecked) {
      this.checked = this.checked.filter((cv) => cv.value.id !== p?.value?.id);
    } else {
      this.checked.push({ name: p.value.id, value: p.value });
    }
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
    this.clipboardVars.forEach((instanceVar: ConfigVariable) => {
      const found =
        instance.instanceVariables.some((iv) => iv.id === instanceVar.value.id) ||
        this.descriptors?.some((d) => d.id === instanceVar.value.id);
      if (found) {
        existingVars.push(instanceVar.value);
      } else {
        newVars.push(instanceVar.value);
      }
    });
    let message = `${this.clipboardVars.length} instance variables copied from clipboard. `;
    if (newVars.length) {
      instance.instanceVariables.push(...newVars);
      this.custom.pairs.push(...newVars.map((newVar) => ({ descriptor: null, value: newVar, editorEnabled: true })));
      message += `Added ${newVars.length} instance variables. `;
    } else {
      message += 'No new instance variables to add. ';
    }
    if (existingVars.length) {
      message += `Skipped ${existingVars.length} instance variables for conflicting with existing ones.`;
    }
    instance.instanceVariables.sort((a, b) => a.id.localeCompare(b.id));
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

  protected onAddCustomVariable(templ: TemplateRef<unknown>) {
    this.newUsedIds = this.groups$.value
      .flatMap((g) => g.pairs)
      .map((p) => p.value)
      .filter((v) => !!v)
      .map((v) => v.id);
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
        this.custom.pairs.push({
          descriptor: null,
          value,
          editorEnabled: true,
        });
        this.buildCompletions();
      });
  }

  protected onEditCustomVariable(variable: VariablePair, template: TemplateRef<unknown>) {
    this.newValue = cloneDeep(variable.value);
    this.dialog
      .message({
        header: 'Edit Variable',
        icon: 'edit',
        template,
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
          this.edit.state$.next(this.edit.state$.value); // retrigger group calculation
        }
      });
  }

  protected onRemoveCustomVariable(p: VariablePair) {
    const varList = this.edit.state$.value.config.config.instanceVariables;
    varList.splice(
      varList.findIndex((x) => x.id === p.value.id),
      1,
    );

    const index = this.custom.pairs.findIndex((x) => x.value.id === p.value.id);
    this.custom.pairs.splice(index, 1);

    // if this is the last variable, leave select mode.
    if (!this.custom.pairs.length) {
      this.custom.isSelectMode = false;
    }

    this.checked = this.checked.filter((cv) => cv.value.id !== p.value.id);

    this.buildCompletions();
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

  // new methods down here
  protected hasGroupSearchMatch(g: VariableGroup): boolean {
    if (!this.search) {
      return true;
    }

    for (const p of g.pairs) {
      if (this.hasPairSearchMatch(p)) {
        return true;
      }
    }
  }

  protected hasPairSearchMatch(p: VariablePair): boolean {
    if (!this.search) {
      return true;
    }

    // search name, description, value.
    return (
      [p.descriptor?.name, p.descriptor?.longDescription, p.value?.value]
        .join(' ')
        .toLowerCase()
        .indexOf(this.search.toLowerCase()) !== -1
    );
  }

  protected hasOptionals(g: VariableGroup): boolean {
    return g.isCustom && !!g.pairs.length;
  }

  protected getValueCount(g: VariableGroup) {
    return g.pairs.filter((p) => !!p.value && this.hasPairSearchMatch(p)).length;
  }

  protected doChangeVariable(p: VariablePair, val: LinkedValueConfiguration) {
    p.value.value = val;
  }

  protected doSetCustomEditorState(p: VariablePair, editor: CustomEditor) {
    p.editorEnabled = editor.allowDirectEdit;
  }

  protected sortPairs(pairs: VariablePair[]): VariablePair[] {
    return pairs.sort((a, b) => {
      if (!!a?.descriptor?.name && !!b?.descriptor?.name) {
        return a.descriptor.name.localeCompare(b.descriptor.name);
      }

      const ida = a.descriptor?.id ? a.descriptor.id : a.value.id;
      const idb = b.descriptor?.id ? b.descriptor.id : b.value.id;

      return ida.localeCompare(idb);
    });
  }
}

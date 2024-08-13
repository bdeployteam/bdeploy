import { BreakpointObserver } from '@angular/cdk/layout';
import {
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  TemplateRef,
  ViewChild,
  inject,
} from '@angular/core';
import { NgForm } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Subscription, interval, startWith } from 'rxjs';
import {
  ApplicationDto,
  CustomEditor,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  SystemConfiguration,
  VariableConfiguration,
  VariableType,
} from 'src/app/models/gen.dtos';
import { ContentCompletion } from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { VariableGroup, VariablePair } from 'src/app/modules/core/utils/variable-utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';

interface ConfigVariable {
  name: string;
  value: VariableConfiguration;
}

@Component({
  selector: 'bd-variable-groups',
  templateUrl: './bd-variable-groups.component.html',
  styleUrl: './bd-variable-groups.component.css',
})
export class BdVariableGroupsComponent implements OnInit, OnDestroy, BdSearchable {
  private readonly snackbar = inject(MatSnackBar);
  private readonly searchService = inject(SearchService);
  private readonly bop = inject(BreakpointObserver);
  protected readonly instanceGroups = inject(GroupsService);

  @Input() groups: VariableGroup[];
  @Input() variableList: VariableConfiguration[];
  @Input() completions: ContentCompletion[];
  @Input() completionPrefixes: ContentCompletion[];
  @Input() instance: InstanceConfigurationDto;
  @Input() system: SystemConfiguration;
  @Input() apps: ApplicationDto[];
  @Input() editorValues: string[];

  @Input() dialog: BdDialogComponent;

  @Output() variableListChanged = new EventEmitter<VariableConfiguration[]>();

  private search: string;
  protected clipboardVars: ConfigVariable[];
  protected checked: ConfigVariable[] = [];
  protected isCustomGroupSelected = false;

  protected typeValues: VariableType[] = Object.values(VariableType);

  protected newValue: VariableConfiguration;
  protected newUsedIds: string[] = [];

  protected narrow$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  @ViewChild('addForm', { static: false }) addForm: NgForm;
  @ViewChild('editForm', { static: false }) editForm: NgForm;

  ngOnInit() {
    this.subscription = this.searchService.register(this);

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

  protected doTrack(_: number, group: VariableGroup) {
    return group.name;
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
    const varList = this.variableList ? this.variableList : [];
    this.clipboardVars.forEach((configVar: ConfigVariable) => {
      const found = varList.some((iv) => iv.id === configVar.value.id);
      if (found) {
        existingVars.push(configVar.value);
      } else {
        newVars.push(configVar.value);
      }
    });
    let message = `${this.clipboardVars.length} variables copied from clipboard. `;
    if (newVars.length) {
      varList.push(...newVars);
      varList.sort((a, b) => a.id.localeCompare(b.id));
      this.variableListChanged.emit(varList);
      message += `Added ${newVars.length} variables. `;
    } else {
      message += 'No new variables to add. ';
    }
    if (existingVars.length) {
      message += `Skipped ${existingVars.length} variables for conflicting with existing ones.`;
    }
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
          const variables: ConfigVariable[] = JSON.parse(data);
          const validNames = variables.every((iv) => !!iv.name);
          const validVariables = variables.every((iv) => !!iv.value && !!iv.value.id);
          if (!validNames || !validVariables) {
            console.error(`Invalid variables format.`);
          }
          this.clipboardVars = variables;
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
    this.newUsedIds = this.groups
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

        const varList = this.variableList ? this.variableList : [];

        varList.push(value);
        varList.sort((a, b) => a.id.localeCompare(b.id));

        this.variableListChanged.emit(varList);
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

        const varList = this.variableList;
        const index = varList.findIndex((x) => x.id === value.id);
        if (index !== -1) {
          varList.splice(index, 1, value);
          this.variableListChanged.emit(varList);
        }
      });
  }

  protected onRemoveCustomVariable(p: VariablePair, g: VariableGroup) {
    const varList = this.variableList;
    varList.splice(
      varList.findIndex((x) => x.id === p.value.id),
      1,
    );

    this.checked = this.checked.filter((cv) => cv.value.id !== p.value.id);

    // if this is the last variable, leave select mode.
    if (g.pairs.length === 1) {
      this.isCustomGroupSelected = false;
    }

    this.variableListChanged.emit(varList);
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
    return g.pairs.filter((p) => this.hasPairSearchMatch(p)).length;
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

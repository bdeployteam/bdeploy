import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
  Input,
  OnDestroy,
  OnInit,
  Output,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Subscription } from 'rxjs';
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
import { ClipboardService } from '../../services/clipboard.service';
import { BdFormInputComponent } from '../bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../validators/trimmed.directive';
import { EditUniqueValueValidatorDirective } from '../../validators/edit-unique-value.directive';
import { BdValueEditorComponent } from '../bd-value-editor/bd-value-editor.component';
import { MatDivider } from '@angular/material/divider';
import { BdFormSelectComponent } from '../bd-form-select/bd-form-select.component';
import {
  MatAccordion,
  MatExpansionPanel,
  MatExpansionPanelDescription,
  MatExpansionPanelHeader,
  MatExpansionPanelTitle,
} from '@angular/material/expansion';
import { BdButtonComponent } from '../bd-button/bd-button.component';
import { ClickStopPropagationDirective } from '../../directives/click-stop-propagation.directive';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe, NgClass } from '@angular/common';
import { BdVariableDescCardComponent } from '../bd-variable-desc-card/bd-variable-desc-card.component';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatTooltip } from '@angular/material/tooltip';
import { MatRipple } from '@angular/material/core';
import { BdPopupDirective } from '../bd-popup/bd-popup.directive';

interface ConfigVariable {
  name: string;
  value: VariableConfiguration;
}

@Component({
  selector: 'app-bd-variable-groups',
  templateUrl: './bd-variable-groups.component.html',
  styleUrl: './bd-variable-groups.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    BdFormInputComponent,
    TrimmedValidator,
    EditUniqueValueValidatorDirective,
    BdValueEditorComponent,
    MatDivider,
    BdFormSelectComponent,
    MatAccordion,
    MatExpansionPanel,
    MatExpansionPanelHeader,
    MatExpansionPanelTitle,
    MatExpansionPanelDescription,
    BdButtonComponent,
    ClickStopPropagationDirective,
    MatIcon,
    NgClass,
    BdVariableDescCardComponent,
    MatCheckbox,
    MatTooltip,
    MatRipple,
    BdPopupDirective,
    AsyncPipe,
  ],
})
export class BdVariableGroupsComponent implements OnInit, OnDestroy, BdSearchable {
  private readonly cd = inject(ChangeDetectorRef);
  private readonly snackbar = inject(MatSnackBar);
  private readonly searchService = inject(SearchService);
  private readonly bop = inject(BreakpointObserver);
  private readonly clipboardService = inject(ClipboardService);
  protected readonly instanceGroups = inject(GroupsService);

  @Input() groups: VariableGroup[];
  @Input() variableList: VariableConfiguration[];
  @Input() completions: ContentCompletion[];
  @Input() completionPrefixes: ContentCompletion[];
  @Input() instance: InstanceConfigurationDto;
  @Input() system: SystemConfiguration;
  @Input() apps: ApplicationDto[];
  @Input() editorValues: string[];
  @Input() suggestedIds: string[]; // passed instance variable ids for system variables to overwrite
  @Input() overwriteIds: string[]; // ids that might overwrite variables in this variable group

  @Input() dialog: BdDialogComponent;

  @Output() variableListChanged = new EventEmitter<VariableConfiguration[]>();

  private search: string;
  protected clipboardVars$ = new BehaviorSubject<ConfigVariable[]>([]);
  protected checked: ConfigVariable[] = [];
  protected isCustomGroupSelected = false;

  protected typeValues: VariableType[] = Object.values(VariableType);

  protected newValue: VariableConfiguration;
  protected newUsedIds: string[] = [];

  private subscription: Subscription;

  @ViewChild('addForm', { static: false }) addForm: NgForm;
  @ViewChild('editForm', { static: false }) editForm: NgForm;

  ngOnInit() {
    this.subscription = this.searchService.register(this);

    this.subscription.add(this.clipboardService.clipboard$.subscribe((cb) => this.readFromClipboard(cb.data)));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected isOverwritten(p: VariablePair): boolean {
    const id = p.descriptor?.id || p.value.id;
    return this.overwriteIds?.some((i) => i === id);
  }

  protected getOverwrittenWarningMessage(p: VariablePair): string {
    const id = p.descriptor?.id || p.value.id;
    return `Variable ${id} is set through system variable`;
  }

  public bdOnSearch(s: string) {
    this.search = !s?.length ? null : s;
    this.cd.markForCheck();
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

  private copyVariables(vars: ConfigVariable[]) {
    const json = JSON.stringify(vars, null, '\t');
    navigator.clipboard.writeText(json).then(
      () =>
        this.snackbar.open('Copied to clipboard successfully', null, {
          duration: 1000
        }),
      () =>
        this.snackbar.open('Unable to write to clipboard.', null, {
          duration: 1000
        })
    );
  }

  protected doCopyChecked() {
    this.copyVariables(this.checked);
  }

  protected doCopyAll(group: VariableGroup) {
    this.copyVariables(group.pairs.map((p) => ({ name: p.value.id, value: p.value })));
  }

  protected doPaste(group: VariableGroup) {
    const clipboardVars = this.clipboardVars$.value;
    if (!clipboardVars.length) {
      this.snackbar.open('Unable to read from clipboard.', null, {
        duration: 1000,
      });
      return;
    }
    const newVars: VariableConfiguration[] = [];
    let updateCount = 0;
    const varList = group.pairs.map((p) => p.value);
    clipboardVars.forEach((configVar: ConfigVariable) => {
      const v = varList.find((iv) => iv.id === configVar.value.id);
      if (v) {
        v.value = configVar.value.value;
        updateCount++;
      } else {
        newVars.push(configVar.value);
      }
    });
    let message = `${clipboardVars.length} variables read from clipboard.`;
    if (newVars.length && group.isCustom) {
      varList.push(...newVars);
      varList.sort((a, b) => a.id.localeCompare(b.id));
      this.variableListChanged.emit(varList);
      message += ` Added ${newVars.length} variables.`;
    } else if (newVars.length) {
      message += ` Ignored ${newVars.length} mismatched variables.`;
    }
    if (updateCount) {
      message += ` Updated ${updateCount} variables.`;
    }
    this.snackbar.open(message, 'DISMISS');
  }

  private readFromClipboard(data: string) {
    this.clipboardVars$.next([]);
    if (!data) {
      return;
    }
    try {
      const variables: ConfigVariable[] = JSON.parse(data);
      const validNames = variables?.every((iv) => !!iv.name);
      const validVariables = variables?.every((iv) => !!iv.value && !!iv.value.id);
      if (!validNames || !validVariables) {
        console.error(`Invalid variables format.`);
      } else {
        this.clipboardVars$.next(variables);
      }
    } catch (e) {
      console.error('Unable to parse from clipboard', e);
    }
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
        validation: () => !!this.editForm && this.editForm.valid,
        actions: [ACTION_CANCEL, ACTION_OK],
        template,
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
      1
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
    return false;
  }

  protected hasPairSearchMatch(p: VariablePair): boolean {
    if (!this.search) {
      return true;
    }

    // search by name, description, value, id.
    return [
      p.descriptor?.name,
      p.descriptor?.longDescription,
      p.value?.id,
      p.value?.description,
      p.value?.value?.value,
      p.value?.value?.linkExpression,
    ]
      .join(' ')
      .toLowerCase()
      .includes(this.search.toLowerCase());
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
}

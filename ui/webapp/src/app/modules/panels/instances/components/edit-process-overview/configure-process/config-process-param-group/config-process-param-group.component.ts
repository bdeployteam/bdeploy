import {
  Component,
  EventEmitter,
  inject,
  Input,
  OnDestroy,
  OnInit,
  Output,
  QueryList,
  TemplateRef,
  ViewChild,
  ViewChildren,
  ViewEncapsulation,
} from '@angular/core';
import { FormsModule, NgControl, NgForm } from '@angular/forms';
import { MatButtonToggle, MatButtonToggleChange, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, combineLatest, Observable, of, Subscription } from 'rxjs';
import { debounceTime, map, skipWhile } from 'rxjs/operators';
import {
  ApplicationConfiguration,
  ApplicationDto,
  CustomEditor,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  ParameterConfiguration,
  ParameterConfigurationTarget,
  ParameterDescriptor,
  SystemConfiguration,
  VariableType,
} from 'src/app/models/gen.dtos';
import { ContentCompletion } from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdPopupDirective } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import { ClipboardService } from 'src/app/modules/core/services/clipboard.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { buildCompletionPrefixes, buildCompletions } from 'src/app/modules/core/utils/completion.utils';
import { createLinkedValue, getPreRenderable, getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { ProcessEditService } from '../../../../services/process-edit.service';
import { HistoryProcessConfigComponent } from '../../../history-process-config/history-process-config.component';
import { BdFormInputComponent } from '../../../../../../core/components/bd-form-input/bd-form-input.component';
import { EditCustomIdValidatorDirective } from '../../../../validators/edit-custom-id-validator.directive';
import { EditUniqueValueValidatorDirective } from '../../../../../../core/validators/edit-unique-value.directive';
import { TrimmedValidator } from '../../../../../../core/validators/trimmed.directive';
import { BdFormSelectComponent } from '../../../../../../core/components/bd-form-select/bd-form-select.component';
import {
  MatAccordion,
  MatExpansionPanel,
  MatExpansionPanelDescription,
  MatExpansionPanelHeader,
  MatExpansionPanelTitle,
} from '@angular/material/expansion';
import { BdButtonComponent } from '../../../../../../core/components/bd-button/bd-button.component';
import { ClickStopPropagationDirective } from '../../../../../../core/directives/click-stop-propagation.directive';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe, NgClass } from '@angular/common';
import { ParamDescCardComponent } from '../../../param-desc-card/param-desc-card.component';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatTooltip } from '@angular/material/tooltip';
import { BdValueEditorComponent } from '../../../../../../core/components/bd-value-editor/bd-value-editor.component';

import { EditServerIssuesValidatorDirective } from '../../../../validators/edit-server-issues-validator.directive';
import { MatDivider } from '@angular/material/divider';

const UNGROUPED = 'Ungrouped Parameters';
const CUSTOM = 'Custom Parameters';

interface ParameterPair {
  descriptor: ParameterDescriptor;
  value: ParameterConfiguration;

  editorEnabled: boolean;
}

interface ParameterGroup {
  name: string;
  pairs: ParameterPair[];

  isCustom: boolean;
  isSelectMode: boolean;
}

@Component({
  selector: 'app-config-process-param-group',
  templateUrl: './config-process-param-group.component.html',
  styleUrls: ['./config-process-param-group.component.css'],
  encapsulation: ViewEncapsulation.None,
  imports: [
    BdFormInputComponent,
    EditCustomIdValidatorDirective,
    EditUniqueValueValidatorDirective,
    FormsModule,
    TrimmedValidator,
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
    ParamDescCardComponent,
    MatCheckbox,
    MatTooltip,
    BdValueEditorComponent,
    BdPopupDirective,
    EditServerIssuesValidatorDirective,
    MatDivider,
    MatButtonToggleGroup,
    MatButtonToggle,
    HistoryProcessConfigComponent,
    AsyncPipe,
  ],
})
export class ConfigProcessParamGroupComponent implements OnInit, OnDestroy, BdSearchable {
  private readonly systems = inject(SystemsService);
  private readonly searchService = inject(SearchService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly clipboardService = inject(ClipboardService);

  protected readonly edit = inject(ProcessEditService);
  protected readonly instances = inject(InstanceEditService);
  protected readonly groups = inject(GroupsService);

  protected groups$ = new BehaviorSubject<ParameterGroup[]>(null);
  protected search: string;
  protected process: ApplicationConfiguration;
  protected app: ApplicationDto;

  protected clipboardParams: ParameterConfiguration[];
  protected checked: ParameterConfiguration[] = [];

  protected instance$ = new BehaviorSubject<InstanceConfigurationDto>(null);
  protected system$ = new BehaviorSubject<SystemConfiguration>(null);
  protected linkEditorPopup$ = new BehaviorSubject<BdPopupDirective>(null);

  protected completions: ContentCompletion[];
  protected completionPrefixes: ContentCompletion[] = buildCompletionPrefixes();

  @Input() dialog: BdDialogComponent;

  @ViewChild(HistoryProcessConfigComponent)
  preview: HistoryProcessConfigComponent;
  @ViewChildren('groupForm') public forms: QueryList<NgForm>;
  @ViewChildren('validateCustom', { read: NgControl })
  private readonly validateCustomFields: QueryList<NgControl>;
  @Output() checkIsInvalid = new EventEmitter<boolean>();

  private custom: ParameterGroup;
  protected customTemp: {
    isEdit: boolean;
    predecessor: string;
    id: string;
    value: string;
  };

  private readonly updatePreview$ = new BehaviorSubject<boolean>(false);
  protected expandPreview = true;
  private subscription: Subscription;
  private formsLoaded = false;

  protected previewProcess$ = new BehaviorSubject<ApplicationConfiguration>(null);

  ngOnInit(): void {
    this.subscription = combineLatest([this.edit.process$, this.edit.application$]).subscribe(([process, app]) => {
      if (!process || !app) {
        this.groups$.next(null);
        return;
      }
      this.process = process;
      this.app = app;
      // group all parameter descriptors and configurations together for simple iteration in the template.
      const r: ParameterGroup[] = [];
      for (const pd of app.descriptor.startCommand.parameters) {
        const grpName = pd.groupName?.length ? pd.groupName : UNGROUPED;
        let grp = r.find((g) => g.name === grpName);
        if (!grp) {
          grp = {
            name: grpName,
            pairs: [],
            isCustom: false,
            isSelectMode: false
          };
          r.push(grp);
        }

        const pair: ParameterPair = {
          descriptor: pd,
          value: null,
          editorEnabled: true // used to lock once custom editor is loaded.
        };
        grp.pairs.push(pair);

        // check if the param has a value (yet) - if yes, push it now to have the order right within the group (same as descriptor order).
        const val = process.start?.parameters?.find((p) => p.id === pd.id);
        if (val) {
          pair.value = val;
        }
      }

      // sort groups by name, ungrouped parameters come last.
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

      // find custom parameters and add them;
      this.custom = {
        name: CUSTOM,
        pairs: [],
        isCustom: true,
        isSelectMode: false
      };
      for (const pv of process.start.parameters) {
        if (!app.descriptor?.startCommand?.parameters?.find((d) => d.id === pv.id)) {
          // no descriptor -> custom
          this.custom.pairs.push({
            descriptor: null,
            value: pv,
            editorEnabled: true
          });
        }
      }

      // *always* add custom parameters, even if none are there yet to allow adding some. custom params are even after ungrouped ones.
      r.push(this.custom);

      this.groups$.next(r);
      this.completions = this.buildCompletions();
    });

    this.subscription.add(this.groups$.subscribe(() => setTimeout(() => this.checkFormsStatus())));

    this.subscription.add(
      combineLatest([
        this.edit.process$,
        this.edit.application$,
        this.instance$,
        this.system$,
        this.updatePreview$.pipe(debounceTime(400))
      ])
        .pipe(skipWhile(([p, a, i]) => !p || !a || !i))
        .subscribe(([process, application, instance, system]) => {
          const previewProcess = structuredClone(process);
          previewProcess.start.parameters.forEach((p) => {
            const descriptor = application.descriptor.startCommand.parameters.find((appParam) => appParam.id === p.id);
            const lv = this.expandPreview
              ? createLinkedValue(getRenderPreview(p.value, process, instance, system))
              : p.value;
            p.preRendered = this.edit.preRenderParameter(descriptor, lv);
          });
          this.previewProcess$.next(previewProcess);
        })
    );

    this.subscription.add(
      this.previewProcess$
        .pipe(
          skipWhile((x) => !x),
          debounceTime(100)
        )
        .subscribe(() => this.preview.update())
    );

    this.subscription.add(
      combineLatest([this.instances.state$, this.systems.systems$]).subscribe(([i, s]) => {
        this.instance$.next(i?.config);

        if (!i?.config?.config?.system || !s?.length) {
          this.system$.next(null);
          this.completions = this.buildCompletions();
          return;
        }

        this.system$.next(
          s.find((x) => x.key.name === i.config.config.system.name && x.key.tag === i.config.config.system.tag)?.config
        );
        this.completions = this.buildCompletions();
      })
    );

    this.subscription.add(this.searchService.register(this));

    this.subscription.add(this.clipboardService.clipboard$.subscribe((cb) => this.readFromClipboard(cb.data)));
  }

  private buildCompletions(): ContentCompletion[] {
    return buildCompletions(
      this.completionPrefixes,
      this.instance$.value,
      this.system$.value,
      this.process,
      this.instances.stateApplications$.value
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private checkFormsStatus() {
    if (!this.formsLoaded && this.forms?.length) {
      this.forms.forEach((form) => {
        this.subscription.add(form.statusChanges.pipe(debounceTime(100)).subscribe(() => this.emitIsInvalid()));
      });
      this.formsLoaded = true;
      this.emitIsInvalid();
    }
  }

  private emitIsInvalid() {
    this.checkIsInvalid.emit(this.forms.some((form) => !form.valid && !form.disabled));
  }

  public bdOnSearch(s: string) {
    this.search = !s?.length ? null : s;
  }

  protected getValueCount(g: ParameterGroup) {
    return g.pairs.filter((p) => !!p.value && this.hasPairSearchMatch(p)).length;
  }

  protected isChecked(p: ParameterPair) {
    return this.checked.some((param) => param.id === p.value.id);
  }

  protected toggleCheck(p: ParameterPair) {
    const isChecked = this.isChecked(p);
    if (isChecked) {
      this.checked = this.checked.filter((param) => param.id !== p?.value?.id);
    } else {
      this.checked.push(p.value);
    }
  }

  protected doCopy() {
    const json = JSON.stringify(this.checked, null, '\t');
    navigator.clipboard.writeText(json).then(
      () =>
        this.snackbar.open('Copied to clipboard successfully', null, {
          duration: 1000
        }),
      () =>
        this.snackbar.open('Unable to write to clipboard', null, {
          duration: 1000
        })
    );
  }

  protected doPaste() {
    if (!this.clipboardParams?.length) {
      this.snackbar.open('Unable to read from clipboard', null, {
        duration: 1000
      });
      return;
    }
    let addedCnt = 0;
    let skippedCnt = 0;
    this.clipboardParams.forEach((param: ParameterConfiguration) => {
      const paramList = this.edit.process$.value.start.parameters;
      const found = paramList.some((p) => p.id === param.id);
      if (found) {
        skippedCnt++;
        return;
      }
      this.customTemp = {
        predecessor: null,
        id: param.id,
        value: param.value.linkExpression || param.value.value,
        isEdit: false
      };
      this.insertCustomParameterAtCorrectPosition();
      this.customTemp = null;
      addedCnt++;
    });
    let message = `${this.clipboardParams.length} parameters copied from clipboard.`;
    if (addedCnt > 0) {
      message += ` Added ${addedCnt} parameters at the beginning of the command (predecessor information is not copied).`;
    } else {
      message += ' No new parameters to add.';
    }
    if (skippedCnt > 0) {
      message += ` Skipped ${skippedCnt} parameters for conflicting with existing ones.`;
    }
    this.snackbar.open(message, 'DISMISS');
  }

  private readFromClipboard(data: string) {
    this.clipboardParams = null;
    if (!data) {
      return;
    }
    try {
      const params: ParameterConfiguration[] = JSON.parse(data);
      const validIds = params?.every((iv) => !!iv.id);
      if (!validIds) {
        console.error(`Invalid parameters format`);
      } else {
        this.clipboardParams = params;
      }
    } catch (e) {
      console.error('Unable to parse from clipboard', e);
    }
  }

  protected doAddRemoveParameter(g: ParameterGroup, p: ParameterPair) {
    const paramList = this.edit.process$.value.start.parameters;
    if (!p.value) {
      const descriptors = this.edit.application$.value.descriptor.startCommand.parameters;

      let initialValue = p.descriptor?.defaultValue;
      if (p.descriptor?.type === VariableType.BOOLEAN && !initialValue) {
        initialValue = createLinkedValue('false');
      }
      if (p.descriptor.global) {
        // need to lookup a potential already existing global value.
        const global = this.edit.getGlobalParameter(p.descriptor.id);
        if (global) {
          initialValue = this.edit.getGlobalParameter(p.descriptor.id).value;
        }
      }

      // create the new parameter.
      p.value = {
        id: p.descriptor.id,
        value: initialValue,
        pinned: false,
        preRendered: [],
        target:
          p.descriptor.type === VariableType.ENVIRONMENT
            ? ParameterConfigurationTarget.ENVIRONMENT
            : ParameterConfigurationTarget.COMMAND
      };
      this.doPreRender(p);

      // the correct insertion point is *before* the *succeeding* parameter definition, as custom parameters may succedd the *preceeding* one.
      const myIndex = descriptors.findIndex((x) => x.id === p.descriptor.id);

      // find the next descriptor which *has* a value *after* my own desciptor.
      const nextDesc = descriptors.find((v, i) => i > myIndex && !!paramList.some((x) => x.id === v.id));

      // if we don't have a next one, simply push it at the end of the list.
      if (!nextDesc) {
        paramList.push(p.value);
      } else {
        paramList.splice(
          paramList.findIndex((x) => x.id === nextDesc.id),
          0,
          p.value
        );
      }
    } else {
      paramList.splice(
        paramList.findIndex((x) => x.id === p.value.id),
        1
      );

      if (g.isCustom) {
        // COMPLETELY remove a custom parameter.
        const index = this.custom.pairs.findIndex((x) => x.value.id === p.value.id);
        this.custom.pairs.splice(index, 1);

        // if this is the last parameter, leave select mode.
        if (!this.custom.pairs.length) {
          this.custom.isSelectMode = false;
        }
      }

      p.value = null;
    }

    this.doUpdateConditionals(p);

    this.updatePreview$.next(true);

    // rebuild completions if parameters changed.
    this.completions = this.buildCompletions();
  }

  protected getAllValueIds() {
    return this.edit.process$.value.start.parameters
      .map((x) => x.id)
      .filter((id) => !this.customTemp.isEdit || id !== this.customTemp.id);
  }

  protected getAllValueIdLabels() {
    return this.getAllValueIds().map(
      (u) => this.edit.application$.value.descriptor.startCommand.parameters.find((x) => x.id === u)?.name || u
    );
  }

  protected onEditCustomParameter(param: ParameterPair, template: TemplateRef<unknown>) {
    // may not be (and is not) called for linkExpression (different editor).
    const parameters = this.edit.process$.value.start.parameters;
    const paramIndex = parameters.findIndex((p) => p.id === param.value.id);
    this.customTemp = {
      predecessor: paramIndex > 0 ? parameters[paramIndex - 1].id : null,
      id: param.value.id,
      value: param.value.value.value,
      isEdit: true
    };
    this.dialog
      .message({
        template,
        header: 'Edit Custom Parameter',
        actions: [ACTION_CANCEL, ACTION_OK],
        validation: () => {
          if (this.validateCustomFields.length < 1) {
            return false;
          }
          return this.validateCustomFields.map((ctrl) => ctrl.valid || ctrl.disabled).reduce((p, c) => p && c, true);
        }
      })
      .subscribe((r) => {
        if (r) {
          // delete old parameter version
          this.deleteCustomParameter(this.customTemp.id);
          // insert updated parameter at the correct position.
          this.insertCustomParameterAtCorrectPosition();
        }
        this.customTemp = null;
      });
  }

  protected onAddCustomParameter(template: TemplateRef<unknown>) {
    this.customTemp = {
      predecessor: null,
      id: null,
      value: null,
      isEdit: false
    };
    this.dialog
      .message({
        header: 'Add Custom Parameter',
        template: template,
        actions: [ACTION_CANCEL, ACTION_OK],
        validation: () => {
          if (this.validateCustomFields.length < 1) {
            return false;
          }
          return this.validateCustomFields.map((ctrl) => ctrl.valid).reduce((p, c) => p && c, true);
        }
      })
      .subscribe((r) => {
        if (r) {
          // insert the parameter at the correct position.
          this.insertCustomParameterAtCorrectPosition();
        }
        this.customTemp = null;
      });
  }

  private deleteCustomParameter(id: string) {
    const pairIndex = this.custom.pairs.findIndex((pair) => pair.value.id === id);
    if (pairIndex >= 0) {
      this.custom.pairs.splice(pairIndex, 1);
    }
    const parameters = this.edit.process$.value.start.parameters;
    const paramIndex = parameters.findIndex((p) => p.id === id);
    if (paramIndex >= 0) {
      parameters.splice(paramIndex, 1);
    }
  }

  private insertCustomParameterAtCorrectPosition() {
    const param: ParameterPair = {
      descriptor: null,
      value: {
        id: this.customTemp.id,
        value: createLinkedValue(this.customTemp.value),
        pinned: false,
        preRendered: [],
        target: ParameterConfigurationTarget.COMMAND // TODO: support custom environment?
      },
      editorEnabled: true
    };
    this.doPreRender(param);
    this.custom.pairs.push(param);

    if (!this.customTemp.predecessor) {
      this.edit.process$.value.start.parameters.unshift(param.value);
    } else {
      const predecessorIndex = this.edit.process$.value.start.parameters.findIndex(
        (p) => p.id === this.customTemp.predecessor
      );
      if (predecessorIndex === this.edit.process$.value.start.parameters.length - 1) {
        // last parameter
        this.edit.process$.value.start.parameters.push(param.value);
      } else {
        this.edit.process$.value.start.parameters.splice(predecessorIndex + 1, 0, param.value);
      }
    }

    this.updatePreview$.next(true);

    // rebuild completions if parameters changed.
    this.completions = this.buildCompletions();
  }

  protected doChangeParam(p: ParameterPair, val: LinkedValueConfiguration) {
    p.value.value = val;
    this.doPreRender(p);

    // need to make sure we add/remove parameters which meet/don't meet their condition.
    this.doUpdateConditionals(p);
  }

  protected doTogglePin(p: ParameterPair) {
    p.value.pinned = !p.value.pinned;
  }

  private doUpdateConditionals(p: ParameterPair) {
    const id = p.descriptor ? p.descriptor.id : p.value?.id;
    if (!id) {
      return;
    }
    for (const grp of this.groups$.value) {
      for (const pair of grp.pairs) {
        // in case the value of a parameter is *referencing* this parameter, we need to update conditionals for the other parameter as well.
        if (pair.value?.value?.linkExpression && pair.value?.value?.linkExpression?.indexOf(`:${id}}}`) > 0) {
          // MIGHT be a reference to another application, but we update just in case.
          this.doUpdateConditionals(pair);
        }
      }
      for (const pair of grp.pairs) {
        if (pair.descriptor?.condition?.parameter === id || pair.descriptor?.condition?.expression?.includes(id)) {
          // the parameter is conditional on the changed parameter.
          this.edit.meetsConditionOnCurrent(pair.descriptor).subscribe((ok) => {
            const mustBeAdded = ok && !pair.value && pair.descriptor?.mandatory;
            const mustBeRemoved = !ok && !!pair.value;
            if (mustBeAdded || mustBeRemoved) {
              this.doAddRemoveParameter(grp, pair);
            }
          });
        }
      }
    }
  }

  protected doPreRender(p: ParameterPair) {
    p.value.preRendered = this.edit.preRenderParameter(p.descriptor, p.value.value);
    p.value.target =
      p.descriptor?.type === VariableType.ENVIRONMENT
        ? ParameterConfigurationTarget.ENVIRONMENT
        : ParameterConfigurationTarget.COMMAND;
    this.updatePreview$.next(true);
  }

  protected toggleExpandPreview(event: MatButtonToggleChange) {
    this.expandPreview = event.value === '1';
    this.updatePreview$.next(true);
  }

  protected doSetCustomEditorState(p: ParameterPair, editor: CustomEditor) {
    p.editorEnabled = editor.allowDirectEdit;
  }

  protected hasOptionals(g: ParameterGroup): boolean {
    if (g.isCustom) {
      return !!g.pairs.length;
    }

    return g.pairs
      .filter((p) => !!p.descriptor)
      .map((p) => p.descriptor.mandatory)
      .some((m) => !m);
  }

  protected isInvalid(groupForm: NgForm, g: ParameterGroup): boolean {
    return groupForm.invalid || this.hasMissingRequired(g) || this.hasExistingForbidden(g);
  }

  protected hasMissingRequired(g: ParameterGroup): boolean {
    if (g.isCustom) {
      return false;
    }
    return (
      g.pairs.filter(
        (p) =>
          p.descriptor?.mandatory &&
          !p.value?.value &&
          this.edit.meetsConditionOnGiven(p.descriptor, this.app.descriptor.startCommand, this.process)
      )?.length > 0
    );
  }

  protected hasExistingForbidden(g: ParameterGroup): boolean {
    if (g.isCustom) {
      return false;
    }
    return (
      g.pairs.filter(
        (p) =>
          p.descriptor?.mandatory &&
          p.value?.value &&
          !this.edit.meetsConditionOnGiven(p.descriptor, this.app.descriptor.startCommand, this.process)
      )?.length > 0
    );
  }

  protected hasPairSearchMatch(p: ParameterPair): boolean {
    if (!this.search) {
      return true;
    }

    // search name, description, parameter, value.
    return [
      p.descriptor?.name,
      p.descriptor?.longDescription,
      p.descriptor?.parameter,
      p.value?.id,
      getPreRenderable(p.value?.value, p.descriptor?.type),
    ]
      .join(' ')
      .toLowerCase()
      .includes(this.search.toLowerCase());
  }

  protected hasGroupSearchMatch(g: ParameterGroup): boolean {
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

  protected canAddRemove(param: ParameterPair): Observable<boolean> {
    if (!param.value) {
      return this.edit.meetsConditionOnCurrent(param.descriptor);
    }

    if (param?.descriptor?.mandatory) {
      // mandatory, cannot manually add/remove. triggering the condition on another parameter will automatically add/remove.
      return this.edit.meetsConditionOnCurrent(param.descriptor).pipe(map((b) => !b));
    } else {
      // optional
      return of(true);
    }
  }

  protected sortPairs(pairs: ParameterPair[]) {
    return pairs.sort((a, b) => {
      if (!!a.descriptor && !!b.descriptor) {
        return a.descriptor.name.localeCompare(b.descriptor.name);
      }

      const ida = a.descriptor?.id ? a.descriptor.id : a.value.id;
      const idb = b.descriptor?.id ? b.descriptor.id : b.value.id;

      return ida.localeCompare(idb);
    });
  }
}

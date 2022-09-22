import { BreakpointObserver } from '@angular/cdk/layout';
import {
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  QueryList,
  TemplateRef,
  ViewChild,
  ViewChildren,
  ViewEncapsulation,
} from '@angular/core';
import { NgControl, NgForm } from '@angular/forms';
import {
  BehaviorSubject,
  combineLatest,
  Observable,
  of,
  Subscription,
} from 'rxjs';
import { debounceTime, map, skipWhile } from 'rxjs/operators';
import {
  ApplicationConfiguration,
  ApplicationDto,
  CustomEditor,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  ParameterConfiguration,
  ParameterDescriptor,
  SystemConfiguration,
} from 'src/app/models/gen.dtos';
import { ContentCompletion } from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdPopupDirective } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import {
  BdSearchable,
  SearchService,
} from 'src/app/modules/core/services/search.service';
import {
  buildCompletionPrefixes,
  buildCompletions,
} from 'src/app/modules/core/utils/completion.utils';
import {
  createLinkedValue,
  getPreRenderable,
} from 'src/app/modules/core/utils/linked-values.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { ProcessEditService } from '../../../../services/process-edit.service';
import { HistoryProcessConfigComponent } from '../../../history-process-config/history-process-config.component';

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
})
export class ConfigProcessParamGroupComponent
  implements OnDestroy, BdSearchable
{
  /* template */ groups$ = new BehaviorSubject<ParameterGroup[]>(null);
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);
  /* template */ search: string;
  /* template */ process: ApplicationConfiguration;
  /* template */ app: ApplicationDto;
  /* template */ globalsAllowed: boolean;

  /* template */ instance: InstanceConfigurationDto;
  /* template */ system: SystemConfiguration;
  /* template */ linkEditorPopup$ = new BehaviorSubject<BdPopupDirective>(null);

  /* template */ completions: ContentCompletion[];
  /* template */ completionPrefixes: ContentCompletion[] =
    buildCompletionPrefixes();

  @Input() dialog: BdDialogComponent;

  @ViewChild(HistoryProcessConfigComponent)
  preview: HistoryProcessConfigComponent;
  @ViewChildren('groupForm') public forms: QueryList<NgForm>;
  @ViewChildren('validateCustom', { read: NgControl })
  private validateCustomFields: QueryList<NgControl>;
  @Output() checkIsInvalid = new EventEmitter<boolean>();

  private custom: ParameterGroup;
  /* template */ customTemp: {
    isEdit: boolean;
    predecessor: string;
    id: string;
    value: string;
  };

  private updatePreview$ = new BehaviorSubject<boolean>(false);
  private subscription: Subscription;
  private formsLoaded = false;

  constructor(
    bop: BreakpointObserver,
    systems: SystemsService,
    private searchService: SearchService,
    public edit: ProcessEditService,
    public instances: InstanceEditService,
    public groups: GroupsService
  ) {
    this.subscription = combineLatest([
      this.edit.process$,
      this.edit.application$,
      this.instances.globalsMigrated$,
    ]).subscribe(([process, app, globalsMigrated]) => {
      if (!process || !app) {
        this.groups$.next(null);
        return;
      }
      this.globalsAllowed = !globalsMigrated; // as long as not migrated, we allow globals
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
            isSelectMode: false,
          };
          r.push(grp);
        }

        const pair: ParameterPair = {
          descriptor: pd,
          value: null,
          editorEnabled: true, // used to lock once custom editor is loaded.
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
        isSelectMode: false,
      };
      // eslint-disable-next-line no-unsafe-optional-chaining
      for (const pv of process.start?.parameters) {
        if (
          !app.descriptor?.startCommand?.parameters?.find((d) => d.id === pv.id)
        ) {
          // no descriptor -> custom
          this.custom.pairs.push({
            descriptor: null,
            value: pv,
            editorEnabled: true,
          });
        }
      }

      // *always* add custom parameters, even if none are there yet to allow adding some. custom params are even after ungrouped ones.
      r.push(this.custom);

      this.groups$.next(r);
      this.completions = this.buildCompletions();
    });

    this.subscription.add(
      this.groups$.subscribe(() => setTimeout(() => this.checkFormsStatus()))
    );

    this.subscription.add(
      this.updatePreview$
        .pipe(
          skipWhile((x) => !x),
          debounceTime(500)
        )
        .subscribe(() => this.preview.update())
    );

    this.subscription.add(
      bop.observe('(max-width: 800px)').subscribe((bs) => {
        this.narrow$.next(bs.matches);
      })
    );

    this.subscription.add(
      combineLatest([instances.state$, systems.systems$]).subscribe(
        ([i, s]) => {
          this.instance = i?.config;

          if (!i?.config?.config?.system || !s?.length) {
            this.system = null;
            this.completions = this.buildCompletions();
            return;
          }

          this.system = s.find(
            (x) =>
              x.key.name === i.config.config.system.name &&
              x.key.tag === i.config.config.system.tag
          )?.config;
          this.completions = this.buildCompletions();
        }
      )
    );

    this.subscription.add(this.searchService.register(this));
  }

  private buildCompletions(): ContentCompletion[] {
    return buildCompletions(
      this.completionPrefixes,
      this.instance,
      this.system,
      this.process,
      this.instances.stateApplications$.value
    );
  }

  private checkFormsStatus() {
    if (!this.formsLoaded && this.forms && this.forms.length) {
      this.forms.forEach((form) => {
        this.subscription.add(
          form.statusChanges
            .pipe(debounceTime(100))
            .subscribe(() => this.emitIsInvalid())
        );
      });
      this.formsLoaded = true;
      this.emitIsInvalid();
    }
  }

  private emitIsInvalid() {
    this.checkIsInvalid.emit(
      this.forms.some((form) => !form.valid && !form.disabled)
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  bdOnSearch(s: string) {
    this.search = !s?.length ? null : s;
  }

  /* template */ getValueCount(g: ParameterGroup) {
    return g.pairs.filter((p) => !!p.value && this.hasPairSearchMatch(p))
      .length;
  }

  /* template */ doAddRemoveParameter(g: ParameterGroup, p: ParameterPair) {
    const paramList = this.edit.process$.value.start.parameters;
    if (!p.value) {
      const descriptors =
        this.edit.application$.value.descriptor.startCommand.parameters;

      let initialValue = p.descriptor?.defaultValue;
      if (p.descriptor.global && this.globalsAllowed) {
        // need to lookup a potential already existing global value.
        const global = this.edit.getGlobalParameter(p.descriptor.id);
        if (global) {
          initialValue = this.edit.getGlobalParameter(p.descriptor.id).value;
        }
      }

      // create the new parameter.
      p.value = {
        id: p.descriptor.id,
        uid: p.descriptor.id, // compat
        value: initialValue,
        pinned: false,
        preRendered: [],
      };
      this.doPreRender(p);

      // the correct insertion point is *before* the *succeeding* parameter definition, as custom parameters may succedd the *preceeding* one.
      const myIndex = descriptors.findIndex((x) => x.id === p.descriptor.id);

      // find the next descriptor which *has* a value *after* my own desciptor.
      const nextDesc = descriptors.find(
        (v, i) => i > myIndex && !!paramList.find((x) => x.id === v.id)
      );

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
        const index = this.custom.pairs.findIndex(
          (x) => x.value.id === p.value.id
        );
        this.custom.pairs.splice(index, 1);

        // if this is the last parameter, leave select mode.
        if (!this.custom.pairs.length) {
          this.custom.isSelectMode = false;
        }
      }

      p.value = null;
    }
    this.updatePreview$.next(true);

    // rebuild completions if parameters changed.
    this.completions = this.buildCompletions();
  }

  /* template */ getAllValueIds() {
    return this.edit.process$.value.start.parameters
      .map((x) => x.id)
      .filter((id) => !this.customTemp.isEdit || id !== this.customTemp.id);
  }

  /* template */ getAllValueIdLabels() {
    return this.getAllValueIds().map(
      (u) =>
        this.edit.application$.value.descriptor.startCommand.parameters.find(
          (x) => x.id === u
        )?.name || u
    );
  }

  /* template */ onEditCustomParameter(
    param: ParameterPair,
    template: TemplateRef<any>
  ) {
    // may not be (and is not) called for linkExpression (different editor).
    const parameters = this.edit.process$.value.start.parameters;
    const paramIndex = parameters.findIndex((p) => p.id === param.value.id);
    this.customTemp = {
      predecessor: paramIndex > 0 ? parameters[paramIndex - 1].id : null,
      id: param.value.id,
      value: param.value.value.value,
      isEdit: true,
    };
    this.dialog
      .message({
        header: 'Edit Custom Parameter',
        template,
        actions: [ACTION_CANCEL, ACTION_OK],
        validation: () => {
          if (this.validateCustomFields.length < 1) {
            return false;
          }
          return this.validateCustomFields
            .map((ctrl) => ctrl.valid || ctrl.disabled)
            .reduce((p, c) => p && c, true);
        },
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

  /* template */ onAddCustomParameter(template: TemplateRef<any>) {
    this.customTemp = {
      predecessor: null,
      id: null,
      value: null,
      isEdit: false,
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
          return this.validateCustomFields
            .map((ctrl) => ctrl.valid)
            .reduce((p, c) => p && c, true);
        },
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
    const pairIndex = this.custom.pairs.findIndex(
      (pair) => pair.value.id === id
    );
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
        uid: this.customTemp.id, // compat
        value: createLinkedValue(this.customTemp.value),
        pinned: false,
        preRendered: [],
      },
      editorEnabled: true,
    };
    this.doPreRender(param);
    this.custom.pairs.push(param);

    if (!this.customTemp.predecessor) {
      this.edit.process$.value.start.parameters.unshift(param.value);
    } else {
      const predecessorIndex =
        this.edit.process$.value.start.parameters.findIndex(
          (p) => p.id === this.customTemp.predecessor
        );
      if (
        predecessorIndex ===
        this.edit.process$.value.start.parameters.length - 1
      ) {
        // last parameter
        this.edit.process$.value.start.parameters.push(param.value);
      } else {
        this.edit.process$.value.start.parameters.splice(
          predecessorIndex + 1,
          0,
          param.value
        );
      }
    }
  }

  /* template */ getDefaultAsString(val: LinkedValueConfiguration) {
    return getPreRenderable(val);
  }

  /* template */ doChangeParam(
    p: ParameterPair,
    val: LinkedValueConfiguration
  ) {
    p.value.value = val;
    this.doPreRender(p);

    // need to make sure we add/remove parameters which meet/don't meet their condition.
    this.doUpdateConditionals(p);
  }

  /* template */ doTogglePin(p: ParameterPair) {
    p.value.pinned = !p.value.pinned;
  }

  private doUpdateConditionals(p: ParameterPair) {
    const id = p.descriptor ? p.descriptor.id : p.value.id;
    for (const grp of this.groups$.value) {
      for (const pair of grp.pairs) {
        // in case the value of a parameter is *referencing* this parameter, we need to update conditionals for the other parameter as well.
        if (
          pair.value?.value?.linkExpression &&
          pair.value?.value?.linkExpression?.indexOf(':' + id + '}}') > 0
        ) {
          // MIGHT be a reference to another application, but we update just in case.
          this.doUpdateConditionals(pair);
        }
      }
      for (const pair of grp.pairs) {
        if (pair.descriptor?.condition?.parameter === id) {
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

  /* template */ doPreRender(p: ParameterPair) {
    p.value.preRendered = this.edit.preRenderParameter(
      p.descriptor,
      p.value.value
    );
    this.updatePreview$.next(true);
  }

  /* template */ doSetCustomEditorState(
    p: ParameterPair,
    editor: CustomEditor
  ) {
    p.editorEnabled = editor.allowDirectEdit;
  }

  /* template */ hasOptionals(g: ParameterGroup) {
    if (g.isCustom) {
      return !!g.pairs.length;
    }

    return (
      g.pairs
        .filter((p) => !!p.descriptor)
        .map((p) => p.descriptor.mandatory)
        .filter((m) => !m).length > 0
    );
  }

  /* template */ hasPairSearchMatch(p: ParameterPair): boolean {
    if (!this.search) {
      return true;
    }

    // search name, description, parameter, value.
    return (
      [
        p.descriptor?.name,
        p.descriptor?.longDescription,
        p.descriptor?.parameter,
        p.value?.value,
      ]
        .join(' ')
        .toLowerCase()
        .indexOf(this.search.toLowerCase()) !== -1
    );
  }

  /* template */ hasGroupSearchMatch(g: ParameterGroup): boolean {
    if (!this.search) {
      return true;
    }

    for (const p of g.pairs) {
      if (this.hasPairSearchMatch(p)) {
        return true;
      }
    }
  }

  /* template */ canAddRemove(param: ParameterPair): Observable<boolean> {
    if (!param.value) {
      return this.edit.meetsConditionOnCurrent(param.descriptor);
    }

    if (param?.descriptor?.mandatory) {
      // mandatory, cannot manually add/remove. triggering the condition on another parameter will automatically add/remove.
      return this.edit
        .meetsConditionOnCurrent(param.descriptor)
        .pipe(map((b) => !b));
    } else {
      // optional
      return of(true);
    }
  }

  /* template */ sortPairs(pairs: ParameterPair[]) {
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

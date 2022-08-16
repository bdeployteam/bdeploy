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
import { debounceTime, skipWhile } from 'rxjs/operators';
import {
  ApplicationConfiguration,
  ApplicationDto,
  CustomEditor,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  ParameterConfiguration,
  ParameterDescriptor,
  ParameterType,
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
  getRenderPreview,
} from 'src/app/modules/core/utils/linked-values.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { ProcessEditService } from '../../../../services/process-edit.service';
import { HistoryProcessConfigComponent } from '../../../history-process-config/history-process-config.component';

const UNGROUPED = 'Ungrouped Parameters';
const CUSTOM = 'Custom Parameters';

interface ParameterPair {
  descriptor: ParameterDescriptor;
  value: ParameterConfiguration;

  booleanValue: boolean;
  passwordLock: boolean;
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
    uid: string;
    value: string;
  };

  private updatePreview$ = new BehaviorSubject<boolean>(false);
  private subscription: Subscription;
  private formsLoaded = false;

  constructor(
    public edit: ProcessEditService,
    private instanceEdit: InstanceEditService,
    bop: BreakpointObserver,
    private searchService: SearchService,
    systems: SystemsService,
    instances: InstanceEditService
  ) {
    this.subscription = combineLatest([
      this.edit.process$,
      this.edit.application$,
    ]).subscribe(([process, app]) => {
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
            isSelectMode: false,
          };
          r.push(grp);
        }

        const pair: ParameterPair = {
          descriptor: pd,
          value: null,
          booleanValue: null,
          passwordLock: pd.type === ParameterType.PASSWORD,
          editorEnabled: true, // used to lock once custom editor is loaded.
        };
        grp.pairs.push(pair);

        // check if the param has a value (yet) - if yes, push it now to have the order right within the group (same as descriptor order).
        const val = process.start?.parameters?.find((p) => p.uid === pd.uid);
        if (val) {
          pair.value = val;
          if (pd.type === ParameterType.BOOLEAN) {
            pair.booleanValue = val.value.value === 'true';
          }
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
          !app.descriptor?.startCommand?.parameters?.find(
            (d) => d.uid === pv.uid
          )
        ) {
          // no descriptor -> custom
          this.custom.pairs.push({
            descriptor: null,
            value: pv,
            booleanValue: null,
            passwordLock: false,
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
      this.instanceEdit.stateApplications$.value
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
      if (p.descriptor.global) {
        // need to lookup a potential already existing global value.
        const global = this.edit.getGlobalParameter(p.descriptor.uid);
        if (global) {
          initialValue = this.edit.getGlobalParameter(p.descriptor.uid).value;
        }
      }

      // create the new parameter.
      p.value = {
        uid: p.descriptor.uid,
        value: initialValue,
        pinned: false,
        preRendered: [],
      };
      this.doPreRender(p);

      // the correct insertion point is *before* the *succeeding* parameter definition, as custom parameters may succedd the *preceeding* one.
      const myIndex = descriptors.findIndex((x) => x.uid === p.descriptor.uid);

      // find the next descriptor which *has* a value *after* my own desciptor.
      const nextDesc = descriptors.find(
        (v, i) => i > myIndex && !!paramList.find((x) => x.uid === v.uid)
      );

      // if we don't have a next one, simply push it at the end of the list.
      if (!nextDesc) {
        paramList.push(p.value);
      } else {
        paramList.splice(
          paramList.findIndex((x) => x.uid === nextDesc.uid),
          0,
          p.value
        );
      }
    } else {
      paramList.splice(
        paramList.findIndex((x) => x.uid === p.value.uid),
        1
      );

      if (g.isCustom) {
        // COMPLETELY remove a custom parameter.
        const index = this.custom.pairs.findIndex(
          (x) => x.value.uid === p.value.uid
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

  /* template */ getAllValueUids() {
    return this.edit.process$.value.start.parameters
      .map((x) => x.uid)
      .filter((uid) => !this.customTemp.isEdit || uid !== this.customTemp.uid);
  }

  /* template */ getAllValueUidLabels() {
    return this.getAllValueUids().map(
      (u) =>
        this.edit.application$.value.descriptor.startCommand.parameters.find(
          (x) => x.uid === u
        )?.name || u
    );
  }

  /* template */ onEditCustomParameter(
    param: ParameterPair,
    template: TemplateRef<any>
  ) {
    // may not be (and is not) called for linkExpression (different editor).
    const parameters = this.edit.process$.value.start.parameters;
    const paramIndex = parameters.findIndex((p) => p.uid === param.value.uid);
    this.customTemp = {
      predecessor: paramIndex > 0 ? parameters[paramIndex - 1].uid : null,
      uid: param.value.uid,
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
          this.deleteCustomParameter(this.customTemp.uid);
          // insert updated parameter at the correct position.
          this.insertCustomParameterAtCorrectPosition();
        }
        this.customTemp = null;
      });
  }

  /* template */ onAddCustomParameter(template: TemplateRef<any>) {
    this.customTemp = {
      predecessor: null,
      uid: null,
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

  private deleteCustomParameter(uid: string) {
    const pairIndex = this.custom.pairs.findIndex(
      (pair) => pair.value.uid === uid
    );
    if (pairIndex >= 0) {
      this.custom.pairs.splice(pairIndex, 1);
    }
    const parameters = this.edit.process$.value.start.parameters;
    const paramIndex = parameters.findIndex((p) => p.uid === uid);
    if (paramIndex >= 0) {
      parameters.splice(paramIndex, 1);
    }
  }

  private insertCustomParameterAtCorrectPosition() {
    const param: ParameterPair = {
      descriptor: null,
      value: {
        uid: this.customTemp.uid,
        value: createLinkedValue(this.customTemp.value),
        pinned: false,
        preRendered: [],
      },
      booleanValue: false,
      editorEnabled: true,
      passwordLock: false,
    };
    this.doPreRender(param);
    this.custom.pairs.push(param);

    if (!this.customTemp.predecessor) {
      this.edit.process$.value.start.parameters.unshift(param.value);
    } else {
      const predecessorIndex =
        this.edit.process$.value.start.parameters.findIndex(
          (p) => p.uid === this.customTemp.predecessor
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
    p.value.value.value = val.value;
    p.value.value.linkExpression = val.linkExpression;
    this.doPreRender(p);

    // need to make sure we add/remove parameters which meet/don't meet their condition.
    this.doUpdateConditionals(p);
  }

  /* template */ doChangeValue(p: ParameterPair, val: string) {
    let reset = false;

    // check type;
    if (p.descriptor) {
      switch (p.descriptor.type) {
        case ParameterType.BOOLEAN:
          if (val !== 'true' && val !== 'false') {
            console.log(
              `Parameter ${p.descriptor.name} is a boolean, but the value is not true or false, resetting to default.`
            );
            reset = true;
          }
          break;
        case ParameterType.NUMERIC:
        case ParameterType.CLIENT_PORT:
        case ParameterType.SERVER_PORT:
          if (isNaN(Number(val))) {
            console.log(
              `Parameter ${p.descriptor.name} value is not a number: ${val}, resetting to default.`
            );
            reset = true;
          }
          break;
      }
    }

    if (reset) {
      p.value.value = p.descriptor.defaultValue; // descriptor cannot be null;
    } else {
      p.value.value = { value: val, linkExpression: null };
    }
    this.doPreRender(p);

    // need to make sure we add/remove parameters which meet/don't meet their condition.
    this.doUpdateConditionals(p);
  }

  /* template */ doChangeLink(p: ParameterPair, val: string) {
    p.value.value = { value: null, linkExpression: val ? val : '' };
    this.doPreRender(p);

    // need to make sure we add/remove parameters which meet/don't meet their condition.
    this.doUpdateConditionals(p);
  }

  /* template */ doTogglePin(p: ParameterPair) {
    p.value.pinned = !p.value.pinned;
  }

  private doUpdateConditionals(p: ParameterPair) {
    const uid = p.descriptor ? p.descriptor.uid : p.value.uid;
    for (const grp of this.groups$.value) {
      for (const pair of grp.pairs) {
        // in case the value of a parameter is *referencing* this parameter, we need to update conditionals for the other parameter as well.
        if (
          pair.value?.value?.linkExpression &&
          pair.value?.value?.linkExpression?.indexOf(':' + uid + '}}') > 0
        ) {
          // MIGHT be a reference to another application, but we update just in case.
          this.doUpdateConditionals(pair);
        }
      }
      for (const pair of grp.pairs) {
        if (pair.descriptor?.condition?.parameter === uid) {
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

  /* template */ doPreRenderBoolean(p: ParameterPair) {
    p.value.value = createLinkedValue(!p.booleanValue ? 'false' : 'true');
    this.doPreRender(p);

    this.doUpdateConditionals(p);
  }

  /* template */ doSetCustomEditorState(
    p: ParameterPair,
    editor: CustomEditor
  ) {
    p.editorEnabled = editor.allowDirectEdit;
  }

  /* template */ isBoolean(p: ParameterPair) {
    return p.descriptor?.type === ParameterType.BOOLEAN;
  }

  /* template */ isPort(p: ParameterPair) {
    return (
      p.descriptor?.type === ParameterType.CLIENT_PORT ||
      p.descriptor?.type === ParameterType.SERVER_PORT
    );
  }

  /* template */ getInputType(p: ParameterPair) {
    if (!p?.descriptor?.type) {
      return undefined;
    }
    switch (p.descriptor.type) {
      case ParameterType.CLIENT_PORT:
      case ParameterType.SERVER_PORT:
      case ParameterType.NUMERIC:
        return 'number';
      case ParameterType.PASSWORD:
        return 'password';
    }
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
      return of(false);
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

      const ida = a.descriptor?.uid ? a.descriptor.uid : a.value.uid;
      const idb = b.descriptor?.uid ? b.descriptor.uid : b.value.uid;

      return ida.localeCompare(idb);
    });
  }

  /* template */ makeValueLink(p: ParameterPair) {
    if (p.descriptor?.type === ParameterType.PASSWORD) {
      this.doChangeLink(p, ''); // DON'T ever apply a password to the plain text editor, rather clear it.
    }
    this.doChangeLink(p, p.value?.value?.value);
  }

  /* template */ makeValuePlain(p: ParameterPair) {
    if (p.descriptor?.type === ParameterType.PASSWORD) {
      this.doChangeValue(p, '');
      return;
    }

    if (p.value?.value?.linkExpression?.indexOf('{{') >= 0) {
      this.doChangeValue(
        p,
        getRenderPreview(
          p.value.value,
          this.process,
          this.instance,
          this.system
        )
      );
    } else {
      this.doChangeValue(p, p.value?.value?.linkExpression);
    }
  }

  /* template */ isLink(p: ParameterPair) {
    return (
      p.value?.value?.linkExpression !== null &&
      p.value?.value?.linkExpression !== undefined
    );
  }

  /* template */ appendLink(p: ParameterPair, v: string) {
    this.doChangeLink(p, p.value?.value?.linkExpression + v);
  }
}

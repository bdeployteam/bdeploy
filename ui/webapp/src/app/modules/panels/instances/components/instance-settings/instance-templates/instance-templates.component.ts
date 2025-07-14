import { Component, inject, OnDestroy, OnInit, signal, TemplateRef, ViewChild } from '@angular/core';
import { MatStep, MatStepper } from '@angular/material/stepper';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, Observable, of, Subscription } from 'rxjs';
import { concatAll, finalize, first, map, skipWhile } from 'rxjs/operators';
import { StatusMessage } from 'src/app/models/config.model';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import {
  ApplicationType,
  FlattenedApplicationTemplateConfiguration,
  FlattenedInstanceTemplateConfiguration,
  FlattenedInstanceTemplateGroupConfiguration,
  InstanceNodeConfigurationDto,
  ProcessControlGroupConfiguration,
  ProductDto,
  TemplateVariable,
  TemplateVariableType,
} from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { createLinkedValue, getPreRenderable } from 'src/app/modules/core/utils/linked-values.utils';
import { getAppKeyName, getTemplateAppKey } from 'src/app/modules/core/utils/manifest.utils';
import { performTemplateVariableSubst } from 'src/app/modules/core/utils/object.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ProcessEditService } from '../../../services/process-edit.service';
import { TemplateMessageDetailsComponent } from './template-message-details/template-message-details.component';
import { BdDataTableComponent } from '../../../../../core/components/bd-data-table/bd-data-table.component';

import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormSelectComponent } from '../../../../../core/components/bd-form-select/bd-form-select.component';
import { FormsModule } from '@angular/forms';
import { MatTooltip } from '@angular/material/tooltip';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { BdFormTemplateVariableComponent } from '../../../../../core/components/bd-form-template-variable/bd-form-template-variable.component';
import { BdNoDataComponent } from '../../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

export interface TemplateMessage {
  group: string;
  node: string;
  appname: string;
  template: FlattenedApplicationTemplateConfiguration;
  message: StatusMessage;
}

const tplColName: BdDataColumn<TemplateMessage, string> = {
  id: 'name',
  name: 'Name',
  data: (r) => (r.appname ? r.appname : `${r.group}/${r.node}`),
};

const tplColDetails: BdDataColumn<TemplateMessage, string> = {
  id: 'details',
  name: 'Details',
  data: (r) => r.message.message,
  component: TemplateMessageDetailsComponent,
  width: '36px',
};

@Component({
  selector: 'app-instance-templates',
  templateUrl: './instance-templates.component.html',
  styleUrls: ['./instance-templates.component.css'],
  imports: [
    BdDataTableComponent,
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    MatStepper,
    MatStep,
    BdFormSelectComponent,
    FormsModule,
    MatTooltip,
    BdButtonComponent,
    BdFormTemplateVariableComponent,
    BdNoDataComponent,
    AsyncPipe,
  ],
})
export class InstanceTemplatesComponent implements OnInit, OnDestroy {
  private readonly products = inject(ProductsService);
  private readonly edit = inject(ProcessEditService);
  private readonly instanceEdit = inject(InstanceEditService);
  protected readonly servers = inject(ServersService);

  protected readonly msgColumns: BdDataColumn<TemplateMessage, unknown>[] = [tplColName, tplColDetails];

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected messages: TemplateMessage[];

  protected instanceTemplates: FlattenedInstanceTemplateConfiguration[];
  protected instanceTemplateLabels: string[];

  protected template: FlattenedInstanceTemplateConfiguration;
  protected groupNodes: Record<string, string[]>;
  protected groupLabels: Record<string, string[]>;

  protected groups: Record<string, string>; // key is group name, value is target node name
  protected areAssignedGroupsValid = false;
  protected groupSelectionNextButtonTooltip = signal<string>(null);
  protected finalConfirmButtonTooltip = signal<string>(null);

  protected allRequiredVariables: TemplateVariable[] = [];
  protected variables: Record<string, string>; // key is var name, value is value
  protected hasAllRequiredVariables = false;

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild('msgTemplate') private readonly tplMessages: TemplateRef<unknown>;
  @ViewChild('stepper', { static: false }) protected readonly stepper: MatStepper;

  private subscription: Subscription;
  private product: ProductDto;
  private directlyUsedVariables: TemplateVariable[] = [];

  ngOnInit() {
    this.subscription = combineLatest([this.instanceEdit.state$, this.products.products$]).subscribe(
      ([state, prods]) => {
        const prod = prods?.find(
          (p) => p.key.name === state?.config?.config?.product.name && p.key.tag === state?.config?.config?.product.tag
        );

        if (!prod) {
          this.instanceTemplates = [];
          return;
        }

        this.product = prod;
        this.instanceTemplates = prod.instanceTemplates ? prod.instanceTemplates : [];
        this.instanceTemplateLabels = this.instanceTemplates.map((record) => record.name);
      }
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected selectTemplate() {
    this.groupNodes = {};
    this.groupLabels = {};
    this.template.groups.forEach((group) => {
      this.groupNodes[group.name] =
        group.type === ApplicationType.CLIENT
          ? [null, CLIENT_NODE_NAME]
          : [
              null,
              ...this.instanceEdit.state$.value.config.nodeDtos
                .map((nodeDto) => nodeDto.nodeName)
                .filter((nodeName) => nodeName !== CLIENT_NODE_NAME),
            ];
      this.groupLabels[group.name] = this.groupNodes[group.name].map((n) => {
        switch (n) {
          case null:
            return '(skip)';
          case CLIENT_NODE_NAME:
            return 'Apply to Client Applications';
          default:
            return 'Apply to ' + n;
        }
      });
    });

    this.groups = {};
    this.areAssignedGroupsValid = false;
    this.groupSelectionNextButtonTooltip.set('At least 1 group must be selected');

    this.directlyUsedVariables = [];
    if (this.template.directlyUsedTemplateVars?.length) {
      this.directlyUsedVariables.push(...this.template.directlyUsedTemplateVars);
    }
    this.allRequiredVariables = [];
    this.variables = {};
    this.hasAllRequiredVariables = false;

    this.stepper.selected.completed = !!this.template;
    this.stepper.next();
  }

  protected assignGroup() {
    let anyGroupAssigned = false;
    const invalidGroups: string[] = [];
    for (const k of Object.keys(this.groups)) {
      const v = this.groups[k];
      if (v !== null && v !== undefined) {
        anyGroupAssigned = true;

        // check if group contains only valid applications
        appLoop: for (const app of this.template.groups.find((group) => group.name === k).applications) {
          const processControlConfig = app.processControl;
          if (!processControlConfig) {
            continue;
          }

          // need to find all apps in the product which match the key name...
          const searchKey = this.product.product + '/' + app.application;
          for (const app of this.instanceEdit.stateApplications$.value) {
            const appKey = getAppKeyName(app.key);
            if (searchKey === appKey) {
              const processControlDescriptor = app?.descriptor?.processControl;
              if (!processControlDescriptor) {
                continue;
              }
              if (
                (processControlConfig['autostart'] && !processControlDescriptor.supportsAutostart) ||
                (processControlConfig['keepAlive'] && !processControlDescriptor.supportsKeepAlive)
              ) {
                invalidGroups.push(k);
                break appLoop;
              }
            }
          }
        }
      }
    }

    if (!anyGroupAssigned) {
      this.areAssignedGroupsValid = false;
      this.groupSelectionNextButtonTooltip.set('At least 1 group must be selected');
    } else if (invalidGroups.length !== 0) {
      this.areAssignedGroupsValid = false;
      this.groupSelectionNextButtonTooltip.set(
        invalidGroups.length === 1
          ? "The group '" +
              invalidGroups[0] +
              "' contains invalid application templates and therefore cannot be selected"
          : "The groups '" +
              invalidGroups.join("', '") +
              "' contain invalid application templates and therefore cannot be selected"
      );
    } else {
      this.groupSelectionNextButtonTooltip.set('Proceed to variable configuration');
      this.areAssignedGroupsValid = true;
    }

    this.allRequiredVariables = [...this.directlyUsedVariables];
    for (const grp of Object.keys(this.groups)) {
      const grpDef = this.template.groups.find((g) => g.name === grp);
      if (!grpDef || !this.groups[grp] || !grpDef.groupVariables?.length) {
        continue;
      }
      for (const v of grpDef.groupVariables) {
        if (this.allRequiredVariables.findIndex((t) => t.id === v.id) === -1) {
          this.allRequiredVariables.push(v);
        }
      }
    }

    this.variables = {};

    for (const requiredVariable of this.allRequiredVariables) {
      this.variables[requiredVariable.id] = requiredVariable.defaultValue;
      if (requiredVariable.defaultValue === null && requiredVariable.type === TemplateVariableType.BOOLEAN) {
        this.variables[requiredVariable.id] = 'false';
      }
    }
    this.checkVariables();
  }

  protected checkVariables() {
    for (const requiredVariable of this.allRequiredVariables) {
      const value = this.variables[requiredVariable.id];
      if (value === '' || value === null || value === undefined) {
        this.hasAllRequiredVariables = false;
        this.finalConfirmButtonTooltip.set(`Required variable '${requiredVariable.name}' is still missing`);
        return;
      }
    }
    this.finalConfirmButtonTooltip.set('Apply template with current configuration');
    this.hasAllRequiredVariables = true;
  }

  protected applyStageFinal() {
    this.loading$.next(true);
    this.messages = [];
    const observables: Observable<string>[] = [];

    // prepare available process control groups
    const pcgs = this.template.processControlGroups.map(
      (p) => Object.assign({}, p) as ProcessControlGroupConfiguration
    );
    pcgs.forEach((p) => (p.processOrder = []));

    const instance = this.instanceEdit.state$.value.config.config;
    if (!instance.instanceVariables) {
      instance.instanceVariables = [];
    }

    // apply instance variable values if set.
    if (this.template.instanceVariableValues?.length) {
      for (const v of this.template.instanceVariableValues) {
        const status: StatusMessage[] = [];
        const existingVariable = instance.instanceVariables.find((iv) => iv.id === v.id);
        if (existingVariable) {
          existingVariable.value = createLinkedValue(
            performTemplateVariableSubst(getPreRenderable(v.value), this.variables, status)
          );
          status.forEach((e) =>
            this.messages.push({
              group: 'Global',
              node: 'Global',
              appname: null,
              template: null,
              message: e,
            })
          );
        } else {
          this.messages.push({
            group: 'Global',
            node: 'Global',
            appname: null,
            template: null,
            message: {
              icon: 'error',
              message: `Cannot set instance variable value for ${v.id}. Instance variable not found.`,
            },
          });
        }
      }
    }

    // apply instance variables if set.
    if (this.template.instanceVariables?.length) {
      for (const v of this.template.instanceVariables) {
        const processed = cloneDeep(v);
        const status: StatusMessage[] = [];
        processed.value = createLinkedValue(
          performTemplateVariableSubst(getPreRenderable(processed.value), this.variables, status)
        );
        status.forEach((e) =>
          this.messages.push({
            group: 'Global',
            node: 'Global',
            appname: null,
            template: null,
            message: e,
          })
        );

        const index = instance.instanceVariables.findIndex((x) => x.id === v.id);
        if (index !== -1) {
          // replace.
          instance.instanceVariables.splice(index, 1, processed);
        } else {
          instance.instanceVariables.push(processed);
        }
      }

      instance.instanceVariables.sort((a, b) => a.id.localeCompare(b.id));
    }

    for (const groupName of Object.keys(this.groups)) {
      const nodeName = this.groups[groupName];
      if (!nodeName) {
        continue; // skipped.
      }

      const node = this.instanceEdit.state$.value?.config?.nodeDtos?.find((n) => n.nodeName === nodeName);
      const group = this.template.groups.find((g) => g.name === groupName);

      if (!node || !group) {
        this.messages.push({
          group: groupName,
          node: nodeName,
          appname: null,
          template: null,
          message: { icon: 'warning', message: 'Cannot find node or group' },
        });
        continue;
      }

      // not set or SERVER
      if (group.type !== ApplicationType.CLIENT) {
        // for servers, we need to find the appropriate application with the correct OS.
        this.applyServerGroup(group, node, pcgs, groupName, nodeName, observables);
      } else {
        // for clients we add all matches, regardless of the OS.
        this.applyClientGroup(group, observables, node, groupName, nodeName);
      }
    }

    if (!observables.length) {
      return; // nothing to do...?
    }

    const templateName = this.template.name; // will be reset in the process.

    // now execute and await all additions.
    combineLatest(observables)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe(() => {
        this.instanceEdit.state$.value?.config?.nodeDtos.forEach((n) => {
          // Removes empty process control groups from the configuration
          n.nodeConfiguration.controlGroups = n.nodeConfiguration.controlGroups.filter((x) => !!x.processOrder?.length);
        });
        let applyResult = of(true);
        // now if we DO have messages, we want to show them to the user.
        if (this.messages.length) {
          applyResult = this.dialog.message({
            header: 'Template Messages',
            template: this.tplMessages,
            actions: [ACTION_CANCEL, ACTION_OK],
          });
        }

        applyResult.subscribe((r) => {
          if (r) {
            this.instanceEdit.conceal(`Apply instance template ${templateName}`);
          } else {
            this.instanceEdit.discard();
          }
          this.tb.closePanel();
        });
      });
  }

  private applyClientGroup(
    group: FlattenedInstanceTemplateGroupConfiguration,
    observables: Observable<string>[],
    node: InstanceNodeConfigurationDto,
    groupName: string,
    nodeName: string
  ) {
    for (const template of group.applications) {
      // need to find all apps in the product which match the key name...
      const searchKey = this.product.product + '/' + template.application;
      const status: StatusMessage[] = [];
      for (const app of this.instanceEdit.stateApplications$.value) {
        const appKey = getAppKeyName(app.key);
        if (searchKey === appKey) {
          observables.push(
            this.edit.addProcess(node, app, template, this.variables, status).pipe(
              finalize(() => {
                status.forEach((e) =>
                  this.messages.push({
                    group: groupName,
                    node: nodeName,
                    appname: template?.name ? template.name : app.name,
                    template: template,
                    message: e,
                  })
                );
              })
            )
          );
        }
      }
    }
  }

  private applyServerGroup(
    group: FlattenedInstanceTemplateGroupConfiguration,
    nodeCfg: InstanceNodeConfigurationDto,
    pcgs: ProcessControlGroupConfiguration[],
    groupName: string,
    nodeName: string,
    observables: Observable<string>[]
  ) {
    for (const app of group.applications) {
      // need to prepare process control groups synchronously before adding applications.
      this.prepareProcessControlGroups(app, nodeCfg, pcgs, groupName, nodeName);

      observables.push(
        this.instanceEdit.nodes$.pipe(
          // wait for node information
          skipWhile((n) => !n),
          // pick the first valid node info
          first(),
          // map the node info to the application key we need for our node.
          map((n) => {
            return {
              node: n[nodeName],
              appDto: this.instanceEdit.stateApplications$.value?.find(
                (a) => a.key.name === getTemplateAppKey(this.product, app, n[nodeName])
              ),
            };
          }),
          // map the key of the app to an observable to actually add the application if possible.
          map(({ node, appDto }) => {
            if (app.applyOn?.length && !app.applyOn.includes(node.os)) {
              // skip application, not wanted on this OS
              return of<string>(null);
            }

            if (!appDto) {
              this.messages.push({
                group: groupName,
                node: nodeName,
                template: app,
                appname: app?.name ? app.name : app.application,
                message: {
                  icon: 'warning',
                  message: 'Cannot find application in product for target OS.',
                },
              });
              return of<string>(null);
            } else {
              const status: StatusMessage[] = [];
              return this.edit.addProcess(nodeCfg, appDto, app, this.variables, status).pipe(
                finalize(() => {
                  status.forEach((e) =>
                    this.messages.push({
                      group: groupName,
                      node: nodeName,
                      appname: app?.name ? app.name : appDto.name,
                      template: app,
                      message: e,
                    })
                  );
                })
              );
            }
          }),
          // since adding returns an observable we concat them, so a subscription to the observable will yield the addProcess response.
          concatAll()
        )
      );
    }
  }

  /**
   * Prepare process control groups for the given application on the node.
   */
  private prepareProcessControlGroups(
    app: FlattenedApplicationTemplateConfiguration,
    node: InstanceNodeConfigurationDto,
    pcgs: ProcessControlGroupConfiguration[],
    groupName: string,
    nodeName: string
  ) {
    const cg = app.preferredProcessControlGroup;
    if (cg) {
      const existingCg = node.nodeConfiguration.controlGroups.find((n) => n.name === cg);
      if (!existingCg) {
        // need to prepare the group.
        const pcgTempl = pcgs.find((p) => p.name === cg);
        if (!pcgTempl) {
          this.messages.push({
            group: groupName,
            node: nodeName,
            appname: null,
            template: null,
            message: {
              icon: 'warning',
              message: `Cannot find template for requested process control group ${cg}`,
            },
          });
        } else {
          // TODO: check order of groups...? Which order is relevant? Order of groups in template?
          node.nodeConfiguration.controlGroups.push(pcgTempl);
        }
      }
    }
  }
}

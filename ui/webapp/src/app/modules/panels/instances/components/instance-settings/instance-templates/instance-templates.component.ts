import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription, combineLatest, of } from 'rxjs';
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

export interface TemplateMessage {
  group: string;
  node: string;
  appname: string;
  template: FlattenedApplicationTemplateConfiguration;
  message: StatusMessage;
}

const tplColName: BdDataColumn<TemplateMessage> = {
  id: 'name',
  name: 'Name',
  data: (r) => (r.appname ? r.appname : `${r.group}/${r.node}`),
};

const tplColDetails: BdDataColumn<TemplateMessage> = {
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
    standalone: false
})
export class InstanceTemplatesComponent implements OnInit, OnDestroy {
  private readonly products = inject(ProductsService);
  private readonly edit = inject(ProcessEditService);
  protected readonly servers = inject(ServersService);
  protected readonly instanceEdit = inject(InstanceEditService);

  protected loading$ = new BehaviorSubject<boolean>(false);

  protected records: FlattenedInstanceTemplateConfiguration[];
  protected recordsLabel: string[];

  protected template: FlattenedInstanceTemplateConfiguration;
  protected variables: { [key: string]: string }; // key is var name, value is value.
  protected groups: { [key: string]: string }; // key is group name, value is target node name.
  protected messages: TemplateMessage[];
  protected readonly msgColumns: BdDataColumn<TemplateMessage>[] = [tplColName, tplColDetails];
  protected isAnyGroupSelected = false;
  protected hasAllVariables = false;
  protected firstStepCompleted = false;
  protected secondStepCompleted = false;
  protected requiredVariables: TemplateVariable[] = [];

  protected groupNodes: { [key: string]: string[] };
  protected groupLabels: { [key: string]: string[] };

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild('msgTemplate') private readonly tplMessages: TemplateRef<unknown>;
  @ViewChild('stepper', { static: false }) private readonly myStepper: MatStepper;

  private product: ProductDto;
  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([this.instanceEdit.state$, this.products.products$]).subscribe(
      ([state, prods]) => {
        const prod = prods?.find(
          (p) => p.key.name === state?.config?.config?.product.name && p.key.tag === state?.config?.config?.product.tag,
        );

        if (!prod) {
          this.records = [];
          return;
        }

        this.product = prod;
        this.records = prod.instanceTemplates ? prod.instanceTemplates : [];
        this.recordsLabel = this.records.map((record) => record.name);
      },
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private getNodesFor(group: FlattenedInstanceTemplateGroupConfiguration): string[] {
    if (group.type === ApplicationType.CLIENT) {
      return [null, CLIENT_NODE_NAME];
    } else {
      return [
        null,
        ...this.instanceEdit.state$.value.config.nodeDtos.map((n) => n.nodeName).filter((n) => n !== CLIENT_NODE_NAME),
      ];
    }
  }

  private getLabelsFor(group: FlattenedInstanceTemplateGroupConfiguration): string[] {
    const nodeValues = this.getNodesFor(group);

    return nodeValues.map((n) => {
      if (n === null) {
        return '(skip)';
      } else if (n === CLIENT_NODE_NAME) {
        return 'Apply to Client Applications';
      } else {
        return 'Apply to ' + n;
      }
    });
  }

  protected validateAnyGroupSelected() {
    for (const k of Object.keys(this.groups)) {
      const v = this.groups[k];
      if (v !== null && v !== undefined) {
        this.isAnyGroupSelected = true;
        return;
      }
    }
    this.isAnyGroupSelected = false;
  }

  protected validateHasAllVariables() {
    if (!this.template) {
      return;
    }
    for (const v of this.requiredVariables) {
      const value = this.variables[v.id];
      if (value === '' || value === null || value === undefined) {
        this.hasAllVariables = false;
        return;
      }
    }
    this.hasAllVariables = true;
  }

  protected selectTemplate() {
    // setup things required by the templates.
    this.groupNodes = {};
    this.groupLabels = {};
    this.template.groups.forEach((group) => {
      this.groupNodes[group.name] = this.getNodesFor(group);
      this.groupLabels[group.name] = this.getLabelsFor(group);
    });
    this.groups = {};

    this.validateAnyGroupSelected();
    this.firstStepCompleted = true;
    this.goNext();
  }

  protected goToAssignVariableStep() {
    this.secondStepCompleted = true;
    this.goNext();
  }

  private goNext() {
    this.myStepper.selected.completed = true;
    this.myStepper.next();
  }

  protected applyStageFinal() {
    this.loading$.next(true);
    this.messages = [];
    const observables: Observable<any>[] = [];

    // prepare available process control groups
    const pcgs = this.template.processControlGroups.map(
      (p) => Object.assign({}, p) as ProcessControlGroupConfiguration,
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
            performTemplateVariableSubst(getPreRenderable(v.value), this.variables, status),
          );
          status.forEach((e) =>
            this.messages.push({
              group: 'Global',
              node: 'Global',
              appname: null,
              template: null,
              message: e,
            }),
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
          performTemplateVariableSubst(getPreRenderable(processed.value), this.variables, status),
        );
        status.forEach((e) =>
          this.messages.push({
            group: 'Global',
            node: 'Global',
            appname: null,
            template: null,
            message: e,
          }),
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
        this.instanceEdit.state$.value?.config?.nodeDtos.forEach((n) => this.cleanProcessControlGroup(n));

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
    nodeName: string,
  ) {
    for (const template of group.applications) {
      // need to find all apps in the product which match the key name...
      const searchKey = this.product.product + '/' + template.application;
      const status = [];
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
                  }),
                );
              }),
            ),
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
    observables: Observable<string>[],
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
                (a) => a.key.name === getTemplateAppKey(this.product, app, n[nodeName]),
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
              const status = [];
              return this.edit.addProcess(nodeCfg, appDto, app, this.variables, status).pipe(
                finalize(() => {
                  status.forEach((e) =>
                    this.messages.push({
                      group: groupName,
                      node: nodeName,
                      appname: app?.name ? app.name : appDto.name,
                      template: app,
                      message: e,
                    }),
                  );
                }),
              );
            }
          }),
          // since adding returns an observable we concat them, so a subscription to the observable will yield the addProcess response.
          concatAll(),
        ),
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
    nodeName: string,
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

  /**
   * Removes empty process control groups from the configuration.
   */
  private cleanProcessControlGroup(node: InstanceNodeConfigurationDto) {
    node.nodeConfiguration.controlGroups = node.nodeConfiguration.controlGroups.filter((n) => !!n.processOrder?.length);
  }

  protected onStepSelectionChange(event: StepperSelectionEvent) {
    switch (event.selectedIndex) {
      case 0:
        this.groups = {};
        this.firstStepCompleted = false;
        this.secondStepCompleted = false;

        this.template = null;
        this.groupLabels = null;
        this.groupNodes = null;
        break;
      case 1:
        this.secondStepCompleted = false;
        this.requiredVariables = [];
        this.hasAllVariables = false;
        break;
      case 2:
        this.variables = {};
        this.requiredVariables = [];

        if (this.template.directlyUsedTemplateVars?.length) {
          this.requiredVariables.push(...this.template.directlyUsedTemplateVars);
        }

        for (const grp of Object.keys(this.groups)) {
          const grpDef = this.template.groups.find((g) => g.name === grp);
          if (!grpDef || !this.groups[grp] || !grpDef.groupVariables?.length) {
            continue;
          }

          for (const v of grpDef.groupVariables) {
            if (this.requiredVariables.findIndex((t) => t.id === v.id) === -1) {
              // not yet there, add.
              this.requiredVariables.push(v);
            }
          }
        }

        if (this.requiredVariables.length) {
          for (const v of this.requiredVariables) {
            this.variables[v.id] = v.defaultValue;
            if (v.defaultValue === null && v.type === TemplateVariableType.BOOLEAN) {
              this.variables[v.id] = 'false';
            }
          }
          this.validateHasAllVariables();
        } else {
          this.hasAllVariables = true;
        }

        break;
    }
  }
}

import { Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { concatAll, finalize, first, map, skipWhile } from 'rxjs/operators';
import { StatusMessage } from 'src/app/models/config.model';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import { ApplicationType, InstanceTemplateDescriptor, InstanceTemplateGroup, ProductDto, TemplateApplication } from 'src/app/models/gen.dtos';
import { ACTION_CANCEL, ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { getAppKeyName, getTemplateAppKey } from 'src/app/modules/core/utils/manifest.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ProcessEditService } from '../../../services/process-edit.service';
import { TemplateMessageDetailsComponent } from './template-message-details/template-message-details.component';

export interface TemplateMessage {
  group: string;
  node: string;
  appname: string;
  template: TemplateApplication;
  message: StatusMessage;
}

const tplColName: BdDataColumn<TemplateMessage> = {
  id: 'name',
  name: 'Name',
  data: (r) => (!!r.template?.name ? r.template.name : !!r.template?.template ? r.template.template : r.template.application),
};

const tplColDetails: BdDataColumn<TemplateMessage> = {
  id: 'details',
  name: 'Details',
  data: (r) => r.message.message,
  component: TemplateMessageDetailsComponent,
  width: '36px',
};

const colName: BdDataColumn<InstanceTemplateDescriptor> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

@Component({
  selector: 'app-instance-templates',
  templateUrl: './instance-templates.component.html',
  styleUrls: ['./instance-templates.component.css'],
})
export class InstanceTemplatesComponent implements OnInit, OnDestroy {
  private colApply: BdDataColumn<InstanceTemplateDescriptor> = {
    id: 'apply',
    name: 'Apply',
    icon: (r) => 'auto_fix_high',
    action: (r) => this.apply(r),
    data: (r) => r.description,
    width: '36px',
  };

  /* template */ loading$ = new BehaviorSubject<boolean>(false);

  /* template */ records$ = new BehaviorSubject<InstanceTemplateDescriptor[]>([]);
  /* template */ columns: BdDataColumn<InstanceTemplateDescriptor>[] = [colName, this.colApply];

  /* template */ template;
  /* template */ variables: { [key: string]: string }; // key is var name, value is value.
  /* template */ groups: { [key: string]: string }; // key is group name, value is target node name.
  /* template */ messages: TemplateMessage[];
  /* template */ msgColumns: BdDataColumn<TemplateMessage>[] = [tplColName, tplColDetails];
  /* template */ isAnyGroupSelected = false;
  /* template */ hasAllVariables = false;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('msgTemplate') private tplMessages: TemplateRef<any>;
  @ViewChild('stepper', { static: false }) private myStepper: MatStepper;

  private product: ProductDto;
  private subscription: Subscription;

  constructor(public servers: ServersService, public instanceEdit: InstanceEditService, private products: ProductsService, private edit: ProcessEditService) {
    this.subscription = combineLatest([this.instanceEdit.state$, this.products.products$]).subscribe(([state, prods]) => {
      const prod = prods?.find((p) => p.key.name === state?.config?.config?.product.name && p.key.tag === state?.config?.config?.product.tag);

      if (!prod) {
        this.records$.next([]);
        return;
      }

      this.product = prod;
      this.records$.next(prod.instanceTemplates ? prod.instanceTemplates : []);
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private getNodesFor(group: InstanceTemplateGroup): string[] {
    if (group.type === ApplicationType.CLIENT) {
      return [null, CLIENT_NODE_NAME];
    } else {
      return [null, ...this.instanceEdit.state$.value?.config?.nodeDtos.map((n) => n.nodeName).filter((n) => n !== CLIENT_NODE_NAME)];
    }
  }

  private getLabelsFor(group: InstanceTemplateGroup): string[] {
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

  public validateAnyGroupSelected() {
    for (const k of Object.keys(this.groups)) {
      const v = this.groups[k];
      if (v !== null && v !== undefined) {
        this.isAnyGroupSelected = true;
        return;
      }
    }
    this.isAnyGroupSelected = false;
  }

  public validateHasAllVariables() {
    if (!this.template) {
      return;
    }
    for (const v of this.template.variables) {
      const value = this.variables[v.uid];
      if (value === '' || value === null || value === undefined) {
        this.hasAllVariables = false;
        return;
      }
    }
    this.hasAllVariables = true;
  }

  private apply(template: InstanceTemplateDescriptor) {
    // setup things required by the templates.
    this.template = template;
    this.template.groups.map((group) => {
      group.nodes = this.getNodesFor(group);
      group.labels = this.getLabelsFor(group);
    });
    this.groups = {};
    this.variables = {};

    if (!!template.variables?.length) {
      for (const v of template.variables) {
        this.variables[v.uid] = v.defaultValue;
      }
    }
    this.validateAnyGroupSelected();
    this.goNext();
  }

  /* template */ goToAssignVariableStep() {
    this.validateHasAllVariables();
    this.goNext();
  }

  private goNext(isLastStep = false) {
    this.myStepper.selected.completed = true;
    isLastStep ? this.myStepper.reset() : this.myStepper.next();
  }

  public applyStageFinal() {
    this.loading$.next(true);
    this.messages = [];
    const observables = [];
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
        for (const app of group.applications) {
          observables.push(
            this.instanceEdit.nodes$.pipe(
              // wait for node information
              skipWhile((n) => !n),
              // pick the first valid node info
              first(),
              // map the node info to the application key we need for our node.
              map((n) => this.instanceEdit.stateApplications$.value?.find((a) => a.key.name === getTemplateAppKey(this.product, app, n[nodeName]))),
              // map the key of the app to an observable to actually add the application if possible.
              map((a) => {
                if (!a) {
                  this.messages.push({
                    group: groupName,
                    node: nodeName,
                    template: app,
                    appname: !!app?.name ? app.name : app.application,
                    message: { icon: 'warning', message: 'Cannot find application in product for target OS.' },
                  });
                  return of<string>(null);
                } else {
                  const status = [];
                  return this.edit.addProcess(node, a, app, this.variables, status).pipe(
                    finalize(() => {
                      status.forEach((e) =>
                        this.messages.push({ group: groupName, node: nodeName, appname: !!app?.name ? app.name : a.name, template: app, message: e })
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
      } else {
        // for clients we add all matches, regardless of the OS.
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
                        appname: !!template?.name ? template.name : app.name,
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
    }

    if (!observables.length) {
      return; // nothing to do...?
    }

    // now execute and await all additions.
    combineLatest(observables)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((_) => {
        let applyResult = of(true);
        // now if we DO have messages, we want to show them to the user.
        if (!!this.messages.length) {
          applyResult = this.dialog.message({
            header: 'Template Messages',
            template: this.tplMessages,
            dismissResult: null,
            actions: [ACTION_CANCEL, ACTION_OK],
          });
        } else {
          this.goNext(true);
        }

        applyResult.subscribe((r) => {
          if (r) {
            this.instanceEdit.conceal(`Apply instance template ${this.template.name}`);
          } else {
            this.instanceEdit.discard();
          }
        });
      });
  }
}

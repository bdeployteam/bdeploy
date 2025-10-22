import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject, OnDestroy, OnInit, signal, viewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, forkJoin, Observable, of, Subscription, switchMap } from 'rxjs';
import { finalize, take } from 'rxjs/operators';
import { sortNodesMasterFirst } from 'src/app/models/consts';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import {
  Actions,
  ApplicationConfiguration,
  InstanceActivateCheckDto,
  InstanceDto,
  InstanceNodeConfigurationDto,
  InstanceStateRecord,
  NodeType,
  ProcessControlGroupHandlingType,
  ProcessControlGroupWaitType
} from 'src/app/models/gen.dtos';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  getNodeOfApplication,
  getProcessControlGroupOfApplication
} from 'src/app/modules/panels/instances/utils/instance-utils';
import { ProductsService } from '../../../products/services/products.service';
import { ServersService } from '../../../servers/services/servers.service';
import { InstanceStateService } from '../../services/instance-state.service';
import { InstancesService } from '../../services/instances.service';
import { ProcessesColumnsService } from '../../services/processes-columns.service';
import { CONTROL_GROUP_COL_ID } from './server-node/process-list/process-list.component';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { MatTooltip } from '@angular/material/tooltip';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import {
  BdServerSyncButtonComponent
} from '../../../../core/components/bd-server-sync-button/bd-server-sync-button.component';
import { BdDataGroupingComponent } from '../../../../core/components/bd-data-grouping/bd-data-grouping.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { RouterLink } from '@angular/router';
import { BdBannerComponent } from '../../../../core/components/bd-banner/bd-banner.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { ServerNodeComponent } from './server-node/server-node.component';
import { ClientNodeComponent } from './client-node/client-node.component';
import { AsyncPipe } from '@angular/common';
import { compareVersions, convert2String } from 'src/app/modules/core/utils/version.utils';
import { MultiNodeComponent } from './multi-node/multi-node.component';
import { BdDialogMessage } from '../../../../core/components/bd-dialog-message/bd-dialog-message.component';

@Component({
    selector: 'app-dashboard',
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.css'],
    imports: [BdDialogComponent, BdDialogToolbarComponent, MatTooltip, BdButtonComponent, MatDivider, BdServerSyncButtonComponent, BdDataGroupingComponent, BdPanelButtonComponent, BdDialogContentComponent, RouterLink, BdBannerComponent, BdNoDataComponent, ServerNodeComponent, MultiNodeComponent, ClientNodeComponent, AsyncPipe]
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly media = inject(BreakpointObserver);
  private readonly cfg = inject(ConfigService);
  private readonly states = inject(InstanceStateService);
  private readonly cardViewService = inject(CardViewService);
  private readonly processesColumns = inject(ProcessesColumnsService);
  private readonly actions = inject(ActionsService);
  private readonly products = inject(ProductsService);
  private readonly dialog = viewChild(BdDialogComponent);
  protected readonly instances = inject(InstancesService);
  protected readonly areas = inject(NavAreasService);
  protected readonly servers = inject(ServersService);
  protected readonly auth = inject(AuthenticationService);

  private readonly installing$ = new BehaviorSubject<boolean>(false);
  private readonly activating$ = new BehaviorSubject<boolean>(false);
  private subscription: Subscription;
  private isCardView: boolean;

  protected mappedInstall$ = this.actions.action([Actions.INSTALL, Actions.PUSH_PRODUCT], this.installing$);
  protected mappedActivate$ = this.actions.action([Actions.ACTIVATE], this.activating$);

  protected narrow$ = new BehaviorSubject<boolean>(true);

  protected serverNodes$ = new BehaviorSubject<InstanceNodeConfigurationDto[]>([]);
  protected clientNode$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  protected allApplications$ = new BehaviorSubject<ApplicationConfiguration[]>([]);

  protected gridMode$ = new BehaviorSubject<boolean>(false);
  protected grouping$ = new BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>([]);

  protected groupingDefinitions: BdDataGroupingDefinition<ApplicationConfiguration>[] = [
    {
      name: 'Process Control Group',
      group: (a) => this.getControlGroupDesc(a),
      sort: (a, b, eA) => this.sortControlGroup(a, b, eA),
      associatedColumn: CONTROL_GROUP_COL_ID,
    },
    { name: 'Start Type', group: (a) => a?.processControl?.startType },
    {
      name: 'Application',
      group: (a) => a?.application?.name,
      associatedColumn: this.processesColumns.applicationNameColumn.id,
    },
  ];
  protected defaultGrouping: BdDataGrouping<ApplicationConfiguration>[] = [
    { definition: this.groupingDefinitions[0], selected: [] },
  ];

  protected collapsed$ = new BehaviorSubject<boolean>(false);

  protected states$ = new BehaviorSubject<InstanceStateRecord>(null);

  protected currentInstance = signal<InstanceDto>(null);
  protected activeInstance = signal<InstanceDto>(null);

  protected isInstalled = signal(false);
  protected hasProduct = signal(false);
  protected hasMinMinionVersion = signal(false);
  protected installButtonDisabledMessage = signal<string>(null);

  protected isCentral = false;

  ngOnInit(): void {
    this.subscription = this.media.observe('(max-width:700px)').subscribe((bs) => this.narrow$.next(bs.matches));
    this.subscription.add(
      this.cfg.isCentral$.subscribe((value) => {
        this.isCentral = value;
      }),
    );
    this.subscription.add(
      this.instances.activeNodeCfgs$.subscribe((nodes) => {
        if (!nodes?.nodeConfigDtos?.length) {
          this.serverNodes$.next([]);
          this.clientNode$.next(null);
          this.allApplications$.next([]);
          return;
        }

        this.serverNodes$.next(
          nodes.nodeConfigDtos
            .filter((p) => p.nodeConfiguration.nodeType !== NodeType.CLIENT && !!p.nodeConfiguration?.applications?.length)
            .sort((a, b) => sortNodesMasterFirst(a.nodeName, b.nodeName)),
        );

        const allApps: ApplicationConfiguration[] = [];
        nodes.nodeConfigDtos.forEach((x) =>
          allApps.push(...(x?.nodeConfiguration?.applications ? x.nodeConfiguration.applications : [])),
        );
        this.allApplications$.next(allApps);
        this.clientNode$.next(
          nodes.nodeConfigDtos.find(
            (p) => p.nodeConfiguration.nodeType === NodeType.CLIENT && p.nodeConfiguration?.applications?.length,
          ),
        );
      }),
    );
    this.subscription.add(
      this.instances.current$.subscribe((currentInstance) => {
        this.currentInstance.set(currentInstance);
      }),
    );
    this.subscription.add(
      this.instances.active$.subscribe((activeInstance) => {
        this.activeInstance.set(activeInstance);
      }),
    );
    this.subscription.add(
      this.states.state$.subscribe((s) => {
        this.states$.next(s);
        this.isInstalled.set(!!s?.installedTags?.find((c) => c === this.currentInstance()?.instance.tag));
      }),
    );
    this.subscription.add(
      combineLatest([this.instances.current$, this.products.products$]).subscribe(([currentInstance, products]) => {
        const productKey = currentInstance?.instanceConfiguration?.product;
        const productDto = products?.find((p) => p.key.name === productKey?.name && p.key.tag === productKey?.tag);
        this.hasProduct.set(!!productDto);

        const minimumVersion = productDto?.minMinionVersion;
        if (minimumVersion) {
          const currentVersion = this.cfg?.config?.version;
          if (currentVersion) {
            if (compareVersions(currentVersion, minimumVersion) >= 0) {
              this.hasMinMinionVersion.set(true);
              this.installButtonDisabledMessage.set(null);
            } else {
              this.hasMinMinionVersion.set(false);
              this.installButtonDisabledMessage.set(
                'Installation is not possible because this product version requires a minimum BDeploy version of ' +
                  convert2String(minimumVersion) +
                  ' or above, but the current minion only has version ' +
                  convert2String(currentVersion)
              );
            }
          } else {
            this.hasMinMinionVersion.set(false);
            this.installButtonDisabledMessage.set(
              'Installation is not possible because this product version requires a minimum BDeploy version of ' +
                convert2String(minimumVersion) +
                ' or above, but the version of the current minion could not be determined'
            );
          }
        } else {
          this.hasMinMinionVersion.set(true);
          this.installButtonDisabledMessage.set(null);
        }
      }),
    );

    this.isCardView = this.cardViewService.checkCardView('processList');
    if (this.isCardView) {
      this.gridMode$.next(this.isCardView);
    }
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected doInstall(version: string) {
    this.installing$.next(true);
    this.states
      .install(version)
      .pipe(finalize(() => this.installing$.next(false)))
      .subscribe();
  }

  private formatPreActivateMessage(apps: ApplicationConfiguration[], check: InstanceActivateCheckDto) {
    const appMsgs = [];

    for (const node of Object.keys(check.runningForbidden)) {
      for (const app of check.runningForbidden[node]) {
        const appName = apps.filter(a => a.id === app)?.map(a => a.name)?.at(0);
        appMsgs.push(`<li>On <b>${node}</b>: <b>${appName}</b></li>`);
      }
    }

    let msg = `${appMsgs.length} processes are still running, but have been removed from the configuration:</br><ul class="local-list list-inside list-disc p-2">`;

    if (appMsgs.length > 10) {
      msg += appMsgs.slice(0, 10).join('');
      msg += `<li>... and ${appMsgs.length - 10} more</li>`;
    } else {
      msg += appMsgs.join();
    }

    msg += '</ul>';
    return msg;
  }

  protected doActivate(version: string) {
    this.activating$.next(true);

    forkJoin([this.allApplications$.pipe(take(1)), this.states.preActivate(version)]).pipe(switchMap(([apps, check]) => {
        type QuestionResult = 'continue' | 'abort' | 'not-required'
        let userQuestion: Observable<QuestionResult> = of('not-required');

        if (Object.keys(check.runningForbidden).length) {
          const m: BdDialogMessage<QuestionResult> = {
            header: 'Running Applications',
            message: this.formatPreActivateMessage(apps, check),
            icon: 'deployed_code_alert',
            actions: [
              { name: 'Cancel activation', result: 'abort', confirm: false },
              { name: 'Stop applications and continue', result: 'continue', confirm: true }
            ]
          };
          userQuestion = this.dialog().message(m);
        }

        return userQuestion.pipe(switchMap((res) => {
          if (res === 'abort') {
            return of(null); // do nothing.
          }

          // force it in case we pressed continue. don't if we did not ask.
          return this.states.activate(version, res === 'continue');
        }));
      }
    ), finalize(() => this.activating$.next(false))).subscribe();
  }

  private getControlGroupDesc(app: ApplicationConfiguration): string {
    const node = getNodeOfApplication(this.serverNodes$.value, app.id);
    if (!node) {
      return null; // client apps
    }
    const grp = getProcessControlGroupOfApplication(node.nodeConfiguration?.controlGroups, app.id);

    let waitType: string;
    switch (grp.startWait) {
      case ProcessControlGroupWaitType.WAIT:
        waitType = 'W_START';
        break;
      case ProcessControlGroupWaitType.WAIT_UNTIL_STOPPED:
        waitType = 'W_STOP';
        break;
      case ProcessControlGroupWaitType.CONTINUE:
        waitType = 'C';
        break;
    }

    return `${grp?.name} [${grp?.startType === ProcessControlGroupHandlingType.SEQUENTIAL ? 'S' : 'P'}-${waitType}/${grp?.stopType === ProcessControlGroupHandlingType.SEQUENTIAL ? 'S' : 'P'}]`;
  }

  private sortControlGroup(a: string, b: string, entriesA: ApplicationConfiguration[]): number {
    // a group can only exist if it has entries, so entriesA cannot be empty. All entries are on the same node, so we calculate the node
    // (hosting the control groups) from *any* application.
    if (!entriesA) {
      // grouping panel, etc.
      return a.localeCompare(b);
    }
    const node = getNodeOfApplication(this.serverNodes$.value, entriesA[0].id);
    if (!node) {
      return a.localeCompare(b); // client apps
    }

    // need to account for the strings built in the getControlGroupDesc function
    const indexA = node.nodeConfiguration.controlGroups?.findIndex((g) => a?.startsWith(g.name + ' ['));
    const indexB = node.nodeConfiguration.controlGroups?.findIndex((g) => b?.startsWith(g.name + ' ['));

    return indexA - indexB;
  }

  protected goToInstanceConfiguration() {
    this.areas.navigateBoth(
      ['/instances', 'configuration', this.areas.groupContext$.value, this.areas.instanceContext$.value],
      ['panels', 'instances', 'settings'],
    );
  }

  protected readonly NodeType = NodeType;
}

import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { CLIENT_NODE_NAME, sortNodesMasterFirst } from 'src/app/models/consts';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import {
  Actions,
  ApplicationConfiguration,
  InstanceDto,
  InstanceNodeConfigurationDto,
  InstanceStateRecord,
  ProcessControlGroupHandlingType,
  ProcessControlGroupWaitType,
} from 'src/app/models/gen.dtos';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  getNodeOfApplication,
  getProcessControlGroupOfApplication,
} from 'src/app/modules/panels/instances/utils/instance-utils';
import { ServersService } from '../../../servers/services/servers.service';
import { InstanceStateService } from '../../services/instance-state.service';
import { InstancesService } from '../../services/instances.service';
import { ProcessesColumnsService } from '../../services/processes-columns.service';
import { CONTROL_GROUP_COL_ID } from './server-node/process-list/process-list.component';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit, OnDestroy {
  private media = inject(BreakpointObserver);
  private cfg = inject(ConfigService);
  private states = inject(InstanceStateService);
  private cardViewService = inject(CardViewService);
  private processesColumns = inject(ProcessesColumnsService);
  private actions = inject(ActionsService);
  protected instances = inject(InstancesService);
  protected areas = inject(NavAreasService);
  protected servers = inject(ServersService);
  protected auth = inject(AuthenticationService);

  private installing$ = new BehaviorSubject<boolean>(false);
  private activating$ = new BehaviorSubject<boolean>(false);
  private subscription: Subscription;
  private isCardView: boolean;

  protected mappedInstall$ = this.actions.action([Actions.INSTALL], this.installing$);
  protected mappedActivate$ = this.actions.action([Actions.ACTIVATE], this.activating$);

  protected narrow$ = new BehaviorSubject<boolean>(true);

  protected serverNodes$ = new BehaviorSubject<InstanceNodeConfigurationDto[]>([]);
  protected clientNode$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  protected allApplications$ = new BehaviorSubject<ApplicationConfiguration[]>([]);

  protected gridMode$ = new BehaviorSubject<boolean>(false);
  protected grouping$ = new BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>([]);
  protected defaultGrouping$ = new BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>([]);

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

  protected currentInstance: InstanceDto;
  protected activeInstance: InstanceDto;
  protected isInstalled: boolean;

  protected isCentral = false;

  ngOnInit(): void {
    this.subscription = this.media.observe('(max-width:700px)').subscribe((bs) => this.narrow$.next(bs.matches));
    this.subscription.add(
      this.cfg.isCentral$.subscribe((value) => {
        this.isCentral = value;
      })
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
            .filter((p) => p.nodeName !== CLIENT_NODE_NAME && !!p.nodeConfiguration?.applications?.length)
            .sort((a, b) => sortNodesMasterFirst(a.nodeName, b.nodeName))
        );

        const allApps = [];
        nodes.nodeConfigDtos.forEach((x) =>
          allApps.push(...(x?.nodeConfiguration?.applications ? x.nodeConfiguration.applications : []))
        );
        this.allApplications$.next(allApps);
        this.clientNode$.next(
          nodes.nodeConfigDtos.find((p) => p.nodeName === CLIENT_NODE_NAME && p.nodeConfiguration?.applications?.length)
        );
      })
    );
    this.subscription.add(
      this.states.state$.subscribe((s) => {
        this.states$.next(s);
        this.isInstalled = !!s?.installedTags?.find((c) => c === this.currentInstance?.instance.tag);
      })
    );
    this.subscription.add(
      this.instances.current$.subscribe((currentInstance) => {
        this.currentInstance = currentInstance;
      })
    );
    this.subscription.add(
      this.instances.active$.subscribe((activeInstance) => {
        this.activeInstance = activeInstance;
      })
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

  protected doActivate(version: string) {
    this.activating$.next(true);
    this.states
      .activate(version)
      .pipe(finalize(() => this.activating$.next(false)))
      .subscribe();
  }

  private getControlGroupDesc(app: ApplicationConfiguration): string {
    const node = getNodeOfApplication(this.serverNodes$.value, app.id);
    if (!node) {
      return null; // client apps
    }
    const grp = getProcessControlGroupOfApplication(node.nodeConfiguration?.controlGroups, app.id);
    return `${grp?.name} [${grp?.startType === ProcessControlGroupHandlingType.SEQUENTIAL ? 'S' : 'P'}-${
      grp?.startWait === ProcessControlGroupWaitType.WAIT ? 'W' : 'C'
    }/${grp?.stopType === ProcessControlGroupHandlingType.SEQUENTIAL ? 'S' : 'P'}]`;
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
}

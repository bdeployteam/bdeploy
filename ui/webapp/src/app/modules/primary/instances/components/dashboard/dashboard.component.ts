import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { CLIENT_NODE_NAME, sortNodesMasterFirst } from 'src/app/models/consts';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  InstanceDto,
  InstanceNodeConfigurationDto,
  InstanceStateRecord,
  ProcessControlGroupHandlingType,
  ProcessControlGroupWaitType,
} from 'src/app/models/gen.dtos';
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

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit, OnDestroy {
  /* template */ narrow$ = new BehaviorSubject<boolean>(true);

  /* template */ serverNodes$ = new BehaviorSubject<
    InstanceNodeConfigurationDto[]
  >([]);
  /* template */ clientNode$ =
    new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  /* template */ allApplications$ = new BehaviorSubject<
    ApplicationConfiguration[]
  >([]);

  /* template */ gridMode$ = new BehaviorSubject<boolean>(false);
  /* template */ grouping$ = new BehaviorSubject<
    BdDataGrouping<ApplicationConfiguration>[]
  >([]);
  /* template */ defaultGrouping$ = new BehaviorSubject<
    BdDataGrouping<ApplicationConfiguration>[]
  >([]);

  /* template */ groupingDefinitions: BdDataGroupingDefinition<ApplicationConfiguration>[] =
    [
      {
        name: 'Process Control Group',
        group: (a) => this.getControlGroupDesc(a),
        sort: (a, b, eA) => this.sortControlGroup(a, b, eA),
      },
      { name: 'Start Type', group: (a) => a?.processControl?.startType },
      { name: 'Application', group: (a) => a?.application?.name },
    ];
  /* template */ defaultGrouping: BdDataGrouping<ApplicationConfiguration>[] = [
    { definition: this.groupingDefinitions[0], selected: [] },
  ];

  /* template */ collapsed$ = new BehaviorSubject<boolean>(false);

  /* template */ states$ = new BehaviorSubject<InstanceStateRecord>(null);
  /* template */ installing$ = new BehaviorSubject<boolean>(false);
  /* template */ activating$ = new BehaviorSubject<boolean>(false);
  /* template */ currentInstance: InstanceDto;
  /* template */ activeInstance: InstanceDto;
  /* template */ isInstalled: boolean;

  private subscription: Subscription;
  /* template */ public isCentral = false;
  private isCardView: boolean;

  constructor(
    private media: BreakpointObserver,
    public instances: InstancesService,
    public areas: NavAreasService,
    private cfg: ConfigService,
    public servers: ServersService,
    public auth: AuthenticationService,
    private states: InstanceStateService,
    private cardViewService: CardViewService
  ) {}

  ngOnInit(): void {
    this.subscription = this.media
      .observe('(max-width:700px)')
      .subscribe((bs) => this.narrow$.next(bs.matches));
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
            .filter(
              (p) =>
                p.nodeName !== CLIENT_NODE_NAME &&
                !!p.nodeConfiguration?.applications?.length
            )
            .sort((a, b) => sortNodesMasterFirst(a.nodeName, b.nodeName))
        );

        const allApps = [];
        nodes.nodeConfigDtos.forEach((x) =>
          allApps.push(
            ...(x?.nodeConfiguration?.applications
              ? x.nodeConfiguration.applications
              : [])
          )
        );
        this.allApplications$.next(allApps);
        this.clientNode$.next(
          nodes.nodeConfigDtos.find(
            (p) =>
              p.nodeName === CLIENT_NODE_NAME &&
              p.nodeConfiguration?.applications?.length
          )
        );
      })
    );
    this.subscription.add(
      this.states.state$.subscribe((s) => {
        this.states$.next(s);
        this.isInstalled = !!s?.installedTags?.find(
          (c) => c === this.currentInstance?.instance.tag
        );
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
    this.subscription.unsubscribe();
  }

  /* template */ doInstall(version: string) {
    this.installing$.next(true);
    this.states
      .install(version)
      .pipe(finalize(() => this.installing$.next(false)))
      .subscribe();
  }

  /* template */ doActivate(version: string) {
    this.activating$.next(true);
    this.states
      .activate(version)
      .pipe(finalize(() => this.activating$.next(false)))
      .subscribe();
  }

  private getControlGroupDesc(app: ApplicationConfiguration): string {
    const node = getNodeOfApplication(this.serverNodes$.value, app.uid);
    if (!node) {
      return null; // client apps
    }
    const grp = getProcessControlGroupOfApplication(
      node.nodeConfiguration?.controlGroups,
      app.uid
    );
    return `${grp?.name} [${
      grp?.startType === ProcessControlGroupHandlingType.SEQUENTIAL ? 'S' : 'P'
    }-${grp?.startWait === ProcessControlGroupWaitType.WAIT ? 'W' : 'C'}/${
      grp?.stopType === ProcessControlGroupHandlingType.SEQUENTIAL ? 'S' : 'P'
    }]`;
  }

  private sortControlGroup(
    a: string,
    b: string,
    entriesA: ApplicationConfiguration[]
  ): number {
    // a group can only exist if it has entries, so entriesA cannot be empty. All entries are on the same node, so we calculate the node
    // (hosting the control groups) from *any* application.
    if (!entriesA) {
      // grouping panel, etc.
      return a.localeCompare(b);
    }
    const node = getNodeOfApplication(this.serverNodes$.value, entriesA[0].uid);
    if (!node) {
      return a.localeCompare(b); // client apps
    }

    // need to account for the strings built in the getControlGroupDesc function
    const indexA = node.nodeConfiguration.controlGroups?.findIndex((g) =>
      a?.startsWith(g.name + ' [')
    );
    const indexB = node.nodeConfiguration.controlGroups?.findIndex((g) =>
      b?.startsWith(g.name + ' [')
    );

    return indexA - indexB;
  }
}

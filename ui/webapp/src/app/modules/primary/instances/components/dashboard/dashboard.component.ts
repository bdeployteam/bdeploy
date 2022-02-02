import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { CLIENT_NODE_NAME, sortNodesMasterFirst } from 'src/app/models/consts';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { ApplicationConfiguration, InstanceDto, InstanceNodeConfigurationDto, InstanceStateRecord } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
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

  /* template */ serverNodes$ = new BehaviorSubject<InstanceNodeConfigurationDto[]>([]);
  /* template */ clientNode$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  /* template */ allApplications$ = new BehaviorSubject<ApplicationConfiguration[]>([]);

  /* template */ gridMode$ = new BehaviorSubject<boolean>(false);
  /* template */ grouping$ = new BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>([]);

  /* template */ groupingDefinitions: BdDataGroupingDefinition<ApplicationConfiguration>[] = [
    { name: 'Start Type', group: (a) => a?.processControl?.startType },
    { name: 'Application', group: (a) => a?.application?.name },
  ];

  /* template */ collapsed$ = new BehaviorSubject<boolean>(false);

  /* template */ states$ = new BehaviorSubject<InstanceStateRecord>(null);
  /* template */ installing$ = new BehaviorSubject<boolean>(false);
  /* template */ activating$ = new BehaviorSubject<boolean>(false);
  /* template */ currentInstance: InstanceDto;
  /* template */ activeInstance: InstanceDto;
  /* template */ isInstalled: boolean;

  private subscription: Subscription;
  /* template */ public isCentral: boolean = false;
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

        this.allApplications$.next([].concat(nodes.nodeConfigDtos.map((x) => (!!x?.nodeConfiguration?.applications ? x.nodeConfiguration.applications : []))));
        this.clientNode$.next(nodes.nodeConfigDtos.find((p) => p.nodeName === CLIENT_NODE_NAME && p.nodeConfiguration?.applications?.length));
      })
    );
    this.subscription.add(
      this.states.state$.subscribe((s) => {
        this.states$.next(s);
        this.isInstalled = !!s?.installedTags?.find((s) => s === this.currentInstance?.instance.tag);
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
}

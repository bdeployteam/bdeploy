import {
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
  ViewEncapsulation,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { distinctUntilChanged, finalize, map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  ApplicationStartType,
  HttpEndpoint,
  HttpEndpointType,
  InstanceNodeConfigurationDto,
  ParameterType,
  ProcessDetailDto,
  ProcessProbeResultDto,
  ProcessState,
} from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { ProcessDetailsService } from '../../services/process-details.service';
import { PinnedParameterValueComponent } from './pinned-parameter-value/pinned-parameter-value.component';

export interface PinnedParameter {
  appId: string;
  paramId: string;
  name: string;
  value: string;
  type: ParameterType;
}

const colPinnedName: BdDataColumn<PinnedParameter> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const colPinnedValue: BdDataColumn<PinnedParameter> = {
  id: 'value',
  name: 'Value',
  data: (r) => r.value,
  component: PinnedParameterValueComponent,
};

@Component({
  selector: 'app-process-status',
  templateUrl: './process-status.component.html',
  styleUrls: ['./process-status.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class ProcessStatusComponent implements OnInit, OnDestroy {
  /* template */ uptime$ = new BehaviorSubject<string>(null);
  /* template */ restartProgress$ = new BehaviorSubject<number>(0);
  /* template */ restartProgressText$ = new BehaviorSubject<string>(null);
  /* template */ outdated$ = new BehaviorSubject<boolean>(false);

  /* template */ starting$ = new BehaviorSubject<boolean>(false);
  /* template */ stopping$ = new BehaviorSubject<boolean>(false);
  /* template */ restarting$ = new BehaviorSubject<boolean>(false);
  /* template */ isCrashedWaiting: boolean;
  /* template */ isStopping: boolean;
  /* template */ isRunning: boolean;
  /* template */ isStartPlanned: boolean;
  /* template */ processDetail: ProcessDetailDto;
  /* template */ processConfig: ApplicationConfiguration;
  /* template */ nodeCfg: InstanceNodeConfigurationDto;
  /* template */ startType: 'Instance' | 'Manual' | 'Confirmed Manual';
  /* template */ pinnedParameters: PinnedParameter[] = [];
  /* template */ pinnedColumns: BdDataColumn<PinnedParameter>[] = [
    colPinnedName,
    colPinnedValue,
  ];
  /* template */ uiEndpoints: HttpEndpoint[] = [];

  public performing$ = new BehaviorSubject<boolean>(false);

  private restartProgressHandle: any;
  private uptimeCalculateHandle: any;

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    public auth: AuthenticationService,
    public groups: GroupsService,
    public details: ProcessDetailsService,
    public processes: ProcessesService,
    public instances: InstancesService,
    public servers: ServersService,
    private cfg: ConfigService,
    private route: ActivatedRoute,
    private systems: SystemsService,
    private cd: ChangeDetectorRef,
    public areas: NavAreasService
  ) {}

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.details.processDetail$,
      this.details.processConfig$,
      this.instances.active$,
      this.instances.activeNodeCfgs$,
      this.systems.systems$,
    ]).subscribe(([detail, config, active, nodes, systems]) => {
      this.clearIntervals();
      this.outdated$.next(false);
      this.processConfig = config;
      this.startType = this.formatStartType(
        this.processConfig?.processControl.startType
      );
      this.nodeCfg = nodes?.nodeConfigDtos?.find(
        (n) =>
          n.nodeConfiguration.applications.findIndex(
            (a) => a.id === config?.id
          ) !== -1
      );

      const app = nodes?.applications?.find(
        (a) =>
          a.key.name === config?.application?.name &&
          a.key.tag === config?.application?.tag
      );

      if (app) {
        const system = systems?.find(
          (s) => s.key.name === active?.instanceConfiguration?.system?.name
        );
        this.pinnedParameters = config.start.parameters
          .filter((p) => p.pinned)
          .map((p) => {
            const desc = app?.descriptor?.startCommand?.parameters?.find(
              (x) => x.id === p.id
            );
            return {
              appId: config.id,
              paramId: p.id,
              name: desc.name,
              value: getRenderPreview(
                p.value,
                config,
                {
                  config: active.instanceConfiguration,
                  nodeDtos: nodes?.nodeConfigDtos,
                },
                system?.config
              ),
              type: desc.type,
            };
          });
      }

      // figure out if we have UI endpoints configured.
      this.uiEndpoints = config?.endpoints.http.filter(
        (e) => e.type === HttpEndpointType.UI
      );

      // when switching to another process, we *need* to forget those, even if we cannot restore them later on.
      this.starting$.next(false);
      this.stopping$.next(false);
      this.restarting$.next(false);

      if (!detail || detail?.status?.appId !== config?.id) {
        this.processDetail = null;
        this.isCrashedWaiting = false;
        this.isRunning = false;
        this.isStartPlanned = false;
        this.isStopping = false;
        this.outdated$.next(false);
        return;
      }
      this.processDetail = detail;
      this.isCrashedWaiting =
        detail.status.processState === ProcessState.CRASHED_WAITING;
      this.isRunning = ProcessesService.isRunning(detail.status.processState);
      this.isStartPlanned =
        detail.status.processState === ProcessState.STOPPED_START_PLANNED;
      this.isStopping =
        detail.status.processState === ProcessState.RUNNING_STOP_PLANNED;

      this.outdated$.next(
        detail.status.instanceTag !== active.activeVersion.tag
      );

      if (this.isCrashedWaiting) {
        this.restartProgressHandle = setInterval(
          () => this.doUpdateRestartProgress(detail),
          1000
        );
        this.doUpdateRestartProgress(detail);
      }

      if (this.isRunning) {
        this.uptimeCalculateHandle = setTimeout(
          () => this.doCalculateUptimeString(detail),
          1
        );
      }
    });

    this.subscription.add(
      combineLatest([this.starting$, this.stopping$, this.restarting$])
        .pipe(map(([a, b, c]) => a || b || c))
        .subscribe((b) => {
          this.performing$.next(b);
        })
    );

    // when processConfig$ emits value with new id, confirmation dialog must be closed
    this.subscription.add(
      this.details.processConfig$
        .pipe(
          map((config) => config?.id),
          distinctUntilChanged()
        )
        .subscribe(() => {
          this.dialog?.messageComp.reset();
        })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.clearIntervals();
  }

  private clearIntervals() {
    if (this.restartProgressHandle) {
      clearInterval(this.restartProgressHandle);
    }
    if (this.uptimeCalculateHandle) {
      clearTimeout(this.uptimeCalculateHandle);
    }
  }

  private formatStartType(type: ApplicationStartType) {
    switch (type) {
      case ApplicationStartType.INSTANCE:
        return 'Instance';
      case ApplicationStartType.MANUAL:
        return 'Manual';
      case ApplicationStartType.MANUAL_CONFIRM:
        return 'Confirmed Manual';
    }
  }

  /* template */ trackProbe(index: number, probe: ProcessProbeResultDto) {
    return probe.type;
  }

  /* template */ start() {
    this.starting$.next(true);
    let confirmation = of(true);

    // rather die than "mistakingly" start a manual confirm application.
    if (!this.processConfig) {
      throw new Error('Process config not available?!');
    }

    if (
      this.processConfig.processControl.startType ===
      ApplicationStartType.MANUAL_CONFIRM
    ) {
      confirmation = this.dialog.message({
        header: 'Confirm Process Start',
        message: `Please confirm the start of <strong>${this.processConfig.name}</strong>.`,
        icon: 'play_arrow',
        confirmation: this.processConfig.name,
        confirmationHint: 'Confirm using process name',
        actions: [ACTION_CANCEL, ACTION_OK],
      });
    }

    confirmation.subscribe((b) => {
      if (!b) {
        this.starting$.next(false);
        return;
      }
      this.processes
        .start([this.processDetail.status.appId])
        .pipe(finalize(() => this.starting$.next(false)))
        .subscribe();
    });
  }

  /* template */ stop() {
    this.stopping$.next(true);
    this.processes
      .stop([this.processDetail.status.appId])
      .pipe(finalize(() => this.stopping$.next(false)))
      .subscribe();
  }

  /* template */ restart() {
    this.restarting$.next(true);
    this.processes
      .restart([this.processDetail.status.appId])
      .pipe(finalize(() => this.restarting$.next(false)))
      .subscribe();
  }

  /* template */ getRouterLink(r: HttpEndpoint) {
    const returnUrl = this.route.snapshot.pathFromRoot
      .map((s) => s.url.map((u) => u.toString()).join('/'))
      .join('/');
    return [
      '',
      {
        outlets: {
          panel: [
            'panels',
            'groups',
            'endpoint',
            this.processConfig.id,
            r.id,
            {
              returnPanel: returnUrl,
            },
          ],
        },
      },
    ];
  }

  private doCalculateUptimeString(detail) {
    this.uptimeCalculateHandle = null;
    if (this.isRunning) {
      const now = this.cfg.getCorrectedNow(); // server's 'now'
      const ms = now - detail.handle.startTime; // this comes from the node. node and master are assumed to have the same time.
      const sec = Math.floor(ms / 1000) % 60;
      const min = Math.floor(ms / 60000) % 60;
      const hours = Math.floor(ms / 3600000) % 24;
      const days = Math.floor(ms / 86400000);

      let s = '';
      if (days > 0) {
        s = s + days + (days === 1 ? ' day ' : ' days ');
      }
      if (hours > 0 || days > 0) {
        s = s + hours + (hours === 1 ? ' hour ' : ' hours ');
      }
      if (min > 0 || hours > 0 || days > 0) {
        s = s + min + (min === 1 ? ' minute' : ' minutes');
      }
      let delay = 0;
      if (days === 0 && hours === 0 && min === 0) {
        s = s + sec + (sec === 1 ? ' second' : ' seconds');
        // calculate reschedule for next second
        delay = 1000 - (ms - Math.floor(ms / 1000) * 1000);
      } else {
        // calculate reschedule for next minute
        delay = 60000 - (ms - Math.floor(ms / 60000) * 60000);
      }
      this.uptime$.next(s);
      this.uptimeCalculateHandle = setTimeout(
        () => this.doCalculateUptimeString(detail),
        delay
      );

      this.cd.detectChanges();
    } else {
      this.uptime$.next(null);
    }
  }

  private doUpdateRestartProgress(detail: ProcessDetailDto) {
    const diff = detail.recoverAt - this.cfg.getCorrectedNow();
    if (diff < 100) {
      this.processes.reload();
    } else {
      const totalSeconds = detail.recoverDelay + 2;
      const remainingSeconds = Math.round(diff / 1000);
      this.restartProgress$.next(100 - 100 * (remainingSeconds / totalSeconds));
      this.restartProgressText$.next(remainingSeconds + ' seconds');
      this.cd.detectChanges();
    }
  }
}

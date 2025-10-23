import { Component, inject, OnDestroy, OnInit, signal, ViewChild, ViewEncapsulation } from '@angular/core';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { distinctUntilChanged, finalize, map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import {
  Actions,
  ApplicationConfiguration,
  ApplicationStartType,
  InstanceNodeConfigurationDto,
  ProcessState
} from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesService, StartType } from 'src/app/modules/primary/instances/services/processes.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import {
  BdNotificationCardComponent
} from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { MatIcon } from '@angular/material/icon';
import { BdIdentifierComponent } from '../../../../core/components/bd-identifier/bd-identifier.component';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatTooltip } from '@angular/material/tooltip';
import { BdExpandButtonComponent } from '../../../../core/components/bd-expand-button/bd-expand-button.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';
import { ProductsService } from '../../../../primary/products/services/products.service';
import {
  PinnedParameterValueComponent
} from '../process-status/pinned-parameter-value/pinned-parameter-value.component';
import { MultiNodeProcessStatusService, MultiProcessStatusDto } from '../../services/multi-node-process-status.service';
import { PinnedParameter } from '../process-status/process-status.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';

const colPinnedName: BdDataColumn<PinnedParameter, string> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name
};

const colPinnedValue: BdDataColumn<PinnedParameter, string> = {
  id: 'value',
  name: 'Value',
  data: (r) => r.value,
  component: PinnedParameterValueComponent
};

@Component({
  selector: 'app-multi-node-process-status',
  templateUrl: './multi-process-status.component.html',
  styleUrls: ['./multi-process-status.component.css'],
  encapsulation: ViewEncapsulation.None,
  imports: [BdDialogComponent, BdDialogToolbarComponent, MatDivider, BdButtonComponent, BdDialogContentComponent, BdNotificationCardComponent, MatIcon, BdIdentifierComponent, MatProgressSpinner, MatTooltip, BdExpandButtonComponent, BdDataTableComponent, AsyncPipe, BdNoDataComponent]
})
export class MultiProcessStatusComponent implements OnInit, OnDestroy {
  private readonly systems = inject(SystemsService);
  private readonly actions = inject(ActionsService);
  private readonly products = inject(ProductsService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly groups = inject(GroupsService);
  protected readonly multiNodeDetailsService = inject(MultiNodeProcessStatusService);
  protected readonly processes = inject(ProcessesService);
  protected readonly instances = inject(InstancesService);
  protected readonly servers = inject(ServersService);
  protected readonly areas = inject(NavAreasService);

  protected outdated$ = new BehaviorSubject<boolean>(false);
  private readonly starting$ = new BehaviorSubject<boolean>(false);
  private readonly stopping$ = new BehaviorSubject<boolean>(false);
  private readonly restarting$ = new BehaviorSubject<boolean>(false);

  protected multiStatus: MultiProcessStatusDto;
  protected processConfig: ApplicationConfiguration;
  protected nodeCfg: InstanceNodeConfigurationDto;
  protected startType: StartType;
  protected pinnedParameters: PinnedParameter[] = [];
  protected readonly pinnedColumns: BdDataColumn<PinnedParameter, unknown>[] = [colPinnedName, colPinnedValue];

  protected pid$ = this.multiNodeDetailsService.processConfig$.pipe(map((x) => x?.id));

  protected mappedStart$ = this.actions.action([Actions.START_PROCESS], this.starting$, null, null, this.pid$);
  protected mappedStop$ = this.actions.action([Actions.STOP_PROCESS], this.stopping$, null, null, this.pid$);
  protected mappedRestart$ = this.actions.action(
    [Actions.START_PROCESS, Actions.STOP_PROCESS],
    this.restarting$,
    null,
    null,
    this.pid$
  );

  protected performing$ = combineLatest([this.mappedStart$, this.mappedStop$, this.mappedRestart$]).pipe(
    map(([a, b, c]) => a || b || c)
  );
  private readonly disabledBase = combineLatest([this.auth.isCurrentScopeWrite$, this.performing$, this.outdated$]);
  protected startDisabled$ = this.disabledBase.pipe(
    map(([perm, perform, outdated]) => !perm || perform || outdated || (this.multiStatus?.totalRunning == this.multiStatus?.total))
  );
  protected stopDisabled$ = this.disabledBase.pipe(
    map(([perm]) => !perm || (this.multiStatus?.totalRunning == 0))
  );
  protected restartDisabled$ = this.disabledBase.pipe(
    map(([perm, perform, outdated]) => !perm || perform || outdated || (this.multiStatus?.totalRunning == 0))
  );

  protected isProductAvailable = signal(false);

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.multiNodeDetailsService.multiStatus$,
      this.multiNodeDetailsService.processConfig$,
      this.instances.active$,
      this.instances.activeNodeCfgs$,
      this.systems.systems$,
      this.products.products$
    ]).subscribe(([multiStatus, config, active, nodes, systems, products]) => {
      this.outdated$.next(false);
      this.processConfig = config;
      this.startType = ProcessesService.formatStartType(this.processConfig?.processControl.startType);
      this.nodeCfg = nodes?.nodeConfigDtos?.find(
        (n) => n.nodeConfiguration.applications.findIndex((a) => a.id === config?.id) !== -1
      );

      const prod = this.nodeCfg?.nodeConfiguration?.product;
      if (prod && products?.find(p => p.key.name === prod.name && p.key.tag === prod.tag)) {
        this.isProductAvailable.set(true);
      }

      const app = nodes?.applications?.find(
        (a) => a.key.name === config?.application?.name && a.key.tag === config?.application?.tag
      );

      const system = systems?.find((s) => s.key.name === active?.instanceConfiguration?.system?.name);
      if (app) {
        this.pinnedParameters = config.start.parameters
          .filter((p) => p.pinned)
          .map((p) => {
            const desc = app?.descriptor?.startCommand?.parameters?.find((x) => x.id === p.id);
            return {
              appId: config.id,
              paramId: p.id,
              name: desc.name,
              value: getRenderPreview(
                p.value,
                config,
                {
                  config: active.instanceConfiguration,
                  nodeDtos: nodes?.nodeConfigDtos
                },
                system?.config
              ),
              type: desc.type
            };
          });
      }

      // when switching to another process, we *need* to forget those, even if we cannot restore them later on.
      this.starting$.next(false);
      this.stopping$.next(false);
      this.restarting$.next(false);


      this.multiStatus = multiStatus;
    });

    // when processConfig$ emits value with new id, confirmation dialog must be closed
    this.subscription.add(
      this.multiNodeDetailsService.processConfig$
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
    this.subscription?.unsubscribe();
  }

  protected start() {
    this.starting$.next(true);
    let confirmation = of(true);

    // rather die than "mistakenly" start a manual confirm application.
    if (!this.processConfig) {
      throw new Error('Process config not available?!');
    }

    if (this.processConfig.processControl.startType === ApplicationStartType.MANUAL_CONFIRM) {
      confirmation = this.dialog.message({
        header: 'Confirm Process Start',
        message: `Please confirm the start of <strong>${this.processConfig.name}</strong>.`,
        icon: 'play_arrow',
        confirmation: this.processConfig.name,
        confirmationHint: 'Confirm using process name',
        actions: [ACTION_CANCEL, ACTION_OK]
      });
    }

    confirmation.subscribe((b) => {
      if (!b) {
        this.starting$.next(false);
        return;
      }
      this.processes
        .start([this.processConfig.id])
        .pipe(finalize(() => this.starting$.next(false)))
        .subscribe();
    });
  }

  protected stop() {
    this.stopping$.next(true);
    this.processes
      .stop([this.processConfig.id])
      .pipe(finalize(() => this.stopping$.next(false)))
      .subscribe();
  }

  protected restart() {
    this.restarting$.next(true);
    this.processes
      .restart([this.processConfig.id])
      .pipe(finalize(() => this.restarting$.next(false)))
      .subscribe();
  }

  protected readonly ProcessState = ProcessState;
}

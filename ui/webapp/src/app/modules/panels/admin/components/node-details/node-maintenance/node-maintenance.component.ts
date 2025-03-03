import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, finalize, Subscription } from 'rxjs';
import { Actions, MinionStatusDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';

import { BdDialogToolbarComponent } from '../../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-node-maintenance',
    templateUrl: './node-maintenance.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdButtonComponent, AsyncPipe]
})
export class NodeMaintenanceComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly actions = inject(ActionsService);
  protected readonly nodesAdmin = inject(NodesAdminService);

  private readonly repairing$ = new BehaviorSubject<boolean>(false);
  private readonly restarting$ = new BehaviorSubject<boolean>(false);
  private readonly shuttingDown$ = new BehaviorSubject<boolean>(false);
  private subscription: Subscription;

  protected nodeName$ = new BehaviorSubject<string>(null);
  protected state: MinionStatusDto;
  protected mappedRepair$ = this.actions.action(
    [Actions.FSCK_NODE, Actions.PRUNE_NODE],
    this.repairing$,
    null,
    null,
    this.nodeName$
  );

  protected mappedRestart$ = this.actions.action([Actions.RESTART_NODE], this.restarting$, null, null, this.nodeName$);
  protected mappedShutdown$ = this.actions.action(
    [Actions.SHUTDOWN_NODE],
    this.shuttingDown$,
    null,
    null,
    this.nodeName$
  );

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  ngOnInit() {
    this.subscription = combineLatest([this.areas.panelRoute$, this.nodesAdmin.nodes$]).subscribe(([route, nodes]) => {
      this.nodeName$.next(route?.params?.['node']);
      this.state = nodes?.find((n) => n.name === this.nodeName$.value)?.status;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected doRepairAndPrune() {
    this.dialog
      .confirm('Repair and Prune', 'Repairing will remove any (anyhow) damaged and unusable elements from the BHive')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.repairing$.next(true);
          this.nodesAdmin
            .repairAndPruneNode(this.nodeName$.value)
            .pipe(finalize(() => this.repairing$.next(false)))
            .subscribe(({ repaired, pruned }) => {
              console.groupCollapsed('Damaged Objects');
              const keys = Object.keys(repaired);
              for (const key of keys) {
                console.log(key, ':', repaired[key]);
              }
              console.groupEnd();

              const repairMessage = keys?.length
                ? `Repair removed ${keys.length} damaged objects`
                : `No damaged objects were found.`;
              const pruneMessage = `Prune freed <strong>${pruned}</strong> on ${this.nodeName$.value}.`;
              this.dialog.info(`Repair and Prune`, `${repairMessage}<br/>${pruneMessage}`, 'build').subscribe();
            });
        }
      });
  }

  protected doRestartNode() {
    this.dialog
      .confirm('Restart', 'Restarting the node will make it temporarily unavailable.')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.restarting$.next(true);
          this.nodesAdmin
            .restartNode(this.nodeName$.value)
            .pipe(finalize(() => this.restarting$.next(false)))
            .subscribe();
        }
      });
  }

  protected doShutdownNode() {
    this.dialog
      .confirm(
        'Shutdown',
        'Shutting down the node will make it unavailable until started manually. ATTENTION: The node cannot be remotely restarted after shutdown.'
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.shuttingDown$.next(true);
          this.nodesAdmin
            .shutdownNode(this.nodeName$.value)
            .pipe(finalize(() => this.shuttingDown$.next(false)))
            .subscribe();
        }
      });
  }
}

import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest, finalize } from 'rxjs';
import { Actions, MinionStatusDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';

@Component({
  selector: 'app-node-maintenance',
  templateUrl: './node-maintenance.component.html',
})
export class NodeMaintenanceComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly actions = inject(ActionsService);
  protected readonly nodesAdmin = inject(NodesAdminService);

  private readonly repairing$ = new BehaviorSubject<boolean>(false);
  private subscription: Subscription;

  protected nodeName$ = new BehaviorSubject<string>(null);
  protected state: MinionStatusDto;
  protected mappedRepair$ = this.actions.action(
    [Actions.FSCK_NODE, Actions.PRUNE_NODE],
    this.repairing$,
    null,
    null,
    this.nodeName$,
  );

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  ngOnInit() {
    this.subscription = combineLatest([this.areas.panelRoute$, this.nodesAdmin.nodes$]).subscribe(([route, nodes]) => {
      this.nodeName$.next(route?.params?.node);
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
}

import { Component, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest, finalize } from 'rxjs';
import { MinionStatusDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';

@Component({
  selector: 'app-node-maintenance',
  templateUrl: './node-maintenance.component.html',
})
export class NodeMaintenanceComponent implements OnDestroy {
  /* template */ nodeName: string;
  /* template */ state: MinionStatusDto;
  /* template */ repairing$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(areas: NavAreasService, public nodesAdmin: NodesAdminService) {
    this.subscription = combineLatest([
      areas.panelRoute$,
      nodesAdmin.nodes$,
    ]).subscribe(([route, nodes]) => {
      this.nodeName = route?.params?.node;
      this.state = nodes?.find((n) => n.name === this.nodeName)?.status;
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ doRepairAndPrune() {
    this.dialog
      .confirm(
        'Repair and Prune',
        'Repairing will remove any (anyhow) damaged and unusable elements from the BHive'
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.repairing$.next(true);
          this.nodesAdmin
            .repairAndPruneNode(this.nodeName)
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
              const pruneMessage = `Prune freed <strong>${pruned}</strong> on ${this.nodeName}.`;
              this.dialog
                .info(
                  `Repair and Prune`,
                  `${repairMessage}<br/>${pruneMessage}`,
                  'build'
                )
                .subscribe();
            });
        }
      });
  }
}

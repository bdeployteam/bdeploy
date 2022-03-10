import { Component, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, finalize, Subscription } from 'rxjs';
import { MinionStatusDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { formatSize } from 'src/app/modules/core/utils/object.utils';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';

@Component({
  selector: 'app-node-maintenance',
  templateUrl: './node-maintenance.component.html',
})
export class NodeMaintenanceComponent implements OnDestroy {
  /* template */ nodeName: string;
  /* template */ state: MinionStatusDto;
  /* template */ repairing$ = new BehaviorSubject<boolean>(false);
  /* template */ pruning$ = new BehaviorSubject<boolean>(false);

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

  /* template */ doFsck() {
    this.dialog
      .confirm(
        'Repair',
        'Repairing will remove any (anyhow) damaged and unusable elements from the BHive'
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.repairing$.next(true);
          this.nodesAdmin
            .fsckNode(this.nodeName)
            .pipe(finalize(() => this.repairing$.next(false)))
            .subscribe((r) => {
              console.groupCollapsed('Damaged Objects');
              const keys = Object.keys(r);
              for (const key of keys) {
                console.log(key, ':', r[key]);
              }
              console.groupEnd();

              this.dialog
                .info(
                  `Repair`,
                  keys?.length
                    ? `Repair removed ${keys.length} damaged objects`
                    : `No damaged objects were found.`,
                  'build'
                )
                .subscribe();
            });
        }
      });
  }

  /* template */ doPrune() {
    this.pruning$.next(true);
    this.nodesAdmin
      .pruneNode(this.nodeName)
      .pipe(finalize(() => this.pruning$.next(false)))
      .subscribe((r) => {
        this.dialog
          .info(
            'Prune',
            `Prune freed <strong>${formatSize(r)}</strong> on ${this.nodeName}.`
          )
          .subscribe();
      });
  }
}

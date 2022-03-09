import { Component, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, finalize, Subscription } from 'rxjs';
import { MinionStatusDto } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';

@Component({
  selector: 'app-node-details',
  templateUrl: './node-details.component.html',
})
export class NodeDetailsComponent implements OnDestroy {
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ nodeName$ = new BehaviorSubject<string>(null);
  /* template */ nodeState$ = new BehaviorSubject<MinionStatusDto>(null);
  /* template */ nodeVersion: string;
  /* template */ isCurrent: boolean;
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  constructor(
    private nodeAdmin: NodesAdminService,
    private cfg: ConfigService,
    areas: NavAreasService
  ) {
    this.subscription = combineLatest([
      this.nodeAdmin.nodes$,
      areas.panelRoute$,
    ]).subscribe(([nodes, route]) => {
      if (!nodes || !route || !route.params.node) {
        this.nodeName$.next(null);
        this.nodeState$.next(null);
        return;
      }

      const nodeName = route.params.node;
      const nodeStatus = nodes.find((node) => node.name === nodeName);

      this.nodeName$.next(nodeName);
      this.nodeState$.next(nodeStatus.status);

      this.nodeVersion = nodeStatus.status?.config?.version
        ? convert2String(nodeStatus.status?.config?.version)
        : 'Unknown';

      this.isCurrent =
        this.nodeVersion === convert2String(this.cfg.config.version);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onDelete() {
    const nodeName = this.nodeName$.value;
    this.dialog
      .confirm(
        `Remove ${nodeName}?`,
        'The node will be removed. This cannot be undone. Any Instance configured to this node will <strong>stop working until configured differently.</strong>',
        'delete'
      )
      .subscribe((r) => {
        if (r) {
          this.deleting$.next(true);
          this.nodeAdmin
            .removeNode(this.nodeName$.value)
            .pipe(finalize(() => this.deleting$.next(false)))
            .subscribe(() => {
              this.tb.closePanel();
            });
        }
      });
  }
}

import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, finalize, Subscription } from 'rxjs';
import { Actions, MinionNodeType, MinionStatusDto } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';


import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import {
  BdNotificationCardComponent
} from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { MatTooltip } from '@angular/material/tooltip';
import { MatDivider } from '@angular/material/divider';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe, DatePipe } from '@angular/common';

@Component({
    selector: 'app-node-details',
    templateUrl: './node-details.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdNotificationCardComponent, MatTooltip, MatDivider, BdPanelButtonComponent, BdButtonComponent, AsyncPipe, DatePipe]
})
export class NodeDetailsComponent implements OnInit, OnDestroy {
  private readonly nodeAdmin = inject(NodesAdminService);
  private readonly cfg = inject(ConfigService);
  private readonly areas = inject(NavAreasService);

  protected nodeName$ = new BehaviorSubject<string>(null);
  protected nodeState$ = new BehaviorSubject<MinionStatusDto>(null);
  protected nodeVersion: string;
  protected isCurrent: boolean;

  private readonly deleting$ = new BehaviorSubject<boolean>(false);
  private readonly actions = inject(ActionsService);
  private subscription: Subscription;

  protected mappedDelete$ = this.actions.action([Actions.REMOVE_NODE], this.deleting$, null, null, this.nodeName$);

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  ngOnInit() {
    this.subscription = combineLatest([this.nodeAdmin.nodes$, this.areas.panelRoute$]).subscribe(([nodes, route]) => {
      if (!nodes || !route?.params?.['node']) {
        this.nodeName$.next(null);
        this.nodeState$.next(null);
        return;
      }

      const nodeName = route.params['node'];
      const nodeStatus = nodes.find((node) => node.name === nodeName);

      this.nodeName$.next(nodeName);
      this.nodeState$.next(nodeStatus.status);

      this.nodeVersion = nodeStatus.status?.config?.version
        ? convert2String(nodeStatus.status?.config?.version)
        : 'Unknown';

      this.isCurrent = this.nodeVersion === convert2String(this.cfg.config.version);
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onDelete() {
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

  protected isRuntimeNode(node: MinionStatusDto) {
    return node.config.minionNodeType === MinionNodeType.MULTI_RUNTIME;
  }
}

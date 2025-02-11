import { Component, inject, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, finalize, map, Subscription } from 'rxjs';
import { Actions, ManifestKey, OperatingSystem } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { MinionRecord, NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';
import { SoftwareUpdateService, SoftwareVersion } from 'src/app/modules/primary/admin/services/software-update.service';
import { BdDialogComponent } from '../../../../../core/components/bd-dialog/bd-dialog.component';

import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import {
  BdNotificationCardComponent
} from '../../../../../core/components/bd-notification-card/bd-notification-card.component';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-node-update',
    templateUrl: './node-update.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdNotificationCardComponent, MatDivider, BdButtonComponent, AsyncPipe]
})
export class NodeUpdateComponent implements OnDestroy {
  private readonly cfg = inject(ConfigService);
  private readonly software = inject(SoftwareUpdateService);
  private readonly nodesAdmin = inject(NodesAdminService);
  private readonly actions = inject(ActionsService);
  private readonly updating$ = new BehaviorSubject<boolean>(false);

  protected nodeName$ = new BehaviorSubject<string>(null);
  protected loading$ = combineLatest([this.software.loading$, this.nodesAdmin.loading$]).pipe(map(([a, b]) => a || b));
  protected mappedUpdate$ = this.actions.action([Actions.UPDATE_NODE], this.updating$, null, null, this.nodeName$);

  protected node: MinionRecord;
  protected version: SoftwareVersion;
  protected isCurrent = false;

  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  private readonly subscription: Subscription;

  constructor() {
    const areas = inject(NavAreasService);

    this.subscription = combineLatest([areas.panelRoute$, this.nodesAdmin.nodes$, this.software.software$]).subscribe(
      ([r, n, s]) => {
        if (!n || !s || !r?.params?.['node']) {
          this.nodeName$.next(null);
          this.node = null;
          return;
        }

        const currentVersion = convert2String(this.cfg.config.version);

        this.nodeName$.next(r.params['node']);
        this.node = n.find((minionRecord) => minionRecord.name === this.nodeName$.value);
        this.version = s.find(
          (v) => this.hasSystemFor(this.node.status.config.os, v.system) && v.version === currentVersion
        );
        this.isCurrent =
          !(this.node.status.config?.version && this.version?.version) ||
          convert2String(this.node.status.config.version) === this.version.version;
      }
    );

    // trigger loading software...
    this.software.load();
  }

  private hasSystemFor(os: OperatingSystem, keys: ManifestKey[]) {
    for (const k of keys) {
      if (getAppOs(k) === os) {
        return true;
      }
    }
    return false;
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected performUpdate() {
    this.updating$.next(true);
    this.nodesAdmin
      .updateNode(this.nodeName$.value, this.version.system)
      .pipe(finalize(() => this.updating$.next(false)))
      .subscribe(() => {
        this.tb.closePanel();
        this.software.load();
      });
  }
}

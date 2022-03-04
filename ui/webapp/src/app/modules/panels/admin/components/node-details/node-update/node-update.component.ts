import { Component, OnDestroy, ViewChild } from '@angular/core';
import {
  BehaviorSubject,
  combineLatest,
  finalize,
  map,
  Subscription,
} from 'rxjs';
import { ManifestKey, OperatingSystem } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import {
  MinionRecord,
  NodesAdminService,
} from 'src/app/modules/primary/admin/services/nodes-admin.service';
import {
  SoftwareUpdateService,
  SoftwareVersion,
} from 'src/app/modules/primary/admin/services/software-update.service';

@Component({
  selector: 'app-node-update',
  templateUrl: './node-update.component.html',
})
export class NodeUpdateComponent implements OnDestroy {
  /* template */ loading$ = combineLatest([
    this.software.loading$,
    this.nodesAdmin.loading$,
  ]).pipe(map(([a, b]) => a || b));
  /* template */ updating$ = new BehaviorSubject<boolean>(false);
  /* template */ nodeName: string;
  /* template */ node: MinionRecord;
  /* template */ version: SoftwareVersion;
  /* template */ isCurrent = false;

  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(
    private cfg: ConfigService,
    private software: SoftwareUpdateService,
    private nodesAdmin: NodesAdminService,
    areas: NavAreasService
  ) {
    this.subscription = combineLatest([
      areas.panelRoute$,
      nodesAdmin.nodes$,
      software.software$,
    ]).subscribe(([r, n, s]) => {
      if (!n || !s || !r?.params?.node) {
        this.nodeName = null;
        this.node = null;
        return;
      }

      const currentVersion = convert2String(cfg.config.version);

      this.nodeName = r.params.node;
      this.node = n.find((n) => n.name === this.nodeName);
      this.version = s.find(
        (v) =>
          this.hasSystemFor(this.node.status.config.os, v.system) &&
          v.version === currentVersion
      );
      this.isCurrent =
        !(this.node.status.config?.version && this.version?.version) ||
        convert2String(this.node.status.config.version) ===
          this.version.version;
    });

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
    this.subscription.unsubscribe();
  }

  /* template */ performUpdate() {
    this.updating$.next(true);
    this.nodesAdmin
      .updateNode(this.nodeName, this.version.system)
      .pipe(finalize(() => this.updating$.next(false)))
      .subscribe(() => {
        this.tb.closePanel();
        this.software.load();
      });
  }
}

import { Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { combineLatest, first, skipWhile, Subscription } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import {
  ClientApp,
  ClientsService,
} from 'src/app/modules/primary/groups/services/clients.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

@Component({
  selector: 'app-process-ui-inline',
  templateUrl: './process-ui-inline.component.html',
  styleUrls: ['./process-ui-inline.component.css'],
})
export class ProcessUiInlineComponent implements OnDestroy {
  /* template */ app: ClientApp;
  /* template */ url: SafeUrl;
  /* template */ directUri: string;
  /* template */ frameLoaded = false;
  /* template */ returnPanel: any[] = null;

  private rawUrl: string;
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('iframe', { static: false }) private iframe: ElementRef;

  constructor(
    clients: ClientsService,
    cfg: ConfigService,
    nav: NavAreasService,
    groups: GroupsService,
    sanitizer: DomSanitizer,
    instances: InstancesService,
    systems: SystemsService
  ) {
    this.subscription = combineLatest([
      nav.panelRoute$,
      groups.current$,
      clients.apps$,
      instances.active$,
      systems.systems$,
      instances.activeNodeCfgs$,
    ])
      .pipe(
        skipWhile(
          ([r, g, a, i, s, n]) =>
            !r?.params?.endpoint ||
            !r?.params?.app ||
            !g ||
            !a?.length ||
            !i ||
            (i?.instanceConfiguration?.system && !s?.length) ||
            !n?.nodeConfigDtos?.length
        ),
        first() // only calculate this *ONCE* when all data is there.
      )
      .subscribe(([route, group, apps, instance, systems, nodes]) => {
        if (route.params.returnPanel) {
          let panel: string = route.params.returnPanel;
          if (panel.startsWith('/')) {
            panel = panel.substring(1);
          }
          this.returnPanel = panel.split('/');
        }

        this.app = apps.find(
          (a) =>
            a.endpoint?.id === route.params.app &&
            a.endpoint.endpoint.id === route.params.endpoint
        );

        if (!this.app) {
          return;
        }

        clients.getDirectUiURI(this.app).subscribe((url) => {
          this.directUri = url;
        });

        const system = systems?.find(
          (s) => s.key.name === instance?.instanceConfiguration?.system?.name
        );
        const process = nodes?.nodeConfigDtos
          ?.map((n) =>
            n.nodeConfiguration?.applications?.find(
              (a) => a.id === this.app.client?.id
            )
          )
          .find((a) => a);

        this.rawUrl = `${cfg.config.api}/master/upx/${group.name}/${
          this.app.instance.id
        }/${this.app.endpoint.id}/${
          this.app.endpoint.endpoint.id
        }${this.cpWithSlash(
          getRenderPreview(
            this.app.endpoint.endpoint.contextPath,
            process,
            {
              config: instance?.instanceConfiguration,
              nodeDtos: nodes.nodeConfigDtos,
            },
            system?.config
          )
        )}`;
        this.url = sanitizer.bypassSecurityTrustResourceUrl(this.rawUrl);
      });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ openUiEndpoint() {
    this.openUrl(this.rawUrl);
  }

  /* template */ openUiEndpointDirect() {
    this.openUrl(this.directUri);
  }

  /* template */ reloadIFrame() {
    this.frameLoaded = false;
    this.iframe?.nativeElement?.contentWindow?.location?.reload();
  }

  /* template */ setIFrameFullscreen() {
    this.iframe?.nativeElement?.contentWindow?.document?.documentElement?.requestFullscreen();
  }

  private cpWithSlash(cp: string) {
    if (!cp) {
      return '/';
    }
    return cp[0] === '/' ? cp : `/${cp}`;
  }

  private openUrl(url: string) {
    window.open(url, '_blank', 'noreferrer,noopener');
  }
}

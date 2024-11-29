import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { Observable, Subscription, catchError, combineLatest, first, map, of, skipWhile, switchMap } from 'rxjs';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { ClientApp, ClientsService } from 'src/app/modules/primary/groups/services/clients.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

@Component({
    selector: 'app-process-ui-inline',
    templateUrl: './process-ui-inline.component.html',
    styleUrls: ['./process-ui-inline.component.css'],
    standalone: false
})
export class ProcessUiInlineComponent implements OnInit, OnDestroy {
  private readonly clients = inject(ClientsService);
  private readonly cfg = inject(ConfigService);
  private readonly nav = inject(NavAreasService);
  private readonly groups = inject(GroupsService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly instances = inject(InstancesService);
  private readonly systems = inject(SystemsService);

  protected app: ClientApp;
  protected url: SafeUrl;
  protected directUri: string;
  protected frameLoaded = false;
  protected returnPanel: unknown[] = null;

  private rawUrl: string;
  private subscription: Subscription;

  @ViewChild('iframe', { static: false }) private readonly iframe: ElementRef;

  ngOnInit(): void {
    this.subscription = combineLatest([this.nav.panelRoute$, this.groups.current$, this.clients.apps$])
      .pipe(
        skipWhile(([r, g, a]) => !r?.params?.endpoint || !r?.params?.app || !g || !a?.length),
        first(), // only calculate this *ONCE* when all data is there.
      )
      .subscribe(([route, group, apps]) => {
        if (route.params.returnPanel) {
          let panel: string = route.params.returnPanel;
          if (panel.startsWith('/')) {
            panel = panel.substring(1);
          }
          this.returnPanel = panel.split('/');
        }

        this.app = apps.find(
          (a) => a.endpoint?.id === route.params.app && a.endpoint.endpoint.id === route.params.endpoint,
        );

        if (!this.app) {
          return;
        }

        this.clients
          .getDirectUiURI(this.app)
          .pipe(catchError(() => of(null)))
          .subscribe((url) => {
            this.directUri = url;
          });

        this.contextPath$(this.app).subscribe((cp) => {
          this.rawUrl = `${this.cfg.config.api}/master/upx/${group.name}/${this.app.instanceId}/${
            this.app.endpoint.id
          }/${this.app.endpoint.endpoint.id}${this.cpWithSlash(cp)}`;

          this.url = this.sanitizer.bypassSecurityTrustResourceUrl(this.rawUrl);
        });
      });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private contextPath$(app: ClientApp): Observable<string> {
    const cp = app.endpoint.endpoint.contextPath;
    if (!cp.linkExpression) {
      return of(cp.value);
    }
    const instance$ = this.instances.instances$.pipe(
      map((instances) => instances?.find((i) => i.instanceConfiguration.id === app.instanceId)),
      skipWhile((instance) => !instance?.activeVersion),
    );
    const activeNodeCfgs$ = instance$.pipe(
      switchMap((instance) => this.instances.loadNodes(instance.instanceConfiguration.id, instance.activeVersion.tag)),
    );
    return combineLatest([instance$, this.systems.systems$, activeNodeCfgs$]).pipe(
      skipWhile(([i, s, n]) => !i || (i?.instanceConfiguration?.system && !s?.length) || !n?.nodeConfigDtos?.length),
      first(), // only calculate this *ONCE* when all data is there.
      map(([instance, systems, nodes]) => {
        // system might be incorrect since instance is taken from current version instead of active one.
        // if this causes a bug, we will need a public method to fetch active version from instances.service
        const system = systems?.find((s) => s.key.name === instance?.instanceConfiguration?.system?.name);
        const process = nodes?.nodeConfigDtos
          ?.map((n) => n.nodeConfiguration?.applications?.find((a) => a.id === app.client?.id))
          .find((a) => a);
        return getRenderPreview(
          app.endpoint.endpoint.contextPath,
          process,
          {
            config: instance?.instanceConfiguration,
            nodeDtos: nodes.nodeConfigDtos,
          },
          system?.config,
        );
      }),
    );
  }

  private cpWithSlash(cp: string) {
    if (!cp) {
      return '/';
    }
    return cp.startsWith('/') ? cp : `/${cp}`;
  }

  protected openUiEndpoint() {
    this.openUrl(this.rawUrl);
  }

  protected openUiEndpointDirect() {
    this.openUrl(this.directUri);
  }

  protected reloadIFrame() {
    this.frameLoaded = false;
    this.iframe?.nativeElement?.contentWindow?.location?.reload();
  }

  protected setIFrameFullscreen() {
    this.iframe?.nativeElement?.contentWindow?.document?.documentElement?.requestFullscreen();
  }

  private openUrl(url: string) {
    window.open(url, '_blank', 'noreferrer');
  }
}

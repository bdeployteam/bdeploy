import { Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  first,
  map,
  Observable,
  of,
  skipWhile,
  Subscription,
  switchMap,
} from 'rxjs';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
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
  selector: 'app-endpoint-detail',
  templateUrl: './endpoint-detail.component.html',
})
export class EndpointDetailComponent implements OnDestroy {
  /* template */ app$ = new BehaviorSubject<ClientApp>(null);
  /* template */ header: string;
  /* template */ directUri: string;
  /* template */ rawUrl: string;

  private subscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private instances: InstancesService,
    private systems: SystemsService,
    private cfg: ConfigService,
    areas: NavAreasService,
    private clients: ClientsService,
    groups: GroupsService
  ) {
    this.subscription = combineLatest([
      areas.panelRoute$,
      clients.apps$,
    ]).subscribe(([route, apps]) => {
      this.header = '';
      this.app$.next(null);

      if (!route || !apps || !route.paramMap.has('app')) {
        return;
      }

      const appId = route.paramMap.get('app');
      const app = apps.find((a) => a?.endpoint?.id === appId);

      if (!app) {
        return;
      }

      this.header = `${app.endpoint.appName} - ${app.endpoint.endpoint.id}`;
      this.app$.next(app);
    });

    this.subscription.add(
      this.app$
        .pipe(switchMap((app) => this.getDirectUiUri(app)))
        .subscribe((url) => (this.directUri = url))
    );

    this.subscription.add(
      combineLatest([this.app$, groups.current$])
        .pipe(switchMap(([app, group]) => this.getRawUrl(app, group)))
        .subscribe((url) => (this.rawUrl = url))
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private getDirectUiUri(app: ClientApp): Observable<string> {
    if (!app) {
      return of(null);
    }
    return this.clients.getDirectUiURI(app).pipe(catchError(() => of(null)));
  }

  private getRawUrl(
    app: ClientApp,
    group: InstanceGroupConfiguration
  ): Observable<string> {
    if (!app || !group) {
      return of(null);
    }
    return this.contextPath$(app).pipe(
      map(
        (cp) =>
          `${this.cfg.config.api}/master/upx/${group.name}/${app.instance.id}/${
            app.endpoint.id
          }/${app.endpoint.endpoint.id}${this.cpWithSlash(cp)}`
      ),
      catchError(() => of(null))
    );
  }

  private contextPath$(app: ClientApp): Observable<string> {
    const cp = app.endpoint.endpoint.contextPath;
    if (!cp.linkExpression) {
      return of(cp.value);
    }
    const instance$ = this.instances.instances$.pipe(
      map((instances) =>
        instances?.find((i) => i.instanceConfiguration.id === app.instance.id)
      ),
      skipWhile((instance) => !instance || !instance.activeVersion)
    );
    const activeNodeCfgs$ = instance$.pipe(
      switchMap((instance) =>
        this.instances.loadNodes(
          instance.instanceConfiguration.id,
          instance.activeVersion.tag
        )
      )
    );
    return combineLatest([
      instance$,
      this.systems.systems$,
      activeNodeCfgs$,
    ]).pipe(
      skipWhile(
        ([i, s, n]) =>
          !i ||
          (i?.instanceConfiguration?.system && !s?.length) ||
          !n?.nodeConfigDtos?.length
      ),
      first(), // only calculate this *ONCE* when all data is there.
      map(([instance, systems, nodes]) => {
        // system might be incorrect since instance is taken from current version instead of active one.
        // if this causes a bug, we will need a public method to fetch active version from instances.service
        const system = systems?.find(
          (s) => s.key.name === instance?.instanceConfiguration?.system?.name
        );
        const process = nodes?.nodeConfigDtos
          ?.map((n) =>
            n.nodeConfiguration?.applications?.find(
              (a) => a.id === app.client?.id
            )
          )
          .find((a) => a);
        return getRenderPreview(
          app.endpoint.endpoint.contextPath,
          process,
          {
            config: instance?.instanceConfiguration,
            nodeDtos: nodes.nodeConfigDtos,
          },
          system?.config
        );
      })
    );
  }

  /* template */ getRouterLink() {
    const app = this.app$.value;
    const returnUrl = this.route.snapshot.pathFromRoot
      .map((s) => s.url.map((u) => u.toString()).join('/'))
      .join('/');
    return [
      '',
      {
        outlets: {
          panel: [
            'panels',
            'groups',
            'endpoint',
            app.endpoint.id,
            app.endpoint.endpoint.id,
            {
              returnPanel: returnUrl,
            },
          ],
        },
      },
    ];
  }

  /* template */ openUiEndpoint() {
    this.openUrl(this.rawUrl);
  }

  /* template */ openUiEndpointDirect() {
    this.openUrl(this.directUri);
  }

  private openUrl(url: string) {
    window.open(url, '_blank', 'noreferrer,noopener');
  }

  private cpWithSlash(cp: string) {
    if (!cp) {
      return '/';
    }
    return cp[0] === '/' ? cp : `/${cp}`;
  }
}

import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { OperatingSystem } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ClientApp, ClientsService } from 'src/app/modules/primary/groups/services/clients.service';

@Component({
    selector: 'app-client-detail',
    templateUrl: './client-detail.component.html',
    styleUrls: ['./client-detail.component.css'],
    standalone: false
})
export class ClientDetailComponent implements OnInit, OnDestroy {
  protected readonly areas = inject(NavAreasService);
  protected readonly clients = inject(ClientsService);
  protected readonly auth = inject(AuthenticationService);

  protected readonly CLIENT_NODE = CLIENT_NODE_NAME;

  protected app$ = new BehaviorSubject<ClientApp>(null);

  protected downloadingInstaller$ = new BehaviorSubject<boolean>(false);
  protected downloadingClickAndStart$ = new BehaviorSubject<boolean>(false);

  protected downloadingLauncher$ = new BehaviorSubject<boolean>(false);
  protected hasLauncher: boolean;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([this.clients.apps$, this.areas.panelRoute$, this.clients.launcher$]).subscribe(
      ([apps, route, launcher]) => {
        if (!route || !apps || !route.paramMap.has('app')) {
          this.app$.next(null);
          return;
        }

        const appId = route.paramMap.get('app');
        const app = apps.find((a) => a.client.id === appId);
        this.app$.next(app);
        if (app && launcher) {
          this.hasLauncher = this.clients.hasLauncher(app.client.os);
        }
      },
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected getOsName(os: OperatingSystem) {
    return !os ? '' : this.capitalize(os);
  }

  protected downloadInstaller(app: ClientApp) {
    this.downloadingInstaller$.next(true);
    this.clients
      .downloadInstaller(app.client.id, app.instanceId)
      .pipe(finalize(() => this.downloadingInstaller$.next(false)))
      .subscribe();
  }

  protected downloadClickAndStart(app: ClientApp) {
    this.downloadingClickAndStart$.next(true);
    this.clients
      .downloadClickAndStart(app.client.id, app.client.description, app.instanceId)
      .pipe(finalize(() => this.downloadingClickAndStart$.next(false)))
      .subscribe();
  }

  protected downloadLauncher(os: OperatingSystem) {
    this.downloadingLauncher$.next(true);
    this.clients
      .downloadLauncherInstaller(os)
      .pipe(finalize(() => this.downloadingLauncher$.next(false)))
      .subscribe();
  }

  private capitalize(val: string) {
    return val.charAt(0).toUpperCase() + val.slice(1).toLowerCase();
  }
}

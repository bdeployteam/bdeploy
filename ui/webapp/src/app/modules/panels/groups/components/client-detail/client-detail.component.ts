import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { OperatingSystem } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  ClientApp,
  ClientsService,
} from 'src/app/modules/primary/groups/services/clients.service';

@Component({
  selector: 'app-client-detail',
  templateUrl: './client-detail.component.html',
  styleUrls: ['./client-detail.component.css'],
})
export class ClientDetailComponent implements OnInit, OnDestroy {
  /* template */ app$ = new BehaviorSubject<ClientApp>(null);

  /* template */ downloadingInstaller$ = new BehaviorSubject<boolean>(false);
  /* template */ downloadingClickAndStart$ = new BehaviorSubject<boolean>(
    false
  );

  /* template */ downloadingLauncher$ = new BehaviorSubject<boolean>(false);
  /* template */ downloadingLauncherZip$ = new BehaviorSubject<boolean>(false);
  /* template */ hasLauncher: boolean;

  private subscription: Subscription;

  constructor(private areas: NavAreasService, public clients: ClientsService) {}

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.clients.apps$,
      this.areas.panelRoute$,
      this.clients.launcher$,
    ]).subscribe(([apps, route, launcher]) => {
      if (!route || !apps || !route.paramMap.has('app')) {
        this.app$.next(null);
        return;
      }

      const appUid = route.paramMap.get('app');
      const app = apps.find((a) => a.client.uuid === appUid);
      this.app$.next(app);
      if (app && launcher) {
        this.hasLauncher = this.clients.hasLauncher(app.client.os);
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ getOsName(os: OperatingSystem) {
    return !os ? '' : this.capitalize(os);
  }

  /* template */ downloadInstaller(app: ClientApp) {
    this.downloadingInstaller$.next(true);
    this.clients
      .downloadInstaller(app.client.uuid, app.instance.uuid)
      .pipe(finalize(() => this.downloadingInstaller$.next(false)))
      .subscribe();
  }

  /* template */ downloadClickAndStart(app: ClientApp) {
    this.downloadingClickAndStart$.next(true);
    this.clients
      .downloadClickAndStart(
        app.client.uuid,
        app.client.description,
        app.instance.uuid
      )
      .pipe(finalize(() => this.downloadingClickAndStart$.next(false)))
      .subscribe();
  }

  /* template */ downloadLauncher(os: OperatingSystem) {
    this.downloadingLauncher$.next(true);
    this.clients
      .downloadLauncherInstaller(os)
      .pipe(finalize(() => this.downloadingLauncher$.next(false)))
      .subscribe();
  }

  /* template */ downloadLauncherZip(os: OperatingSystem) {
    this.downloadingLauncherZip$.next(true);
    this.clients
      .downloadLauncherZip(os)
      .pipe(finalize(() => this.downloadingLauncherZip$.next(false)))
      .subscribe();
  }

  private capitalize(val: string) {
    return val.charAt(0).toUpperCase() + val.slice(1).toLowerCase();
  }
}

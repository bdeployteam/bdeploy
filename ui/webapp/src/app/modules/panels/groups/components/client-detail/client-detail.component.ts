import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { NodeType, OperatingSystem } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ClientApp, ClientsService } from 'src/app/modules/primary/groups/services/clients.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { MatIcon } from '@angular/material/icon';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdIdentifierComponent } from '../../../../core/components/bd-identifier/bd-identifier.component';
import { BdExpandButtonComponent } from '../../../../core/components/bd-expand-button/bd-expand-button.component';
import { ClientUsageGraphComponent } from './usage-graph/usage-graph.component';
import { MatTooltip } from '@angular/material/tooltip';
import { AsyncPipe } from '@angular/common';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Component({
    selector: 'app-client-detail',
    templateUrl: './client-detail.component.html',
    styleUrls: ['./client-detail.component.css'],
    imports: [BdDialogComponent, BdDialogToolbarComponent, MatIcon, MatDivider, BdButtonComponent, BdDialogContentComponent, BdIdentifierComponent, BdExpandButtonComponent, ClientUsageGraphComponent, MatTooltip, AsyncPipe]
})
export class ClientDetailComponent implements OnInit, OnDestroy {
  protected readonly areas = inject(NavAreasService);
  protected readonly clients = inject(ClientsService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly instances = inject(InstancesService);

  protected nodeName: string;

  protected app$ = new BehaviorSubject<ClientApp>(null);

  protected downloadingInstaller$ = new BehaviorSubject<boolean>(false);
  protected downloadingClickAndStart$ = new BehaviorSubject<boolean>(false);

  protected downloadingLauncher$ = new BehaviorSubject<boolean>(false);
  protected hasLauncher: boolean;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.clients.apps$,
      this.areas.panelRoute$,
      this.clients.launcher$,
      this.instances.activeNodeCfgs$
    ]).subscribe(([apps, route, launcher, nodes]) => {
        if (!route || !apps || !route.paramMap.has('app')) {
          this.app$.next(null);
          return;
        }

        this.nodeName = nodes?.nodeConfigDtos?.find((n) => n.nodeConfiguration.nodeType === NodeType.CLIENT)?.nodeName

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

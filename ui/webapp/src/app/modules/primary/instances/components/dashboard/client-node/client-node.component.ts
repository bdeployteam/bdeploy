import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ApplicationConfiguration, InstanceNodeConfigurationDto } from 'src/app/models/gen.dtos';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { ClientsService } from 'src/app/modules/primary/groups/services/clients.service';
import { ClientsUsageService } from '../../../services/clients-usage.service';
import { InstancesService } from '../../../services/instances.service';

@Component({
  selector: 'app-instance-client-node',
  templateUrl: './client-node.component.html',
  styleUrls: ['./client-node.component.css'],
})
export class ClientNodeComponent implements OnInit, OnDestroy {
  @Input() node: InstanceNodeConfigurationDto;

  /* template */ selected: ApplicationConfiguration;
  /* template */ showGraph$ = new BehaviorSubject<boolean>(true);

  /* template */ downloadingInstaller$ = new BehaviorSubject<boolean>(false);
  /* template */ downloadingClickAndStart$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  constructor(private breakpoint: BreakpointObserver, public instances: InstancesService, public usage: ClientsUsageService, public clients: ClientsService) {
    this.subscription = this.breakpoint.observe('(min-width: 710px)').subscribe((bs) => {
      this.showGraph$.next(bs.matches);
    });
  }

  ngOnInit(): void {
    if (!!this.node?.nodeConfiguration?.applications?.length) {
      this.selected = this.node.nodeConfiguration.applications[0];
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private capitalize(val: string) {
    return val.charAt(0).toUpperCase() + val.slice(1).toLowerCase();
  }

  /* template */ getLabels(apps: ApplicationConfiguration[]) {
    return apps.map((a) => `${a.name} - ${this.capitalize(getAppOs(a.application))}`);
  }

  /* template */ getTotalLaunches(uid: string): number {
    const usage = this.usage.clientUsage$.value;
    if (!usage) {
      return 0;
    }

    const forApp = usage.find((u) => u.appUid === uid);
    if (!forApp) {
      return 0;
    }
    return forApp.usage.map((u) => u.usage).reduce((p, c) => c.map((x) => x.usage).reduce((y, z) => z + y) + p, 0);
  }

  /* template */ hasLauncher() {
    return this.clients.hasLauncher(getAppOs(this.selected.application));
  }

  /* template */ downloadInstaller(app: ApplicationConfiguration) {
    this.downloadingInstaller$.next(true);
    this.clients
      .downloadInstaller(app.uid, this.instances.active$.value.instanceConfiguration.uuid)
      .pipe(finalize(() => this.downloadingInstaller$.next(false)))
      .subscribe();
  }

  /* template */ downloadClickAndStart(app: ApplicationConfiguration) {
    this.downloadingClickAndStart$.next(true);
    this.clients
      .downloadClickAndStart(app.uid, app.name, this.instances.active$.value.instanceConfiguration.uuid)
      .pipe(finalize(() => this.downloadingClickAndStart$.next(false)))
      .subscribe();
  }
}

import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ServersService } from '../../../servers/services/servers.service';
import { InstancesService } from '../../services/instances.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit, OnDestroy {
  /* template */ synchronizing$ = new BehaviorSubject<boolean>(false);
  /* template */ narrow$ = new BehaviorSubject<boolean>(true);

  private subscription: Subscription;

  constructor(
    private media: BreakpointObserver,
    public instances: InstancesService,
    public areas: NavAreasService,
    public cfg: ConfigService,
    public servers: ServersService
  ) {}

  ngOnInit(): void {
    this.subscription = this.media.observe('(max-width:900px)').subscribe((bs) => this.narrow$.next(bs.matches));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ doSynchronize(server: ManagedMasterDto) {
    this.synchronizing$.next(true);
    this.servers
      .synchronize(server)
      .pipe(finalize(() => this.synchronizing$.next(false)))
      .subscribe();
  }
}

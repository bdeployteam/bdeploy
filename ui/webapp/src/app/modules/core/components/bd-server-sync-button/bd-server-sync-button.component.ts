import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Input,
  NgZone,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { ServerDetailsService } from 'src/app/modules/panels/servers/services/server-details.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { AuthenticationService } from '../../services/authentication.service';

@Component({
  selector: 'app-bd-server-sync-button',
  templateUrl: './bd-server-sync-button.component.html',
  styleUrls: ['./bd-server-sync-button.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdServerSyncButtonComponent implements OnInit, OnDestroy {
  @Input() server: ManagedMasterDto;
  @Input() collapsed = true;

  /* template */ synchronizing$ = new BehaviorSubject<boolean>(false);
  /* template */ sync$ = new BehaviorSubject<boolean>(false);
  /* template */ tooltip$ = new BehaviorSubject<string>(null);
  /* template */ badge$ = new BehaviorSubject<number>(null);
  /* template */ noPerm$ = new BehaviorSubject<boolean>(false);

  private interval;
  private sub: Subscription;

  constructor(
    private serverDetailsService: ServerDetailsService,
    private servers: ServersService,
    private auth: AuthenticationService,
    private instancesService: InstancesService,
    private ngZone: NgZone,
    private cd: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.sub = this.servers.servers$.subscribe(() => {
      this.update();
    });

    this.ngZone.runOutsideAngular(() => {
      // updating states ever second to reflect changes to the badge (second-wise countdown)
      this.interval = setInterval(() => this.update(), 1000);
    });

    this.update();
  }

  ngOnDestroy(): void {
    clearInterval(this.interval);
    this.sub.unsubscribe();
  }

  updateSyncState() {
    const instance =
      this.instancesService.current$?.value?.managedServer ||
      this.instancesService.active$?.value?.managedServer;
    if (instance) {
      this.servers.updateInstanceSyncState(instance);
    }
    if (this.serverDetailsService.server$?.value) {
      this.servers.updateServerSyncState(
        this.serverDetailsService.server$?.value
      );
    }
  }

  /* template */ doSynchronize(server: ManagedMasterDto) {
    this.synchronizing$.next(true);
    this.servers
      .synchronize(server)
      .pipe(
        finalize(() => {
          this.synchronizing$.next(false);
        })
      )
      .subscribe((result) => {
        this.instancesService.updateStatusDtos(result.states);
      });
  }

  private update() {
    if (!this.server) {
      return;
    }
    const oldBadge = this.badge$.value;

    const isSynchronized = this.servers.isSynchronized(this.server);
    this.noPerm$.next(false);
    if (!isSynchronized && this.server?.update?.forceUpdate) {
      this.sync$.next(false);
      this.tooltip$.next(
        'The server requires a mandatory update before synchronization is possible.'
      );
      this.badge$.next(null);
    } else if (!isSynchronized) {
      this.sync$.next(false);
      this.tooltip$.next(
        'The server is not synchronized. Click to synchronize now'
      );
      this.badge$.next(null);
    } else {
      this.sync$.next(true);

      const remainingSeconds = Math.round(
        this.servers.getRemainingSynchronizedTime(this.server) / 1000
      );

      if (remainingSeconds > 60) {
        const remainingMinutes = Math.round(remainingSeconds / 60);
        this.tooltip$.next(
          `The server is in synchronized state for ${remainingMinutes} minutes`
        );
        this.badge$.next(remainingMinutes);
      } else {
        this.tooltip$.next(
          `The server is in synchronized state for ${remainingSeconds} seconds`
        );
        this.badge$.next(remainingSeconds);
      }
    }

    if (!this.auth.isCurrentScopeRead()) {
      this.tooltip$.next('Insufficient permissions to synchronize.');
      this.noPerm$.next(true);
    }
    this.updateSyncState();

    if (oldBadge !== this.badge$?.value) {
      this.cd.detectChanges(); // marking is not enough, really force it now.
    }
  }
}

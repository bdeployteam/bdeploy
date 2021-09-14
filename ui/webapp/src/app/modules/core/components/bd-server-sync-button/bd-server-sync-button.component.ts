import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { AuthenticationService } from '../../services/authentication.service';

@Component({
  selector: 'app-bd-server-sync-button',
  templateUrl: './bd-server-sync-button.component.html',
  styleUrls: ['./bd-server-sync-button.component.css'],
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

  constructor(private servers: ServersService, private auth: AuthenticationService) {}

  ngOnInit(): void {
    this.sub = this.servers.servers$.subscribe((_) => {
      this.update();
    });

    // updating states ever second to reflect changes to the badge (second-wise countdown)
    this.interval = setInterval(() => this.update(), 1000);
    this.update();
  }

  ngOnDestroy(): void {
    clearInterval(this.interval);
    this.sub.unsubscribe();
  }

  /* template */ doSynchronize(server: ManagedMasterDto) {
    this.synchronizing$.next(true);
    this.servers
      .synchronize(server)
      .pipe(finalize(() => this.synchronizing$.next(false)))
      .subscribe();
  }

  private update() {
    if (!this.server) {
      return;
    }

    this.noPerm$.next(false);
    if (!this.servers.isSynchronized(this.server) && this.server?.update?.forceUpdate) {
      this.sync$.next(false);
      this.tooltip$.next('The server requires a mandatory update before synchronization is possible.');
      this.badge$.next(null);
    } else if (!this.servers.isSynchronized(this.server)) {
      this.sync$.next(false);
      this.tooltip$.next('The server is not synchronized. Click to synchronize now');
      this.badge$.next(null);
    } else {
      this.sync$.next(true);

      const remainingSeconds = Math.round(this.servers.getRemainingSynchronizedTime(this.server) / 1000);

      if (remainingSeconds > 60) {
        const remainingMinutes = Math.round(remainingSeconds / 60);
        this.tooltip$.next(`The server is in synchronized state for ${remainingMinutes} minutes`);
        this.badge$.next(remainingMinutes);
      } else {
        this.tooltip$.next(`The server is in synchronized state for ${remainingSeconds} seconds`);
        this.badge$.next(remainingSeconds);
      }
    }

    if (!this.auth.isCurrentScopeWrite()) {
      this.tooltip$.next('Insufficient permissions to synchronize.');
      this.noPerm$.next(true);
    }
  }
}

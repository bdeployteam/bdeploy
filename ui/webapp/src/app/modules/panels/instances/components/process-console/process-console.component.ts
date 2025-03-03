import { Component, NgZone, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest, finalize } from 'rxjs';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';
import { ProcessDetailsService } from '../../services/process-details.service';
import { ServersService } from './../../../../primary/servers/services/servers.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { NgClass, AsyncPipe } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdTerminalComponent } from '../../../../core/components/bd-terminal/bd-terminal.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { BdServerSyncButtonComponent } from '../../../../core/components/bd-server-sync-button/bd-server-sync-button.component';

const MAX_TAIL = 512 * 1024; // 512KiB max initial fetch.

@Component({
    selector: 'app-process-console',
    templateUrl: './process-console.component.html',
    styleUrls: ['./process-console.component.css'],
    imports: [BdDialogComponent, BdDialogToolbarComponent, NgClass, MatTooltip, MatDivider, BdButtonComponent, BdDialogContentComponent, BdTerminalComponent, BdNoDataComponent, BdServerSyncButtonComponent, AsyncPipe]
})
export class ProcessConsoleComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthenticationService);
  private readonly ngZone = inject(NgZone);
  protected readonly instances = inject(InstancesService);
  protected readonly details = inject(ProcessDetailsService);
  protected readonly servers = inject(ServersService);

  protected content$ = new BehaviorSubject<string>('');
  protected available$ = new BehaviorSubject<boolean>(false);
  protected hasStdin$ = new BehaviorSubject<boolean>(false);
  protected stdin$ = new BehaviorSubject<boolean>(false);
  protected follow$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;
  private followInterval: ReturnType<typeof setInterval>;

  private loadingChunk = false;
  private offset = 0;

  ngOnInit(): void {
    this.subscription = this.follow$.subscribe((b) => {
      clearInterval(this.followInterval);
      if (b) {
        this.ngZone.runOutsideAngular(() => {
          this.followInterval = setInterval(() => this.nextChunk(), 2000);
        });
      }
    });

    this.subscription.add(
      combineLatest([this.details.processConfig$, this.details.processDetail$]).subscribe(([cfg, detail]) => {
        if (!detail) {
          this.available$.next(false);
          this.follow$.next(false);
        }

        if (!cfg || !detail) {
          return;
        }

        this.hasStdin$.next(cfg.processControl.attachStdin);
        this.stdin$.next(
          // if the process supports stdin, has stdin, is running, and the user has write permission.
          cfg.processControl.attachStdin &&
            detail.hasStdin &&
            ProcessesService.isRunning(detail.status.processState) &&
            this.auth.isCurrentScopeWrite(),
        );
        this.follow$.next(ProcessesService.isRunning(detail.status.processState));
        this.available$.next(true);

        this.nextChunk(); // initial
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    clearInterval(this.followInterval);
  }

  private nextChunk() {
    if (this.loadingChunk) {
      return;
    }

    this.loadingChunk = true;
    this.details.getOutputEntry().subscribe(([dir, entry]) => {
      if (!entry) {
        this.loadingChunk = false;
        return;
      }

      if (!this.offset && entry.size > MAX_TAIL) {
        this.offset = entry.size - MAX_TAIL;
      }
      this.instances
        .getContentChunk(dir, entry, this.offset, 0)
        .pipe(finalize(() => (this.loadingChunk = false)))
        .subscribe((chunk) => {
          if (!chunk) {
            return;
          }

          this.content$.next(chunk.content);
          this.offset = chunk.endPointer;
        });
    });
  }

  protected onUserInput(input: string) {
    this.details.writeStdin(input);
  }

  protected doDownload() {
    this.details.getOutputEntry().subscribe(([dir, entry]) => {
      this.instances.download(dir, entry);
    });
  }
}

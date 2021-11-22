import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subject, Subscription } from 'rxjs';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';
import { ProcessDetailsService } from '../../services/process-details.service';

const MAX_TAIL = 512 * 1024; // 512KB max initial fetch.

@Component({
  selector: 'app-process-console',
  templateUrl: './process-console.component.html',
  styleUrls: ['./process-console.component.css'],
})
export class ProcessConsoleComponent implements OnInit, OnDestroy {
  /* template */ content$ = new Subject<string>();
  /* template */ available$ = new BehaviorSubject<boolean>(false);
  /* template */ hasStdin$ = new BehaviorSubject<boolean>(false);
  /* template */ stdin$ = new BehaviorSubject<boolean>(false);
  /* template */ follow$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;
  private followInterval;

  private offset = 0;

  constructor(private auth: AuthenticationService, private instances: InstancesService, public details: ProcessDetailsService) {}

  ngOnInit(): void {
    this.subscription = this.follow$.subscribe((b) => {
      clearInterval(this.followInterval);
      if (b) {
        this.followInterval = setInterval(() => this.nextChunk(), 2000);
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
          cfg.processControl.attachStdin && detail.hasStdin && ProcessesService.isRunning(detail.status.processState) && this.auth.isCurrentScopeWrite()
        );
        this.follow$.next(ProcessesService.isRunning(detail.status.processState));
        this.available$.next(true);

        this.nextChunk(); // initial
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    clearInterval(this.followInterval);
  }

  private nextChunk() {
    this.details.getOutputEntry().subscribe(([dir, entry]) => {
      if (!entry) {
        return;
      }

      if (!this.offset && entry.size > MAX_TAIL) {
        this.offset = entry.size - MAX_TAIL;
      }
      this.instances.getContentChunk(dir, entry, this.offset, 0).subscribe((chunk) => {
        if (!chunk) {
          return;
        }

        this.content$.next(chunk.content);
        this.offset = chunk.endPointer;
      });
    });
  }

  /* template */ onUserInput(input: string) {
    this.details.writeStdin(input);
  }

  /* template */ doDownload() {
    this.details.getOutputEntry().subscribe(([dir, entry]) => {
      this.instances.download(dir, entry);
    });
  }
}

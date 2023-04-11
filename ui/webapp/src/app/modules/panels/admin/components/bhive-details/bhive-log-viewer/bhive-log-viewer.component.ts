import { Component, NgZone, OnDestroy } from '@angular/core';
import {
  BehaviorSubject,
  Subject,
  Subscription,
  combineLatest,
  delay,
  of,
  skipWhile,
  switchMap,
} from 'rxjs';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  isArchived,
  isOversized,
  unwrap,
} from 'src/app/modules/core/utils/file-viewer.utils';
import { HiveLoggingService } from '../../../services/hive-logging.service';

const MAX_TAIL = 512 * 1024; // 512KB max initial fetch.

@Component({
  selector: 'app-bhive-log-viewer',
  templateUrl: './bhive-log-viewer.component.html',
})
export class BhiveLogViewerComponent implements OnDestroy {
  /* template */ directory$ = new BehaviorSubject<RemoteDirectory>(null);
  /* template */ file$ = new BehaviorSubject<RemoteDirectoryEntry>(null);
  /* template */ content$ = new Subject<string>();
  /* template */ follow$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;
  private followInterval;
  private offset = 0;

  constructor(
    private hiveLogging: HiveLoggingService,
    areas: NavAreasService,
    ngZone: NgZone
  ) {
    this.subscription = combineLatest([
      areas.panelRoute$,
      hiveLogging.directories$,
    ]).subscribe(([r, d]) => {
      if (!r?.params || !r.params['node'] || !r.params['file'] || !d) {
        return;
      }

      const nodeName = r.params['node'];
      const fileName = r.params['file'];

      // check if we need to reset, otherwise e.g. the file size was updated, which is fine to follow along.
      if (
        nodeName !== this.directory$.value?.minion ||
        fileName !== this.file$.value?.path
      ) {
        // reset!
        this.offset = 0;
      }

      for (const dir of d) {
        if (dir.minion !== nodeName) {
          continue;
        }

        for (const f of dir.entries) {
          if (f.path === fileName) {
            this.directory$.next(dir);
            this.file$.next(f);
            this.nextChunk(); // initial
            break;
          }
        }
      }
    });

    this.subscription.add(
      this.follow$.subscribe((b) => {
        clearInterval(this.followInterval);
        if (b) {
          ngZone.runOutsideAngular(() => {
            this.followInterval = setInterval(() => hiveLogging.reload(), 2000);
          });
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    clearInterval(this.followInterval);
  }

  private nextChunk() {
    // these are current enough :) we're called when the size of a file was updated.
    const dir = this.directory$.value;
    const entry = this.file$.value;

    if (isArchived(entry) && isOversized(entry)) {
      const message = `File ${entry.path} is too large to display it here. Please download it`;
      of(message) // hack to make bd-terminal receive event emission after resubscription
        .pipe(delay(0))
        .subscribe((content) => this.content$.next(content));
      return;
    }

    if (!this.offset && entry.size > MAX_TAIL) {
      this.offset = entry.size - MAX_TAIL;
    }

    if (this.offset === entry.size) {
      // we have everything, no need to bother
      return;
    }

    this.hiveLogging
      .getLogContentChunk(dir, entry, this.offset, 0, true)
      .pipe(
        skipWhile((chunk) => !chunk),
        switchMap((chunk) => unwrap(entry, chunk))
      )
      .subscribe((chunk) => {
        this.content$.next(chunk.content);
        this.offset = chunk.endPointer;
      });
  }
}

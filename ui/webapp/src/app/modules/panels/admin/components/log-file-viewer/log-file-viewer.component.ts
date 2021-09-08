import { Component, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subject, Subscription } from 'rxjs';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { LoggingAdminService } from 'src/app/modules/primary/admin/services/logging-admin.service';

const MAX_TAIL = 512 * 1024; // 512KB max initial fetch.

@Component({
  selector: 'app-log-file-viewer',
  templateUrl: './log-file-viewer.component.html',
  styleUrls: ['./log-file-viewer.component.css'],
})
export class LogFileViewerComponent implements OnInit {
  /* template */ directory$ = new BehaviorSubject<RemoteDirectory>(null);
  /* template */ file$ = new BehaviorSubject<RemoteDirectoryEntry>(null);
  /* template */ content$ = new Subject<string>();
  /* template */ follow$ = new BehaviorSubject<boolean>(false);

  private followInterval;
  private offset = 0;
  private subscription: Subscription;

  constructor(private loggingAdmin: LoggingAdminService, areas: NavAreasService) {
    this.subscription = combineLatest([areas.panelRoute$, loggingAdmin.directories$]).subscribe(([r, d]) => {
      if (!r?.params || !r.params['node'] || !r.params['file'] || !d) {
        return;
      }

      const nodeName = r.params['node'];
      const fileName = r.params['file'];

      // check if we need to reset, otherwise e.g. the file size was updated, which is fine to follow along.
      if (nodeName !== this.directory$.value?.minion || fileName !== this.file$.value?.path) {
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
          this.followInterval = setInterval(() => loggingAdmin.reload(), 2000);
        }
      })
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    clearInterval(this.followInterval);
  }

  private nextChunk() {
    // these are current enough :) we're called when the size of a file was updated.
    const dir = this.directory$.value;
    const entry = this.file$.value;

    if (!this.offset && entry.size > MAX_TAIL) {
      this.offset = entry.size - MAX_TAIL;
    }

    if (this.offset === entry.size) {
      // we have everything, no need to bother
      return;
    }

    this.loggingAdmin.getLogContentChunk(dir, entry, this.offset, 0, true).subscribe((chunk) => {
      if (!chunk) {
        return;
      }

      this.content$.next(chunk.content);
      this.offset = chunk.endPointer;
    });
  }
}

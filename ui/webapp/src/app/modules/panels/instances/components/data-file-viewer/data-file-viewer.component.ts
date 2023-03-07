import { Component, NgZone, OnDestroy } from '@angular/core';
import { TextWriter, Uint8ArrayReader, ZipReader } from '@zip.js/zip.js';
import * as Pako from 'pako';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  delay,
  forkJoin,
  from,
  map,
  Observable,
  of,
  Subject,
  Subscription,
  switchMap,
} from 'rxjs';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { DataFilesService } from 'src/app/modules/primary/instances/services/data-files.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

const MAX_FILE_SIZE = 1048576; // 1 MB
const MAX_TAIL = 512 * 1024; // 512KB max initial fetch.

@Component({
  selector: 'app-data-file-viewer',
  templateUrl: './data-file-viewer.component.html',
})
export class DataFileViewerComponent implements OnDestroy {
  /* template */ directory$ = new BehaviorSubject<RemoteDirectory>(null);
  /* template */ file$ = new BehaviorSubject<RemoteDirectoryEntry>(null);
  /* template */ content$ = new Subject<string>();
  /* template */ follow$ = new BehaviorSubject<boolean>(false);

  /* template */ oversized = false;
  /* template */ canEdit = false;

  private followInterval;
  private offset = 0;
  private subscription: Subscription;

  constructor(
    private instances: InstancesService,
    areas: NavAreasService,
    df: DataFilesService,
    auth: AuthenticationService,
    ngZone: NgZone
  ) {
    this.subscription = combineLatest([
      areas.panelRoute$,
      df.directories$,
    ]).subscribe(([r, d]) => {
      if (!r?.params || !r.params['node'] || !r.params['file'] || !d) {
        return;
      }

      this.canEdit = auth.isCurrentScopeWrite();

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

            this.oversized = f?.size > MAX_FILE_SIZE;

            const isGZip = f.path.endsWith('.gz') || f.path.endsWith('.gzip');
            const isZip = f.path.endsWith('.zip');
            const isArchived = isZip || isGZip;
            if (isArchived && this.oversized) {
              const message = `File ${f.path} is too large to display it here. Please download it`;
              of(message) // hack to make bd-terminal receive event emission after resubscription
                .pipe(delay(0))
                .subscribe((content) => this.content$.next(content));
            } else if (isZip) {
              this.streamZip(dir, f);
            } else if (isGZip) {
              this.streamGZip(dir, f);
            } else {
              this.nextChunk(); // initial
            }
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
            this.followInterval = setInterval(() => df.load(), 2000);
          });
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    clearInterval(this.followInterval);
  }

  /* template */ doDownload() {
    this.instances.download(this.directory$.value, this.file$.value);
  }

  private streamGZip(dir: RemoteDirectory, file: RemoteDirectoryEntry) {
    this.instances
      .streamFile(dir, file)
      .pipe(map((data) => this.ungzip(data)))
      .subscribe((content) => this.content$.next(content));
  }

  private ungzip(buff: ArrayBuffer): string {
    const data = new Uint8Array(buff);
    try {
      return Pako.ungzip(data, { to: 'string' });
    } catch (e) {
      return `failed to decompress. ${e}`;
    }
  }

  private streamZip(dir: RemoteDirectory, file: RemoteDirectoryEntry) {
    this.instances
      .streamFile(dir, file)
      .pipe(switchMap((buf) => this.unzip(buf)))
      .subscribe((content) => this.content$.next(content));
  }

  private unzip(buf: ArrayBuffer): Observable<string> {
    const data = new Uint8Array(buf);
    const zipReader = new ZipReader(new Uint8ArrayReader(data));
    return from(zipReader.getEntries()).pipe(
      switchMap((es) =>
        forkJoin(es.map((e) => e.getData<string>(new TextWriter())))
      ),
      map((ss) => ss.join('\n')),
      catchError((e) => of(`failed to fetch content. ${e}`))
    );
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

    this.instances
      .getContentChunk(dir, entry, this.offset, 0)
      .subscribe((chunk) => {
        if (!chunk) {
          return;
        }

        this.content$.next(chunk.content);
        this.offset = chunk.endPointer;
      });
  }
}

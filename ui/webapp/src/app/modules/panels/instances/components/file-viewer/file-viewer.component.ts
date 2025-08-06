import { Component, NgZone, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subject, Subscription, combineLatest, delay, of, skipWhile, switchMap } from 'rxjs';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isArchived, isOversized, unwrap } from 'src/app/modules/core/utils/file-viewer.utils';
import { FilesService } from 'src/app/modules/primary/instances/services/files.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { ClickStopPropagationDirective } from '../../../../core/directives/click-stop-propagation.directive';
import { MatTooltip } from '@angular/material/tooltip';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdTerminalComponent } from '../../../../core/components/bd-terminal/bd-terminal.component';
import { AsyncPipe } from '@angular/common';

const MAX_TAIL = 512 * 1024; // 512KiB max initial fetch.

@Component({
    selector: 'app-file-viewer',
    templateUrl: './file-viewer.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdPanelButtonComponent, ClickStopPropagationDirective, MatTooltip, BdButtonComponent, MatDivider, BdDialogContentComponent, BdTerminalComponent, AsyncPipe]
})
export class FileViewerComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly filesService = inject(FilesService);
  private readonly auth = inject(AuthenticationService);
  private readonly ngZone = inject(NgZone);
  protected readonly instances = inject(InstancesService);

  private offset = 0;
  private readonly subscription: Subscription;
  private followInterval: ReturnType<typeof  setInterval>;

  protected directory$ = new BehaviorSubject<RemoteDirectory>(null);
  protected file$ = new BehaviorSubject<RemoteDirectoryEntry>(null);
  protected content$ = new Subject<string>();
  protected follow$ = new BehaviorSubject<boolean>(false);
  protected oversized = false;
  protected showEdit = false;
  protected canEdit = false;

  constructor() {
    this.subscription = this.areas.primaryRoute$.subscribe((s) => (this.showEdit = s.data['isDataFiles']));
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([this.areas.panelRoute$, this.filesService.directories$]).subscribe(([r, d]) => {
        if (!r?.params?.['node'] || !r.params['file'] || !d) {
          return;
        }

        this.canEdit = this.auth.isCurrentScopeWrite();

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
              this.oversized = isOversized(f);
              this.nextChunk(); // initial
              break;
            }
          }
        }
      }),
    );

    this.subscription.add(
      this.follow$.subscribe((b) => {
        clearInterval(this.followInterval);
        if (b) {
          this.ngZone.runOutsideAngular(() => {
            this.followInterval = setInterval(() => this.filesService.loadDataFiles(), 2000);
          });
        }
      }),
    );

    this.subscription.add(
      this.areas.primaryRoute$.subscribe((primRoute) => {
        this.showEdit = primRoute.url.some((segment) => segment.path === 'data-files');
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
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

    this.instances
      .getContentChunk(dir, entry, this.offset, 0)
      .pipe(
        skipWhile((chunk) => !chunk),
        switchMap((chunk) => unwrap(entry, chunk)),
      )
      .subscribe((chunk) => {
        if(!chunk.binary) {
          this.content$.next(chunk.content);
        } else {
          this.content$.next('<file content is detected to be binary - please download to view>');
        }
        this.offset = chunk.endPointer;
      });
  }
}

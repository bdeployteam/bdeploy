import { Component, HostListener, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import { FileStatusDto, FileStatusType, RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { FilesService } from 'src/app/modules/primary/instances/services/files.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';


import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdEditorComponent } from '../../../../core/components/bd-editor/bd-editor.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-data-file-editor',
    templateUrl: './data-file-editor.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, BdDialogContentComponent, BdEditorComponent, BdNoDataComponent, AsyncPipe]
})
export class DataFileEditorComponent implements DirtyableDialog, OnInit, OnDestroy {
  private readonly instances = inject(InstancesService);
  private readonly areas = inject(NavAreasService);
  protected readonly filesService = inject(FilesService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected directory$ = new BehaviorSubject<RemoteDirectory>(null);
  protected file$ = new BehaviorSubject<RemoteDirectoryEntry>(null);
  protected binary$ = new BehaviorSubject<boolean>(false);
  protected content = '';
  protected originalContent = '';

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([this.filesService.directories$, this.areas.panelRoute$]).subscribe(([d, r]) => {
        if (!r?.params?.['node'] || !r.params['file'] || !d) {
          return;
        }

        const nodeName = r.params['node'];
        const fileName = r.params['file'];

        for (const dir of d) {
          if (dir.minion !== nodeName) {
            continue;
          }

          for (const f of dir.entries) {
            if (f.path === fileName) {
              this.directory$.next(dir);
              this.file$.next(f);

              this.instances
                .getContentChunk(dir, f, 0, 0) // no limit, load all
                .pipe(finalize(() => this.loading$.next(false)))
                .subscribe((chunk) => {
                  this.binary$.next(chunk?.binary);
                  this.content = chunk?.content;
                  this.originalContent = chunk?.content;
                });
              break;
            }
          }
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.content !== this.originalContent;
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave() {
    return of(true).pipe(
      tap(() => {
        const f: FileStatusDto = {
          type: FileStatusType.EDIT,
          file: this.file$.value.path,
          content: Base64.encode(this.content),
        };

        this.filesService.updateFile(this.directory$.value, f).subscribe(() => this.filesService.loadDataFiles());

        this.content = '';
        this.originalContent = '';
      }),
    );
  }

  @HostListener('window:keydown.control.s', ['$event'])
  private onCtrlS(event: KeyboardEvent) {
    this.onSave();
    event.preventDefault();
  }
}

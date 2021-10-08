import { Component, OnInit, ViewChild } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import { FileStatusDto, FileStatusType, RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { DataFilesService } from 'src/app/modules/primary/instances/services/data-files.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Component({
  selector: 'app-data-file-editor',
  templateUrl: './data-file-editor.component.html',
  styleUrls: ['./data-file-editor.component.css'],
})
export class DataFileEditorComponent implements OnInit {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ directory$ = new BehaviorSubject<RemoteDirectory>(null);
  /* template */ file$ = new BehaviorSubject<RemoteDirectoryEntry>(null);
  /* template */ binary$ = new BehaviorSubject<boolean>(false);
  /* template */ content = '';
  /* template */ originalContent = '';

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(public df: DataFilesService, instances: InstancesService, areas: NavAreasService) {
    this.subscription = combineLatest([this.df.directories$, areas.panelRoute$]).subscribe(([d, r]) => {
      if (!r?.params || !r.params['node'] || !r.params['file'] || !d) {
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

            instances
              .getContentChunk(dir, f, 0, 0) // no limit, load all
              .pipe(finalize(() => this.loading$.next(false)))
              .subscribe((chunk) => {
                this.binary$.next(chunk.binary);
                this.content = chunk?.content;
                this.originalContent = chunk?.content;
              });
            break;
          }
        }
      }
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return this.content !== this.originalContent;
  }

  /* template */ onSave() {
    this.doSave().subscribe((_) => this.tb.closePanel());
  }

  public doSave() {
    return of(true).pipe(
      tap((_) => {
        const f: FileStatusDto = {
          type: FileStatusType.EDIT,
          file: this.file$.value.path,
          content: Base64.encode(this.content),
        };

        this.df.updateFile(this.directory$.value, f).subscribe();

        this.content = '';
        this.originalContent = '';
      })
    );
  }
}

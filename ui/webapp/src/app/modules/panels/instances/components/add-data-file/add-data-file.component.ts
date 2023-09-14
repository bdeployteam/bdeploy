import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, Observable, Subscription, finalize, map, of, switchMap, tap } from 'rxjs';
import { FileStatusDto, FileStatusType, RemoteDirectory } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { DataFilesService } from 'src/app/modules/primary/instances/services/data-files.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'add-data-file',
  templateUrl: './add-data-file.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddDataFileComponent implements OnInit, OnDestroy, DirtyableDialog {
  public df = inject(DataFilesService);
  private areas = inject(NavAreasService);

  protected minions$ = new BehaviorSubject<string[]>([]);

  protected tempFilePath: string;
  protected tempFileMinion: string;
  protected tempFileError: string;
  protected tempFileContentLoading$ = new BehaviorSubject<boolean>(false);
  private tempFileContent = '';
  protected saving$ = new BehaviorSubject<boolean>(false);
  private fileToSave: FileStatusDto;
  private directory: RemoteDirectory;

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      this.df.directories$.subscribe((dd) => {
        if (!dd) {
          return;
        }
        for (const dir of dd) {
          if (dir.problem) {
            console.warn(`Problem reading files from ${dir.minion}: ${dir.problem}`);
            continue;
          }
        }
        this.minions$.next(dd.map((d) => d.minion));
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.form.dirty;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.doSave().subscribe();
  }

  public doSave(): Observable<any> {
    this.saving$.next(true);
    this.fileToSave = {
      file: this.tempFilePath,
      type: FileStatusType.ADD,
      content: this.tempFileContent,
    };
    this.directory = this.df.directories$.value.find((d) => d.minion === this.tempFileMinion);

    // standard update
    let update: Observable<any> = this.df.updateFile(this.directory, this.fileToSave).pipe(
      switchMap(() => {
        return of(true);
      })
    );

    // replace update
    if (this.shouldReplace()) {
      update = this.dialog.confirm('File Exists', 'A file with the given name exists - replace?', 'warning').pipe(
        tap((r) => {
          if (r) this.fileToSave.type = FileStatusType.EDIT;
        }),
        switchMap((confirm) => {
          if (this.shouldReplace() && confirm) {
            return this.df.updateFile(this.directory, this.fileToSave).pipe(map(() => true));
          }
          return of(false);
        })
      );
    }

    return update.pipe(
      finalize(() => this.saving$.next(false)),
      tap((r) => {
        if (r) this.reset();
      })
    );
  }

  public doReplace(): Observable<any> {
    return this.df.updateFile(this.directory, this.fileToSave);
  }

  private shouldReplace() {
    return !!this.directory.entries.find((e) => e.path === this.tempFilePath);
  }

  protected doAddFileContent(file: File) {
    this.tempFileError = null;
    this.tempFileContentLoading$.next(true);

    if (file.size > 1024 * 1024 * 20) {
      this.tempFileContentLoading$.next(false);
      this.tempFileError = 'Selected File is too large, size limit 20MB';
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result.toString();

      // always set the file name/path to the original dropped file name.
      this.tempFilePath = file.name;

      // extract the base64 part of the data URL...
      this.tempFileContent = result.substr(result.indexOf(',') + 1);
      this.tempFileContentLoading$.next(false);
    };
    reader.readAsDataURL(file);
  }

  private reset() {
    this.df.load();
    this.areas.closePanel();
    this.subscription.unsubscribe();
  }
}

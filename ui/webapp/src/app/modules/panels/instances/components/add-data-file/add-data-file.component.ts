import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, Observable, Subscription, combineLatest, finalize, map, of, switchMap, tap } from 'rxjs';
import { FileStatusDto, FileStatusType, RemoteDirectory } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { FilesService } from 'src/app/modules/primary/instances/services/files.service';
import { decodeDataFilePath } from '../../utils/data-file-utils';

@Component({
  selector: 'app-add-data-file',
  templateUrl: './add-data-file.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddDataFileComponent implements OnInit, OnDestroy, DirtyableDialog {
  public filesService = inject(FilesService);
  private readonly areas = inject(NavAreasService);

  protected minions$ = new BehaviorSubject<string[]>([]);

  private readonly currentPath$ = new BehaviorSubject('');
  protected fileMinion$ = new BehaviorSubject<string>(null);
  protected tempFilePath: string;
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
      this.filesService.directories$.subscribe((dd) => {
        if (!dd) {
          return;
        }
        for (const dir of dd) {
          if (dir.problem) {
            console.warn(`Problem reading files from ${dir.minion}: ${dir.problem}`);
          }
        }
        this.minions$.next(dd.map((d) => d.minion));
      }),
    );
    this.subscription.add(
      combineLatest([this.minions$, this.areas.primaryRoute$]).subscribe(([minions, route]) => {
        if (!minions?.length || !route?.params?.['path']) {
          return;
        }
        const path = decodeDataFilePath(route.params['path']);
        this.fileMinion$.next(minions.find((minion) => path.minion === minion));
        this.currentPath$.next(path.path + (path.path ? '/' : ''));
      }),
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

  get filePath(): string {
    return this.currentPath$.value + this.tempFilePath;
  }

  public doSave(): Observable<unknown> {
    this.saving$.next(true);
    this.fileToSave = {
      file: this.filePath,
      type: FileStatusType.ADD,
      content: this.tempFileContent,
    };
    this.directory = this.filesService.directories$.value.find((d) => d.minion === this.fileMinion$.value);

    // standard update
    let update: Observable<unknown> = this.filesService
      .updateFile(this.directory, this.fileToSave)
      .pipe(switchMap(() => of(true)));

    // replace update
    if (this.shouldReplace()) {
      update = this.dialog.confirm('File Exists', 'A file with the given name exists - replace?', 'warning').pipe(
        tap((r) => {
          if (r) this.fileToSave.type = FileStatusType.EDIT;
        }),
        switchMap((confirm) => {
          if (this.shouldReplace() && confirm) {
            return this.filesService.updateFile(this.directory, this.fileToSave).pipe(map(() => true));
          }
          return of(false);
        }),
      );
    }

    return update.pipe(
      finalize(() => this.saving$.next(false)),
      tap((r) => {
        if (r) this.reset();
      }),
    );
  }

  public doReplace(): Observable<unknown> {
    return this.filesService.updateFile(this.directory, this.fileToSave);
  }

  private shouldReplace() {
    return !!this.directory.entries.find((e) => e.path.replace('\\', '/') === this.filePath);
  }

  protected doAddFileContent(file: File) {
    this.tempFileError = null;
    this.tempFileContentLoading$.next(true);

    if (file.size > 1024 * 1024 * 20) {
      this.tempFileContentLoading$.next(false);
      this.tempFileError = 'Selected file is too large, size limit 20MB';
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
    this.filesService.loadDataFiles();
    this.areas.closePanel();
    this.subscription.unsubscribe();
  }
}

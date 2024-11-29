import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, Subscription, combineLatest, forkJoin, of } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdEditorDiffComponent } from 'src/app/modules/core/components/bd-editor-diff/bd-editor-diff.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ConfigFilesService } from '../../../../services/config-files.service';

@Component({
    selector: 'app-compare',
    templateUrl: './compare.component.html',
    standalone: false
})
export class CompareComponent implements DirtyableDialog, OnInit, OnDestroy {
  private readonly edit = inject(InstanceEditService);
  private readonly areas = inject(NavAreasService);
  protected readonly cfgFiles = inject(ConfigFilesService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected file$ = new BehaviorSubject<string>(null);
  protected content = '';
  protected originalContent = '';
  protected contentTemplate = '';

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild(BdEditorDiffComponent) private readonly editor: BdEditorDiffComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([this.cfgFiles.files$, this.areas.panelRoute$, this.edit.state$]).subscribe(
      ([f, r, s]) => {
        if (!f || !r?.params?.['file'] || !s?.config?.config?.product) {
          this.file$.next(null);
          this.content = null;
          return;
        }

        const file = r.params['file'];
        this.file$.next(file);
        forkJoin([this.cfgFiles.load(file), this.cfgFiles.loadTemplate(file, s.config.config.product)])
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe(([c, t]) => {
            this.content = Base64.decode(c);
            this.originalContent = this.content;
            this.contentTemplate = Base64.decode(t);
          });
      },
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.content !== this.originalContent;
  }

  protected onApplyChanges() {
    this.content = this.contentTemplate;
    setTimeout(() => this.editor.update());
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave() {
    return of(true).pipe(
      tap(() => {
        this.cfgFiles.edit(
          this.file$.value,
          Base64.encode(this.content),
          false, // cannot be binary, we're editing it.
        );
        this.content = '';
        this.originalContent = '';
      }),
    );
  }
}

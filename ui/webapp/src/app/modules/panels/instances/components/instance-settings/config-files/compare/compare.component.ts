import { Component, OnDestroy, ViewChild } from '@angular/core';
import { Base64 } from 'js-base64';
import {
  BehaviorSubject,
  combineLatest,
  forkJoin,
  of,
  Subscription,
} from 'rxjs';
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
})
export class CompareComponent implements DirtyableDialog, OnDestroy {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ file$ = new BehaviorSubject<string>(null);
  /* template */ content = '';
  /* template */ originalContent = '';
  /* template */ contentTemplate = '';

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild(BdEditorDiffComponent) private editor: BdEditorDiffComponent;

  private subscription: Subscription;

  constructor(
    public cfgFiles: ConfigFilesService,
    private edit: InstanceEditService,
    areas: NavAreasService
  ) {
    this.subscription = combineLatest([
      this.cfgFiles.files$,
      areas.panelRoute$,
      this.edit.state$,
    ]).subscribe(([f, r, s]) => {
      if (!f || !r || !r.params['file'] || !s?.config?.config?.product) {
        this.file$.next(null);
        this.content = null;
        return;
      }

      const file = r.params['file'];
      this.file$.next(file);
      forkJoin([
        this.cfgFiles.load(file),
        this.cfgFiles.loadTemplate(file, s.config.config.product),
      ])
        .pipe(finalize(() => this.loading$.next(false)))
        .subscribe(([c, t]) => {
          this.content = Base64.decode(c);
          this.originalContent = this.content;
          this.contentTemplate = Base64.decode(t);
        });
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return this.content !== this.originalContent;
  }

  /* template */ onApplyChanges() {
    this.content = this.contentTemplate;
    setTimeout(() => this.editor.update());
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave() {
    return of(true).pipe(
      tap(() => {
        this.cfgFiles.edit(
          this.file$.value,
          Base64.encode(this.content),
          false // cannot be binary, we're editing it.
        );
        this.content = '';
        this.originalContent = '';
      })
    );
  }
}

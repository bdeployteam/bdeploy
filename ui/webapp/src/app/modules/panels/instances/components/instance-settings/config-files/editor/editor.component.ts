import { Component, HostListener, OnDestroy, ViewChild } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import { ContentCompletion } from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  buildCompletionPrefixes,
  buildCompletions,
} from 'src/app/modules/core/utils/completion.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { ConfigFilesService } from '../../../../services/config-files.service';

@Component({
  selector: 'app-editor',
  templateUrl: './editor.component.html',
})
export class EditorComponent implements DirtyableDialog, OnDestroy {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ file$ = new BehaviorSubject<string>(null);
  /* template */ content = '';
  /* template */ originalContent = '';

  /* template */ completions: ContentCompletion[];

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(
    public cfgFiles: ConfigFilesService,
    areas: NavAreasService,
    edit: InstanceEditService,
    systems: SystemsService
  ) {
    this.subscription = combineLatest([
      this.cfgFiles.files$,
      areas.panelRoute$,
      edit.state$,
      edit.stateApplications$,
      systems.systems$,
    ]).subscribe(([f, r, i, a, s]) => {
      if (
        !f ||
        !r ||
        !r.params['file'] ||
        !i ||
        !a ||
        (i.config.config.system && !s?.length)
      ) {
        this.file$.next(null);
        this.content = null;
        return;
      }

      const file = r.params['file'];
      this.file$.next(file);
      this.cfgFiles
        .load(file)
        .pipe(finalize(() => this.loading$.next(false)))
        .subscribe((c) => {
          this.content = Base64.decode(c);
          this.originalContent = this.content;
        });

      this.completions = buildCompletions(
        buildCompletionPrefixes(),
        i.config,
        s?.find((s) => s.key.name === i.config.config.system.name)?.config,
        null,
        a
      );
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return this.content !== this.originalContent;
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave() {
    return of(true).pipe(
      tap(() => {
        this.cfgFiles.edit(this.file$.value, Base64.encode(this.content));

        this.content = '';
        this.originalContent = '';
      })
    );
  }

  @HostListener('window:keydown.control.s', ['$event'])
  public onCtrlS(event: KeyboardEvent) {
    this.onSave();
    event.preventDefault();
  }
}

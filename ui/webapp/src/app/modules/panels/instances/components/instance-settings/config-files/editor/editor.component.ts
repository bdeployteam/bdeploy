import { Component, HostListener, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, Subscription, combineLatest, of } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import { ContentCompletion } from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { buildCompletionPrefixes, buildCompletions } from 'src/app/modules/core/utils/completion.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { ConfigFilesService } from '../../../../services/config-files.service';

@Component({
  selector: 'app-editor',
  templateUrl: './editor.component.html',
})
export class EditorComponent implements DirtyableDialog, OnInit, OnDestroy {
  protected cfgFiles = inject(ConfigFilesService);
  private areas = inject(NavAreasService);
  private edit = inject(InstanceEditService);
  private systems = inject(SystemsService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected file$ = new BehaviorSubject<string>(null);
  protected content = '';
  protected originalContent = '';

  protected completions: ContentCompletion[];

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([
      this.cfgFiles.files$,
      this.areas.panelRoute$,
      this.edit.state$,
      this.edit.stateApplications$,
      this.systems.systems$,
    ]).subscribe(([f, r, i, a, s]) => {
      if (!f || !r || !r.params['file'] || !i || !a || (i.config.config.system && !s?.length)) {
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

  @HostListener('window:keydown.control.s', ['$event'])
  private onCtrlS(event: KeyboardEvent) {
    this.onSave();
    event.preventDefault();
  }
}

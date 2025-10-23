import { Component, HostListener, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Base64 } from 'js-base64';
import { editor } from 'monaco-editor';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import { InstanceConfigurationDto, SystemConfiguration } from 'src/app/models/gen.dtos';
import { ContentCompletion } from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { buildCompletionPrefixes, buildCompletions } from 'src/app/modules/core/utils/completion.utils';
import { createLinkedValue, getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { ConfigFilesService } from '../../../../services/config-files.service';
import { errorMarker } from '../../../../utils/monaco-editor-utils';

import { BdButtonComponent } from '../../../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdEditorComponent } from '../../../../../../core/components/bd-editor/bd-editor.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-editor',
  templateUrl: './editor.component.html',
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdButtonComponent,
    BdDialogContentComponent,
    BdEditorComponent,
    AsyncPipe,
  ],
})
export class EditorComponent implements DirtyableDialog, OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly edit = inject(InstanceEditService);
  private readonly systems = inject(SystemsService);
  protected readonly cfgFiles = inject(ConfigFilesService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected file$ = new BehaviorSubject<string>(null);
  protected content = '';
  protected originalContent = '';

  private instance: InstanceConfigurationDto;
  private system: SystemConfiguration;

  protected completions: ContentCompletion[];
  protected recursivePrefixes = ['JSON', 'XML', 'YAML'];
  protected variableExpansionRegex = '\\{\\{.*?\\}\\}';

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  protected markUnresolvedExpansion = (match: editor.FindMatch): editor.IMarkerData => {
    const exp = match.matches[0];

    if (exp.includes('DELAYED:')) {
      return errorMarker('DELAYED variables are not allowed in config files', match);
    }

    const lv = createLinkedValue(exp);
    const preview = getRenderPreview(lv, null, this.instance, this.system); // null for ApplicationConfiguration

    return !preview.includes('{{') ? null : errorMarker('Failed to resolve', match);
  };

  ngOnInit() {
    this.subscription = combineLatest([
      this.cfgFiles.files$,
      this.areas.panelRoute$,
      this.edit.state$,
      this.edit.stateApplications$,
      this.systems.systems$,
      this.edit.current$,
    ]).subscribe(([f, r, i, a, s, c]) => {
      if (!f || !r?.params?.['file'] || !i || !a || (i.config.config.system && !s?.length) || !c) {
        this.file$.next(null);
        this.content = null;
        return;
      }

      const file = r.params['file'];
      this.file$.next(file);
      this.cfgFiles
        .load(file)
        .pipe(finalize(() => this.loading$.next(false)))
        .subscribe((str) => {
          this.content = Base64.decode(str);
          this.originalContent = this.content;
        });

      this.completions = buildCompletions(
        buildCompletionPrefixes(),
        i.config,
        s?.find((systemConfigDto) => systemConfigDto.key.name === i.config.config.system?.name)?.config,
        null,
        a
      ).filter((contentCompletion) => !contentCompletion.value.startsWith('{{DELAYED')); // DELAYED variables should not be used in config files

      this.system = s?.find((system) => system.key.name === c?.instanceConfiguration?.system?.name)?.config;

      this.instance = {
        config: c?.instanceConfiguration,
        nodeDtos: i?.config?.nodeDtos,
      };
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

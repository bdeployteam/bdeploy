import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnInit, ViewChild } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ConfigFilesService } from '../../../../services/config-files.service';

@Component({
  selector: 'app-editor',
  templateUrl: './editor.component.html',
  styleUrls: ['./editor.component.css'],
})
export class EditorComponent implements OnInit, DirtyableDialog {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);
  /* template */ file$ = new BehaviorSubject<string>(null);
  /* template */ content = '';
  /* template */ originalContent = '';

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(private bop: BreakpointObserver, public cfgFiles: ConfigFilesService, areas: NavAreasService) {
    this.subscription = bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });

    this.subscription.add(
      combineLatest([this.cfgFiles.files$, areas.panelRoute$]).subscribe(([f, r]) => {
        if (!f || !r || !r.params['file']) {
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
      })
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  isDirty(): boolean {
    return this.content !== this.originalContent;
  }

  doApply() {
    this.cfgFiles.edit(this.file$.value, Base64.encode(this.content));

    this.content = '';
    this.originalContent = '';
    this.tb.closePanel();
  }
}
